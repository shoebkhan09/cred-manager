/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.plugins.menuitem;

import org.gluu.credmanager.extension.NavigationMenuItem;
import org.gluu.credmanager.misc.Utils;
import org.pf4j.Extension;

/**
 * @author jgomer
 */
@Extension
public class BackHomeExtension implements NavigationMenuItem {

    public boolean isDisplayable(String userId, String url) {
        return !(Utils.isEmpty(url) || url.equals("/index.zul"));
    }

    public String getPageUrl() {
        return "/index.zul";
    }

    public String getIconImageUrl() {
        return "images/go-home.png";
    }

    public String getLabel() {
        return null;
    }

    public String getUiLabelKey() {
        return "general.back_main";
    }

    public String getStyle() {
        return "opacity:0.7";
    }


}
