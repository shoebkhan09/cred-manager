/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.extension;

/**
 * @author jgomer
 */
public interface MenuItem {
    String getPageUrl();
    String getIconImageUrl();
    String getLabel();
    String getUiLabelKey();
    String getStyle();
}
