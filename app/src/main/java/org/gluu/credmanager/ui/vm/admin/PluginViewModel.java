/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.ui.vm.admin;

import org.gluu.credmanager.conf.PluginInfo;
import org.gluu.credmanager.core.ExtensionsManager;
import org.gluu.credmanager.extension.AuthnMethod;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.ui.model.PluginData;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.bind.BindUtils;
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
public class PluginViewModel extends MainViewModel {

    private static final Class<AuthnMethod> AUTHN_METHOD = AuthnMethod.class;

    private Logger logger = LoggerFactory.getLogger(getClass());

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

    @NotifyChange({"pluginToShow", "uiAdding"})
    @Command
    public void showPlugin(@BindingParam("id") String pluginId) {
        pluginToShow = pluginList.stream().filter(pl -> pl.getDescriptor().getPluginId().equals(pluginId)).findAny().orElse(null);
        uiAdding = false;
    }

    @NotifyChange({"pluginToShow", "uiAdding"})
    @Command
    public void hidePluginDetails() {
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
                        //Copy the jar to plugins dir
                        try {
                            Path path = Files.write(getPluginDestinationPath(evt.getMedia().getName()), blob, StandardOpenOption.CREATE_NEW);
                            String pluginId = extManager.loadPlugin(path);

                            if (pluginId == null) {
                                logger.warn("Loading plugin from {} returned no pluginId.", path.toString());
                                //Files.delete(path); //IMPORTANT: See note at method getPluginDestinationPath
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

    @Command
    public void deletePlugin(@BindingParam("id") String pluginId, @BindingParam("provider") String provider) {

        provider = Utils.isEmpty(provider) ? Labels.getLabel("adm.plugins_nodata") : provider;
        String msg = Labels.getLabel("adm.plugins_confirm_del", new String[] {pluginId, provider});

        Messagebox.show(msg, null, Messagebox.YES | Messagebox.NO, Messagebox.QUESTION,
                event -> {
                    if (Messagebox.ON_YES.equals(event.getName())) {
                        boolean success = extManager.unloadPlugin(pluginId);
                        //boolean success = extManager.deletePlugin(pluginId);    //IMPORTANT: See note at method getPluginDestinationPath
                        if (success) {
                            PluginData pluginData = pluginList.stream()
                                    .filter(pl -> pl.getDescriptor().getPluginId().equals(pluginId)).findAny().get();
                            pluginList.remove(pluginData);

                            PluginInfo pl = getSettings().getKnownPlugins().stream().filter(apl -> apl.getId().equals(pluginId)).findAny().get();
                            getSettings().getKnownPlugins().remove(pl);
                            updateMainSettings();
                            BindUtils.postNotifyChange(null, null, PluginViewModel.this, "pluginList");
                        } else {
                            Utils.showMessageUI(false);
                        }
                        hidePluginDetails();
                        BindUtils.postNotifyChange(null, null, PluginViewModel.this, "pluginToShow");
                        BindUtils.postNotifyChange(null, null, PluginViewModel.this, "uiAdding");
                    }
                }
        );
    }

    @NotifyChange({"pluginList", "pluginToShow", "uiAdding"})
    @Command
    public void addPlugin() {
        String id = pluginToShow.getDescriptor().getPluginId();
        boolean success = extManager.startPlugin(id);

        if (success) {
            String started = PluginState.STARTED.toString();
            pluginToShow.setState(Labels.getLabel("adm.plugins_state." + started));
            pluginList.add(pluginToShow);

            //Update backend config file
            PluginInfo info = new PluginInfo();
            info.setId(id);
            info.setRelativePath(Paths.get(pluginToShow.getPath()).getFileName().toString());
            info.setState(started);

            List<PluginInfo> list = Optional.ofNullable(getSettings().getKnownPlugins()).orElse(new ArrayList<>());
            list.add(info);

            getSettings().setKnownPlugins(list);
            updateMainSettings();
            hidePluginDetails();
        } else {
            Utils.showMessageUI(false);
        }

    }

    @NotifyChange({"pluginToShow", "uiAdding"})
    @Command
    public void cancelAdd() {

        String plugId = pluginToShow.getDescriptor().getPluginId();
        hidePluginDetails();
        if (!extManager.unloadPlugin(plugId)) {
            Utils.showMessageUI(false, Labels.getLabel("adm.plugins_error_unloaded"));
        }
        /*
        //IMPORTANT: See note at method getPluginDestinationPath
        try {
            String path = pluginToShow.getPath();
            Files.delete(Paths.get(path));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            String message = Labels.getLabel("adm.plugins_error_delete", new String[] { path });
            Messagebox.show(message, Labels.getLabel("general.warning"), Messagebox.OK, Messagebox.INFORMATION);
        }
        */

    }

    @NotifyChange({"pluginList", "pluginToShow", "uiAdding"})
    @Command
    public void startPlugin(@BindingParam("id") String pluginId) {

        boolean success = extManager.startPlugin(pluginId);
        if (success) {
            String started = PluginState.STARTED.toString();
            PluginData pluginData = pluginList.stream().filter(pl -> pl.getDescriptor().getPluginId().equals(pluginId))
                    .findAny().get();
            pluginData.setState(Labels.getLabel("adm.plugins_state." + started));

            PluginInfo pl = getSettings().getKnownPlugins().stream().filter(apl -> apl.getId().equals(pluginId)).findAny().get();
            pl.setState(started);
            updateMainSettings();
        } else {
            Utils.showMessageUI(false);
        }
        hidePluginDetails();

    }

    @NotifyChange({"pluginList", "pluginToShow", "uiAdding"})
    @Command
    public void stopPlugin(@BindingParam("id") String pluginId) {

        boolean success = extManager.stopPlugin(pluginId);
        if (success) {
            String stopped = PluginState.STOPPED.toString();
            PluginData pluginData = pluginList.stream().filter(pl -> pl.getDescriptor().getPluginId().equals(pluginId))
                    .findAny().get();
            pluginData.setState(Labels.getLabel("adm.plugins_state." + stopped));

            PluginInfo pl = getSettings().getKnownPlugins().stream().filter(apl -> apl.getId().equals(pluginId)).findAny().get();
            pl.setState(stopped);
            updateMainSettings();
        } else {
            Utils.showMessageUI(false);
        }
        hidePluginDetails();

    }

    private void reloadPluginList() {
        //a grid row might look like this: id version (details), state, implements + buttons to stop
        pluginList = new ArrayList<>();
        //extManager.getPlugins().stream().forEach(w -> logger.debug("Found {} {} ",w.getPluginId(), w.getPluginState().toString()));
        extManager.getPlugins().forEach(wrapper -> pluginList.add(buildPluginData(wrapper)));
    }

    private PluginData buildPluginData(PluginWrapper pw) {

        PluginDescriptor pluginDescriptor = pw.getDescriptor();
        PluginData pl = new PluginData();

        //In practice resolved (that is, just loaded not started) could be seen as stopped
        String state = (pw.getPluginState().equals(PluginState.RESOLVED) ? PluginState.STOPPED : pw.getPluginState()).toString();

        pl.setState(Labels.getLabel("adm.plugins_state." + state));
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

            if (Optional.ofNullable(getSettings().getAcrPluginMap())
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

    private Path getPluginDestinationPath(String fileName) {
        /*
        Add some "random" suffix since the same file can be uploaded multiples because as it's explained at
        https://github.com/pf4j/pf4j/issues/217 there is no effective means to delete a plugin jar file
        */
        String suffix = Long.toString(System.currentTimeMillis());
        int aux = suffix.length();
        suffix = "_" +  suffix.substring(aux - 5, aux);

        aux = fileName.lastIndexOf(".");
        aux = aux == -1 ? fileName.length() : aux;
        fileName = fileName.substring(0, aux) + suffix + ".jar";
        return Paths.get(extManager.getPluginsRoot().toString(), fileName);

    }

}
