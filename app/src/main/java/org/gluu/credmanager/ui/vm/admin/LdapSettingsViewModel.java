/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.ui.vm.admin;

import org.gluu.credmanager.conf.LdapSettings;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.service.LdapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zkplus.cdi.DelegatingVariableResolver;
import org.zkoss.zul.Messagebox;

/**
 * @author jgomer
 */
@VariableResolver(DelegatingVariableResolver.class)
public class LdapSettingsViewModel extends MainViewModel {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @WireVariable
    private LdapService ldapService;

    private LdapSettings ldapSettings;

    public LdapSettings getLdapSettings() {
        return ldapSettings;
    }

    public void setLdapSettings(LdapSettings ldapSettings) {
        this.ldapSettings = ldapSettings;
    }

    @Init//(superclass = true)
    public void init() {
        reloadConfig();
    }

    @NotifyChange({"ldapSettings"})
    @Command
    public void save() {

        //salt is optional
        if (Utils.isNotEmpty(ldapSettings.getOxLdapLocation()) && ldapSettings.getOxLdapLocation().trim().length() > 0) {
            String msg = updateLdapSettings();
            if (msg != null) {
                reloadConfig();
                Messagebox.show(msg, null, Messagebox.OK, Messagebox.EXCLAMATION);
            } else {
                getSettings().setLdapSettings(ldapSettings);
                updateMainSettings();
            }
            //BindUtils.postNotifyChange(null, null, this, "ldapSettings");
        } else {
            Utils.showMessageUI(false, Labels.getLabel("adm.ldap_nonempty"));
        }

    }

    private void reloadConfig() {
        ldapSettings = (LdapSettings) Utils.cloneObject(getSettings().getLdapSettings());
    }

    //This method does not change application level settings
    private String updateLdapSettings() {

        boolean success = false;
        String msg = null;
        try {
            logger.info("Testing newer LDAP settings");
            success = ldapService.setup(ldapSettings);
        } catch (Exception e) {
            msg = e.getMessage();
        }
        if (!success && msg == null) {
            msg = Labels.getLabel("adm.ldap_novalid");
        }
        if (!success) {
            try {
                //Revert to good settings
                logger.warn("Reverting to previously working LDAP settings");
                if (ldapService.setup(getSettings().getLdapSettings())) {
                    msg += "\n" + Labels.getLabel("admin.reverted");
                } else {
                    msg += "\n" + Labels.getLabel("admin.error_reverting");
                }
            } catch (Exception e) {
                msg += "\n" + Labels.getLabel("admin.error_reverting");
                logger.error(e.getMessage(), e);
            }
        }
        return msg;

    }

}
