/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.core;

import org.gluu.credmanager.conf.MainSettings;
import org.gluu.credmanager.extension.AuthnMethod;
import org.gluu.credmanager.extension.OpenIdFlow;
import org.gluu.credmanager.misc.Utils;
import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.zkoss.util.Pair;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author jgomer
 */
@ApplicationScoped
@Named
public class ExtensionsManager {

    public static final String ASSETS_DIR = "assets";

    private static final Class<OpenIdFlow> OPENID_FLOW_CLASS = OpenIdFlow.class;
    private static final Class<AuthnMethod> AUTHN_METHOD_CLASS = AuthnMethod.class;

    @Inject
    private Logger logger;

    @Inject
    private ConfigurationHandler configurationHandler;

    @Inject
    private ZKService zkService;

    @Inject
    private MainSettings mainSettings;

    @Inject
    private ResourceExtractor resourceExtractor;

    @Inject
    private RSRegistryHandler registryHandler;

    private PluginManager pluginManager;

    private Deque<Pair<AuthnMethod, String>> authnMethodsExts;

    private Deque<Pair<OpenIdFlow, String>> openIdFlowExts;

    private boolean inspectExternalPath;

    @PostConstruct
    private void inited() {

        authnMethodsExts = new LinkedList<>();
        openIdFlowExts = new LinkedList<>();

        if (Utils.isNotEmpty(mainSettings.getPluginsPath())) {
            Path pluginsPath = Paths.get(mainSettings.getPluginsPath());
            if (Files.isDirectory(pluginsPath)) {
                pluginManager = new DefaultPluginManager(pluginsPath);
                inspectExternalPath = true;
            }
        }
        if (pluginManager == null) {
            logger.warn("No external plugins will be loaded: there is no valid location for searching");
            pluginManager = new DefaultPluginManager();
        }

    }

    private List<AuthnMethod> scanInnerAuthnMechanisms() {

        List<AuthnMethod> actualAMEs = new ArrayList<>();
        List<AuthnMethod> authnMethodExtensions = pluginManager.getExtensions(AUTHN_METHOD_CLASS);
        if (authnMethodExtensions != null) {

            for (AuthnMethod ext : authnMethodExtensions) {
                String acr = ext.getAcr();
                String name = ext.getName();
                logger.info("Found system extension '{}' for {}", name, acr);
                actualAMEs.add(ext);
            }
        }
        return actualAMEs;

    }

    private OpenIdFlow scanOpenIdMechanism() {

        OpenIdFlow actualOIE = null;
        List<OpenIdFlow> oidExtensions = pluginManager.getExtensions(OPENID_FLOW_CLASS);

        if (oidExtensions == null || oidExtensions.size() == 0) {
            logger.warn("No system extension found for OpenId flow!");
        } else {
            actualOIE = oidExtensions.stream().findAny().get();

            if (oidExtensions.size() > 1) {
                logger.warn("Several system extensions for OpenId flow were found, keeping only '{}'", actualOIE.getName());
            }
        }
        return actualOIE;

    }

    private void parsePluginAuthnMethodExtensions(String pluginId) {

        List<AuthnMethod> ames = pluginManager.getExtensions(AUTHN_METHOD_CLASS, pluginId);
        if (ames.size() > 0) {
            logger.info("Plugin extends {} at {} point(s)", AUTHN_METHOD_CLASS.getName(), ames.size());

            for (AuthnMethod ext : ames) {
                String acr = ext.getAcr();
                logger.info("Extension point found to deal with acr value '{}'", acr);
                authnMethodsExts.add(new Pair<>(ext, pluginId));
            }

        }

    }

    private void parsePluginOpenIdFlowExtensions(String pluginId) {

        List<OpenIdFlow> oidExtensions = pluginManager.getExtensions(OPENID_FLOW_CLASS, pluginId);
        if (oidExtensions.size() > 0) {
            logger.info("Plugin extends {} at {} point(s)", OPENID_FLOW_CLASS.getName(), oidExtensions.size());

            for (OpenIdFlow ext: oidExtensions) {
                openIdFlowExts.add(new Pair<>(ext, pluginId));
            }
        }

    }

    private Path getDestinationPathForPlugin(String pluginId) {
        return Paths.get(zkService.getAppFileSystemRoot(), RSRegistryHandler.ENDPOINTS_PREFIX, pluginId);
    }

    private void extractResources(PluginWrapper pluginWrapper) throws IOException {

        Path path = pluginWrapper.getPluginPath();
        String pluginId = pluginWrapper.getPluginId();

        Path destPath = getDestinationPathForPlugin(pluginId);
        logger.info("Extracting resources for plugin {} to {}", pluginId, destPath.toString());

        if (Files.isDirectory(path)) {
            path = Paths.get(path.toString(), ASSETS_DIR);
            if (Files.isDirectory(path)) {
                resourceExtractor.createDirectory(path, destPath);
            } else {
                logger.info("No resources to extract");
            }
        } else if (Utils.isJarFile(path)) {
            try (JarInputStream jis = new JarInputStream(new BufferedInputStream(new FileInputStream(path.toString())), false)) {
                resourceExtractor.createDirectory(jis, ASSETS_DIR + "/", destPath);
            }
        }

    }

    void scan() {

        //Load inner extensions first, then load plugins
        List<AuthnMethod> authnMethodsExtsList = scanInnerAuthnMechanisms();
        authnMethodsExtsList.forEach(ame -> authnMethodsExts.add(new Pair<>(ame, null)));

        OpenIdFlow openIdFlowExt = scanOpenIdMechanism();
        if (openIdFlowExt != null) {
            openIdFlowExts.add(new Pair<>(openIdFlowExt, null));
        }

        if (inspectExternalPath) {
            logger.info("Loading external plugins...");
            pluginManager.loadPlugins();

            logger.info("{} total plugins found", pluginManager.getPlugins().size());
            pluginManager.startPlugins();

            List<PluginWrapper> startedPlugins = pluginManager.getStartedPlugins();
            logger.info("{} plugins started", startedPlugins.size());

            for (PluginWrapper wrapper : startedPlugins) {
                Path pluginPath = wrapper.getPluginPath();
                String pluginId = wrapper.getPluginId();
                logger.info("");
                logger.info("Plugin {} ({})", pluginId, wrapper.getDescriptor().getPluginClass());

                parsePluginAuthnMethodExtensions(pluginId);
                parsePluginOpenIdFlowExtensions(pluginId);

                Set<String> classNames = pluginManager.getExtensionClassNames(pluginId);
                //classNames.remove(AUTHN_METHOD_CLASS.getName());
                //classNames.remove(OPENID_FLOW_CLASS.getName());

                if (classNames.size() > 0) {
                    logger.info("Plugin's extensions are at: {}", classNames.toString());
                }

                try {
                    extractResources(wrapper);
                } catch (IOException e) {
                    logger.error("Error when extracting plugin resources");
                    logger.error(e.getMessage(), e);
                }

                zkService.readPluginLabels(pluginId, pluginPath);
                registryHandler.scan(pluginId, pluginPath, wrapper.getPluginClassLoader());

            }
            zkService.refreshLabels();
        }

        long distinctAcrs = authnMethodsExts.stream().map(Pair::getX).map(AuthnMethod::getAcr).distinct().count();
        if (distinctAcrs < authnMethodsExts.size()) {
            logger.warn("Several extensions pretend to handle the same acr.");
            logger.warn("Only the last one parsed for the plugin referenced in the config file will be effective");
            logger.warn("The system extension (if exists) will be used if no plugin can handle an acr");
        }

        if (openIdFlowExts.size() > 1) {
            logger.warn("Only the last one extension added for OpenId Flow will be used.");
        }

    }

    public boolean unloadPlugin(String pluginId) {

        boolean success = pluginManager.unloadPlugin(pluginId);
        try {
            if (success) {
                authnMethodsExts.forEach(pair -> {
                    if (pluginId.equals(pair.getY())) {
                        authnMethodsExts.remove(pair);
                    }
                });
                openIdFlowExts.forEach(pair -> {
                    if (pluginId.equals(pair.getY())) {
                        openIdFlowExts.remove(pair);
                    }
                });
                zkService.removePluginLabels(pluginId);
                registryHandler.remove(pluginId);
                resourceExtractor.removeDestinationDirectory(getDestinationPathForPlugin(pluginId));
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return success;

    }

/*
    public PluginState addPlugin(Path path) {

        PluginState state = null;
        String pluginId = pluginManager.loadPlugin(path);

        if (pluginId != null) {
            state = pluginManager.startPlugin(pluginId);

            if (PluginState.STARTED.equals(state)) {
                Set<String> acrs = configurationHandler.retrieveAcrs();
                parsePluginAuthnMethodExtensions(pluginId, acrs);
                parsePluginOpenIdFlowExtensions(pluginId);

                //Notifies activation/deactivation for extensions handling authentication methods
                authnMethodsExts.forEach(pair -> pair.getX().deactivate());
                for (String acr : acrs) {
                    getExtensionForAuthnMethod(acr).activate();
                }

                zkService.readPluginLabels(pluginId, path);
                registryHandler.scan(pluginId, path, pluginManager.getPluginClassLoader(pluginId));
                zkService.refreshLabels();
            } else {
                logger.warn("Plugin loaded from {} not in STARTED state, but in {} state", state == null ? null : state.toString());
            }
        } else {
            logger.warn("Loading the plugin from {} returned null pluginId!", path.toString());
        }
        return state;
    }
    */

    public AuthnMethod getExtensionForAuthnMethod(String acr) {

        AuthnMethod handler = null;
        String plugId = mainSettings.getAcrPluginMap().get(acr);
        Iterator<Pair<AuthnMethod, String>> iterator = authnMethodsExts.descendingIterator();

        while (iterator.hasNext() && handler == null) {
            Pair<AuthnMethod, String> pair = iterator.next();
            handler = pair.getX();
            if (acr.equals(handler.getAcr())) {

                if (Utils.isEmpty(plugId)) {    //Find the proper system extension
                    handler =  pair.getY() == null ? handler : null;
                } else {
                    handler = pair.getY().equals(plugId) ? handler : null;
                }
            } else {
                handler = null;
            }
        }
        return handler;

    }

    public boolean pluginImplementsAuthnMethod(String acr, String plugId) {

        Stream<String> idsStream = authnMethodsExts.stream().filter(pair -> pair.getX().getAcr().equals(acr)).map(Pair::getY);
        if (Utils.isEmpty(plugId)) {
            return idsStream.anyMatch(id -> id == null);
        } else {
            return idsStream.anyMatch(id -> id.equals(plugId));
        }
    }

    public OpenIdFlow getExtensionForOpenIdFlow() {
        //Return the latest added available
        return openIdFlowExts.size() == 0 ? null : openIdFlowExts.getLast().getX();
    }

    public ClassLoader getPluginClassLoader(String clsName) {

        ClassLoader clsLoader = null;
        for (PluginWrapper wrapper : pluginManager.getStartedPlugins()) {
            try {
                String pluginClassName = wrapper.getDescriptor().getPluginClass();
                ClassLoader loader = wrapper.getPluginClassLoader();

                Class<?> cls = loader.loadClass(pluginClassName);
                if (clsName.startsWith(cls.getPackage().getName())) {
                    clsLoader = loader;
                    break;
                }
            } catch (ClassNotFoundException e) {
                //Intentionally left empty
            }
        }
        return clsLoader;

    }

    public List<AuthnMethod> getAuthnMethodExts() {
        return authnMethodsExts.stream().map(Pair::getX).collect(Collectors.toList());
    }

    public <T> List<T> getSystemExtensionsForClass(Class<T> clazz) {
        return pluginManager.getExtensions(clazz);
    }

}
