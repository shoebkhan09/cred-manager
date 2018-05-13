/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.ui.vm.admin;

import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.NotifyChange;

/**
 * @author jgomer
 */
public class MainViewModel {

    private String subpage;

    public String getSubpage() {
        return subpage;
    }

    /**
     * Changes the page loaded in the content area. Also sets values needed in the UI (these are taken directly from
     * calls to AdminService's getConfigSettings method.
     * @param page The (string) url of the page that must be loaded (by default /admin/default.zul is being shown)
     */
    @Command
    @NotifyChange({"subpage"})
    public void loadSubPage(@BindingParam("page") String page) {
        subpage = page;
    }

}
