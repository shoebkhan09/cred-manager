/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.ui.vm.admin;

import org.gluu.credmanager.conf.MainSettings;
import org.gluu.credmanager.core.ExtensionsManager;
import org.gluu.credmanager.extension.AuthnMethod;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.ui.model.PluginData;
import org.gluu.credmanager.ui.vm.user.UserViewModel;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.event.UploadEvent;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zkplus.cdi.DelegatingVariableResolver;
import org.zkoss.zul.Messagebox;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * @author jgomer
 */
@VariableResolver(DelegatingVariableResolver.class)
public class PluginViewModel /*extends UserViewModel*/ {

    private static final Class<AuthnMethod> AUTHN_METHOD = AuthnMethod.class;

    private Logger logger = LoggerFactory.getLogger(getClass());

    @WireVariable
    private MainSettings mainSettings;

    @WireVariable("extensionsManager")
    private ExtensionsManager extManager;

    private List<PluginData> pluginList;

    private PluginData pluginToShow;

    private boolean uiAdding;

    public List<PluginData> getPluginList() {
        return pluginList;
    }

    public PluginData getPluginToShow() {
        return pluginToShow;
    }

    public boolean isUiAdding() {
        return uiAdding;
    }

    @Init//(superclass = true)
    public void init() {
        reloadPluginList();
    }

    @NotifyChange({"pluginToShow"})
    @Command
    public void showPlugin(@BindingParam("id") String pluginId) {
        pluginToShow = pluginList.stream().filter(pl -> pl.getDescriptor().getPluginId().equals(pluginId)).findAny().orElse(null);
    }

    @NotifyChange({"pluginToShow", "uiAdding"})
    @Command
    public void hidePluginInfo() {
        pluginToShow = null;
        uiAdding = false;
    }

    @NotifyChange({"pluginToShow", "uiAdding"})
    @Command
    public void uploaded(@BindingParam("uplEvent") UploadEvent evt) {
        try {
            pluginToShow = null;
            byte[] blob = evt.getMedia().getByteData();
            logger.debug("Size of blob received: {} bytes", blob.length);

            try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(blob), false)) {

                Manifest m = jis.getManifest();
                if (m != null) {
                    String id = m.getMainAttributes().getValue("Plugin-Id");
                    String version = m.getMainAttributes().getValue("Plugin-Version");

                    if (pluginList.stream().anyMatch(pl -> pl.getDescriptor().getPluginId().equals(id))) {
                        Utils.showMessageUI(false, Labels.getLabel("adm.plugins_already_existing", new String[] { id }));
                    } else if (Stream.of(id, version).allMatch(Utils::isNotEmpty)) {
                        String fileName = evt.getMedia().getName(); // String.format("%s_%s.jar", id, version);
                        //Copy the jar to plugins dir
                        try {
                            //TODO: https://github.com/pf4j/pf4j/issues/217, copy it to a tmp destination?
                            Path path = Files.write(Paths.get(extManager.getPluginsRoot().toString(), fileName), blob, StandardOpenOption.CREATE_NEW);
                            String pluginId = extManager.loadPlugin(path);
                            /*
                            logger.debug("unaded {}", extManager.unloadPlugin(pluginId));
                            logger.debug("Deleted {} {} ", path, Files.deleteIfExists(path));
                            logger.debug("eists {}", Files.exists(path));
                            */
                            if (pluginId == null) {
                                logger.warn("Loading plugin from {} returned no pluginId.", path.toString());
                                Files.delete(path);
                                Utils.showMessageUI(false);
                            } else {
                                Optional<PluginWrapper> owrp = extManager.getPlugins().stream()
                                        .filter(wrapper -> wrapper.getPluginId().equals(pluginId)).findAny();
                                if (owrp.isPresent()) {
                                    pluginToShow = buildPluginData(owrp.get());
                                    uiAdding = true;
                                } else {
                                    Utils.showMessageUI(false);
                                }
                            }
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                            Utils.showMessageUI(false);
                        }
                    } else {
                        Utils.showMessageUI(false, Labels.getLabel("adm.plugins_invalid_plugin"));
                    }

                } else {
                    Utils.showMessageUI(false, Labels.getLabel("adm.plugins_invalid_plugin"));
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    @NotifyChange({"pluginList"})
    @Command
    public void deletePlugin(@BindingParam("id") String pluginId) {
        //TODO: https://github.com/pf4j/pf4j/issues/217, if not solved disable the plugin (and populate pluginList with started/resolved plugins only filter those )
        boolean success = extManager.deletePlugin(pluginId);
        if (success) {
            PluginData pluginData = pluginList.stream().filter(pl -> pl.getDescriptor().getPluginId().equals(pluginId))
                    .findAny().get();
            pluginList.remove(pluginData);
        } else {
            Utils.showMessageUI(false);
        }
    }

    @NotifyChange({"pluginList", "pluginToShow"})
    @Command
    public void addPlugin() {
        boolean success = extManager.startPlugin(pluginToShow.getDescriptor().getPluginId());
        if (success) {
            pluginToShow.setState(Labels.getLabel("adm.plugins_state." + PluginState.STARTED.toString()));
            pluginList.add(pluginToShow);
            hidePluginInfo();
        } else {
            Utils.showMessageUI(false);
        }

    }

    @NotifyChange({"pluginToShow", "uiAdding"})
    @Command
    public void cancelAdd() {

        String plugId = pluginToShow.getDescriptor().getPluginId();
        String path = pluginToShow.getPath();
        try {
            hidePluginInfo();
            extManager.unloadPlugin(plugId);
            //TODO: https://github.com/pf4j/pf4j/issues/217, uncomment deletion when solved or copy jar to plugins dir
            //Files.delete(Paths.get(path));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            String message = Labels.getLabel("adm.plugins_error_delete", new String[] { path });
            Messagebox.show(message, Labels.getLabel("general.warning"), Messagebox.OK, Messagebox.INFORMATION);
        }

    }

    @NotifyChange({"pluginList"})
    @Command
    public void startPlugin(@BindingParam("id") String pluginId) {

        boolean success = extManager.startPlugin(pluginId);
        if (success) {
            PluginData pluginData = pluginList.stream().filter(pl -> pl.getDescriptor().getPluginId().equals(pluginId))
                    .findAny().get();
            pluginData.setState(Labels.getLabel("adm.plugins_state." + PluginState.STARTED.toString()));
        }
        Utils.showMessageUI(success);

    }

    @NotifyChange({"pluginList"})
    @Command
    public void stopPlugin(@BindingParam("id") String pluginId) {

        boolean success = extManager.stopPlugin(pluginId);
        if (success) {
            PluginData pluginData = pluginList.stream().filter(pl -> pl.getDescriptor().getPluginId().equals(pluginId))
                    .findAny().get();
            pluginData.setState(Labels.getLabel("adm.plugins_state." + PluginState.STOPPED.toString()));
        }
        Utils.showMessageUI(success);

    }

    private void reloadPluginList() {
        //a grid row might look like this: id version (details), state, implements + buttons to stop
        pluginList = new ArrayList<>();
        extManager.getPlugins().forEach(wrapper -> pluginList.add(buildPluginData(wrapper)));
    }

    private PluginData buildPluginData(PluginWrapper pw) {

        PluginDescriptor pluginDescriptor = pw.getDescriptor();
        PluginData pl = new PluginData();

        pl.setState(Labels.getLabel("adm.plugins_state." + pw.getPluginState().toString()));
        pl.setExtensions(buildExtensionList(pw));
        pl.setPath(pw.getPluginPath().toString());
        pl.setDescriptor(pluginDescriptor);

        return pl;

    }

    private List<String> buildExtensionList(PluginWrapper wrapper) {

        List<String> extList = new ArrayList<>();
        PluginManager manager = wrapper.getPluginManager();
        String pluginId = wrapper.getPluginId();

        for (String clsName : manager.getExtensionClassNames(pluginId)) {
            if (!clsName.equals(AUTHN_METHOD.getName())) {
                extList.add(getExtensionLabel(clsName));
            }
        }
        for (AuthnMethod method : manager.getExtensions(AUTHN_METHOD, pluginId)) {
            String text = getExtensionLabel(AUTHN_METHOD.getName(), method.getName());
            String acr = method.getAcr();

            if (Optional.ofNullable(mainSettings.getAcrPluginMap())
                    .map(mapping -> mapping.get(acr).equals(pluginId)).orElse(false)) {
                text += Labels.getLabel("adm.plugins_acr_handler", new String[]{ acr });
            }
            extList.add(text);
        }

        return extList;

    }

    private String getExtensionLabel(String clsName, Object ...args) {
        String text = Labels.getLabel("adm.plugins_extension." + clsName, args);
        return text == null ? clsName.substring(clsName.lastIndexOf(".") + 1) : text;
    }

}
