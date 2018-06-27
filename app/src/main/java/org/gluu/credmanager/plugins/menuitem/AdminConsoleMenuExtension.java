/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.plugins.menuitem;

import org.gluu.credmanager.extension.NavigationMenuItem;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.service.LdapService;
import org.pf4j.Extension;

/**
 * @author jgomer
 */
@Extension
public class AdminConsoleMenuExtension implements NavigationMenuItem {

    private LdapService ldapService;

    public AdminConsoleMenuExtension() {
        ldapService = Utils.managedBean(LdapService.class);
    }

    public boolean isDisplayable(String userId, String url) {
        return ldapService.isAdmin(userId) && url.split("/").length == 2;   //it's a first level url (e.g /user.zul)
    }

    public String getPageUrl() {
        return "/admin.zul";
    }

    public String getIconImageUrl() {
        return "images/administration.png";
    }

    public String getLabel() {
        return null;
    }

    public String getUiLabelKey() {
        return "adm.go_console";
    }

    public String getStyle() {
        return "opacity:0.7";
    }

}
