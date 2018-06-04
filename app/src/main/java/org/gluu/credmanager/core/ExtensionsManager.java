/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.core;

import org.gluu.credmanager.conf.MainSettings;
import org.gluu.credmanager.conf.PluginInfo;
import org.gluu.credmanager.extension.AuthnMethod;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.service.IExtensionsManager;
import org.pf4j.*;
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
import java.util.*;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author jgomer
 */
@ApplicationScoped
@Named
public class ExtensionsManager implements IExtensionsManager {

    public static final String ASSETS_DIR = "assets";

    private static final String PLUGINS_DIR_NAME = "plugins";
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

    private Path pluginsRoot;

    private PluginManager pluginManager;

    private Deque<Pair<AuthnMethod, String>> authnMethodsExts;

    @PostConstruct
    private void inited() {

        pluginsRoot = Paths.get(System.getProperty("server.base"), PLUGINS_DIR_NAME);
        pluginManager = new DefaultPluginManager();
        authnMethodsExts = new LinkedList<>();

        if (Files.isDirectory(pluginsRoot)) {
            purgePluginsPath();
        } else {
            pluginsRoot = null;
            logger.warn("External plugins directory does not exist: there is no valid location for searching");
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

    private Path getDestinationPathForPlugin(String pluginId) {
        return Paths.get(zkService.getAppFileSystemRoot(), RSRegistryHandler.ENDPOINTS_PREFIX, pluginId);
    }

    private void extractResources(String pluginId, Path path) throws IOException {

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

        if (pluginsRoot != null) {
            List<PluginInfo> pls = Optional.ofNullable(mainSettings.getKnownPlugins()).orElse(Collections.emptyList());

            if (pls.size() > 0) {
                logger.info("Loading external plugins...");

                List<String> loaded = new ArrayList<>();
                for (PluginInfo pl : pls) {
                    String id = loadPlugin(Paths.get(pluginsRoot.toString(), pl.getRelativePath()));
                    if (id != null) {
                        loaded.add(id);
                    }
                }

                logger.info("Total plugins loaded {}", loaded.size());
                int started = 0;

                for (String pluginId : loaded) {
                    if (pls.stream().anyMatch(pl -> pl.getId().equals(pluginId) && PluginState.STARTED.toString().equals(pl.getState()))) {

                        if (startPlugin(pluginId, false)) {
                            started++;
                            PluginWrapper wrapper = pluginManager.getPlugin(pluginId);

                            logger.info("Plugin {} ({}) started", pluginId, wrapper.getDescriptor().getPluginClass());

                            Set<String> classNames = pluginManager.getExtensionClassNames(pluginId);
                            //classNames.remove(AUTHN_METHOD_CLASS.getName());
                            if (classNames.size() > 0) {
                                logger.info("Plugin's extensions are at: {}", classNames.toString());
                            }
                            logger.info("");

                        }
                    }
                }
                logger.info("Plugins started {}", started);
            }
            zkService.refreshLabels();

        }

        long distinctAcrs = authnMethodsExts.stream().map(Pair::getX).map(AuthnMethod::getAcr).distinct().count();
        if (distinctAcrs < authnMethodsExts.size()) {
            logger.warn("Several extensions pretend to handle the same acr.");
            logger.warn("Only the last one parsed for the plugin referenced in the config file will be effective");
            logger.warn("The system extension (if exists) will be used if no plugin can handle an acr");
        }

    }

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
        return idsStream.anyMatch(id -> plugId == null ? id == null : id.equals(plugId));
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

    public Path getPluginsRoot() {
        return pluginsRoot;
    }

    public List<PluginWrapper> getPlugins() {
        return pluginManager.getPlugins();
    }

    public String loadPlugin(Path path) {
        String id = pluginManager.loadPlugin(path);
        if (id != null) {
            logger.debug("Loaded plugin {}, now in state {}", id, pluginManager.getPlugin(id).getPluginState().toString());
        } else {
            logger.warn("Plugin found at {} could not be loaded", path.toString());
        }
        return id;
    }

    public boolean unloadPlugin(String pluginId) {
        boolean unloaded = pluginManager.unloadPlugin(pluginId);
        logger.debug("Plugin {} was{} unloaded", pluginId, unloaded ? "" : "not");
        return unloaded;
    }

    public boolean stopPlugin(String pluginId) {

        PluginState state = pluginManager.stopPlugin(pluginId);
        try {
            if (state.equals(PluginState.STOPPED)) {
                authnMethodsExts.forEach(pair -> {
                    if (pluginId.equals(pair.getY())) {
                        authnMethodsExts.remove(pair);
                    }
                });
                zkService.removePluginLabels(pluginId);
                registryHandler.remove(pluginId);
                resourceExtractor.removeDestinationDirectory(getDestinationPathForPlugin(pluginId));
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return state.equals(PluginState.STOPPED);

    }

    public boolean deletePlugin(String pluginId) {

        //Not used, see: https://github.com/pf4j/pf4j/issues/217
        boolean success = pluginManager.deletePlugin(pluginId);
        if (!success) {
            logger.warn("Plugin '{}' could not be unloaded or deleted", pluginId);
        }
        return success;

    }

    public boolean startPlugin(String pluginId) {
        return startPlugin(pluginId, true);
    }

    private boolean startPlugin(String pluginId, boolean refreshLabels) {

        boolean success = false;
        PluginState state = pluginManager.startPlugin(pluginId);
        Path path = pluginManager.getPlugin(pluginId).getPluginPath();

        if (PluginState.STARTED.equals(state)) {
            parsePluginAuthnMethodExtensions(pluginId);

            /*
            //Notifies activation/deactivation for extensions handling authentication methods
            Set<String> acrs = configurationHandler.retrieveAcrs();
            authnMethodsExts.forEach(pair -> pair.getX().deactivate());
            for (String acr : acrs) {
                getExtensionForAuthnMethod(acr).activate();
            } */
            reconfigureServices(pluginId, path, pluginManager.getPluginClassLoader(pluginId));
            success = true;

            if (refreshLabels) {
                zkService.refreshLabels();
            }
        } else {
            logger.warn("Plugin loaded from {} not started. Current state is {}", path.toString(), state == null ? null : state.toString());
        }
        return success;

    }

    private void reconfigureServices(String pluginId, Path path, ClassLoader cl) {

        try {
            extractResources(pluginId, path);
        } catch (IOException e) {
            logger.error("Error when extracting plugin resources");
            logger.error(e.getMessage(), e);
        }
        zkService.readPluginLabels(pluginId, path);
        registryHandler.scan(pluginId, path, cl);

    }

    private void purgePluginsPath() {

        //Deletes all files in path directory as a consequence of https://github.com/pf4j/pf4j/issues/217
        //Also prevents cheating...
        try {
            List<PluginInfo> pls = Optional.ofNullable(mainSettings.getKnownPlugins()).orElse(Collections.emptyList());
            List<String> validFileNames = pls.stream().map(PluginInfo::getRelativePath).collect(Collectors.toList());

            Files.list(pluginsRoot).forEach(p -> {
                if (Files.isRegularFile(p)) {
                    if (!validFileNames.contains(p.getFileName().toString())) {
                        try {
                            Files.delete(p);
                        } catch (Exception e) {
                            logger.error("Error deleting unnecesary file {}: {}", p.toString(), e.getMessage());
                        }
                    }
                } else if (Files.isDirectory(p)) {
                    //TODO add support for directory-based plugins
                }
            });

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            logger.warn("An error occured while cleaning plugins directory {}", pluginsRoot.toString());
        }

    }

}
