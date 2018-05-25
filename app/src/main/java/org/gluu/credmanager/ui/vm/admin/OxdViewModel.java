/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.ui.vm.admin;

import org.gluu.credmanager.conf.OxdSettings;
import org.gluu.credmanager.core.OxdService;
import org.gluu.credmanager.misc.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zkplus.cdi.DelegatingVariableResolver;
import org.zkoss.zul.Messagebox;

import java.net.InetSocketAddress;
import java.util.stream.Stream;

/**
 * @author jgomer
 */
@VariableResolver(DelegatingVariableResolver.class)
public class OxdViewModel extends MainViewModel {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @WireVariable
    private OxdService oxdService;

    private OxdSettings oxdSettings;

    public OxdSettings getOxdSettings() {
        return oxdSettings;
    }

    @Init//(superclass = true)
    public void init() {
        reloadConfig();
    }

    private void reloadConfig() {
        oxdSettings = (OxdSettings) Utils.cloneObject(getSettings().getOxdSettings());
    }

    @NotifyChange("oxdSettings")
    @Command
    public void switchUseOxdExtension(@BindingParam("use") boolean useExtension){
        oxdSettings.setUseHttpsExtension(useExtension);
        oxdSettings.setHost(null);
        oxdSettings.setPort(0);
    }

    @NotifyChange({"oxdSettings"})
    @Command
    public void saveOxdSettings() {

        int oxdPort = oxdSettings.getPort();
        String oxdHost = oxdSettings.getHost();
        String postlogoutUrl = Utils.isValidUrl(oxdSettings.getPostLogoutUri()) ? oxdSettings.getPostLogoutUri() : null;

        if (Stream.of(oxdHost, postlogoutUrl).allMatch(Utils::isNotEmpty) && oxdPort > 0 && oxdPort < 65536) {

            boolean connected = false;    //Try to guess if it looks like an oxd-server
            try {
                oxdHost = oxdHost.trim();
                if (oxdSettings.isUseHttpsExtension()) {
                    connected = true;     //TODO: Check not implemented
                } else {
                    connected = Utils.hostAvailabilityCheck(new InetSocketAddress(oxdHost, oxdPort), 3500);
                }
            } catch (Exception e){
                logger.error(e.getMessage(), e);
            }
            if (!connected) {
                Messagebox.show(Labels.getLabel("adm.oxd_no_connection"), null, Messagebox.YES | Messagebox.NO, Messagebox.QUESTION,
                        event -> {
                            if (Messagebox.ON_YES.equals(event.getName())) {
                                processUpdate();
                            } else {
                                reloadConfig();
                            }
                        }
                );
            } else {
                processUpdate();
            }
        } else {
            Messagebox.show(Labels.getLabel("adm.oxd_no_settings"), null, Messagebox.OK, Messagebox.INFORMATION);
        }

    }

    @NotifyChange("oxdSettings")
    @Command
    public void cancel(){
        reloadConfig();
    }

    private void processUpdate() {

        OxdSettings lastWorkingConfig = getSettings().getOxdSettings();
        String msg = updateOxdSettings(lastWorkingConfig);
        if (msg == null) {
            updateMainSettings();
        } else {
            oxdSettings = lastWorkingConfig;
            msg = Labels.getLabel("general.error.detailed", new String[] { msg });
            Messagebox.show(msg, null, Messagebox.OK, Messagebox.EXCLAMATION);
        }

    }

    //TODO: check logout url side effects
    private String updateOxdSettings(OxdSettings lastWorkingConfig) {

        String msg = null;
        //Triger a new registration only if host/port changed, otherwise call update site operation
        if (lastWorkingConfig.getHost().equalsIgnoreCase(oxdSettings.getHost()) && lastWorkingConfig.getPort() == oxdSettings.getPort()) {
            try {
                if (!oxdService.updateSite(oxdSettings.getPostLogoutUri(), null)) {
                    msg = Labels.getLabel("adm.oxd_site_update_failure");
                }
            } catch (Exception e) {
                msg = e.getMessage();
                logger.error(msg, e);
            }
        } else {
            try {
                oxdService.setSettings(oxdSettings, true);
                extendLifeTime();

                //remove unneeded client
                oxdService.removeSite(lastWorkingConfig.getClient().getOxdId());
            } catch (Exception e){
                msg = e.getMessage();
                try {
                    logger.warn("Reverting to previous working OXD settings");
                    //Revert to last working settings
                    oxdService.setSettings(lastWorkingConfig, false);
                } catch (Exception e1){
                    msg += "\n" + Labels.getLabel("admin.error_reverting");
                    logger.error(e1.getMessage(), e1);
                }
            }
        }
        return msg;

    }

    private void extendLifeTime() {
        if (!oxdService.extendSiteLifeTime()) {
            logger.warn("An error occured while extending the lifetime of the associated oxd client.");
        }
    }


}
