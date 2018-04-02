/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.ui.vm;

import org.gluu.credmanager.core.ExtensionsManager;
import org.gluu.credmanager.core.SessionContext;
import org.gluu.credmanager.extension.UserMenuItem;
import org.gluu.credmanager.plugins.menuitem.BackHomeExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.bind.annotation.Init;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zkplus.cdi.DelegatingVariableResolver;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author jgomer
 */
@VariableResolver(DelegatingVariableResolver.class)
public class MenuViewModel {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @WireVariable
    private SessionContext sessionContext;

    @WireVariable("extensionsManager")
    private ExtensionsManager extManager;

    private List<UserMenuItem> menuItems;

    public List<UserMenuItem> getMenuItems() {
        return menuItems;
    }

    @Init
    public void init() {
        String userId = sessionContext.getUser().getId();
        String url = Executions.getCurrent().getDesktop().getRequestPath();

        menuItems = extManager.getSystemExtensionsForClass(UserMenuItem.class).stream().filter(ext -> ext.isDisplayable(userId, url))
                .collect(Collectors.toList());

        //Find "Go home" and put it at the head of the list
        Optional<UserMenuItem> itemOptional = menuItems.stream().filter(ext -> BackHomeExtension.class.equals(ext.getClass())).findAny();
        if (itemOptional.isPresent()) {
            menuItems.remove(itemOptional.get());
            menuItems.add(0, itemOptional.get());
        }


    }

}
