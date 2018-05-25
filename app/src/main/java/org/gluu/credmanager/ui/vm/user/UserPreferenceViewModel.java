/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.ui.vm.user;

import org.gluu.credmanager.core.ConfigurationHandler;
import org.gluu.credmanager.core.ExtensionsManager;
import org.gluu.credmanager.extension.AuthnMethod;
import org.gluu.credmanager.misc.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.DependsOn;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zkplus.cdi.DelegatingVariableResolver;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This is the ViewModel of page fragment preferred.zul (and the fragments included by it). It controls the functionality
 * for setting the user's preferred authentication method when second factor authentication is enabled
 * @author jgomer
 */
@VariableResolver(DelegatingVariableResolver.class)
public class UserPreferenceViewModel extends UserViewModel {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @WireVariable("configurationHandler")
    private ConfigurationHandler confHandler;

    @WireVariable("extensionsManager")
    private ExtensionsManager extManager;

    private String noMethodName;
    private String prevSelectedMethod;
    private String selectedMethod;
    private List<Pair<String, String>> availMethods;

    private boolean uiEditing;
    private boolean uiEditable;
    private boolean uiNotEnoughCredsFor2FA;

    public boolean isUiNotEnoughCredsFor2FA() {
        return uiNotEnoughCredsFor2FA;
    }

    public boolean isUiEditing() {
        return uiEditing;
    }

    public boolean isUiEditable() {
        return uiEditable;
    }

    public List<Pair<String, String>> getAvailMethods() {
        return availMethods;
    }

    public String getSelectedMethod() {
        return selectedMethod;
    }

    @DependsOn("selectedMethod")
    public String getSelectedMethodName() {

        Optional<String> optCred = Optional.ofNullable(selectedMethod).map(acr -> {
            AuthnMethod extension = extManager.getExtensionForAuthnMethod(acr);
            return extension == null ? null : Labels.getLabel(extension.getUINameKey());
        });
        return optCred.orElse(noMethodName);
    }

    @Init(superclass = true)
    public void childInit() {

        selectedMethod = user.getPreferredMethod();
        noMethodName = Labels.getLabel("usr.method.none");

        Set<String> enabledMethods = confHandler.getEnabledAcrs();
        List<Pair<AuthnMethod, Integer>> userMethodsCount = userService.getUserMethodsCount(user.getId(), enabledMethods);

        availMethods = userMethodsCount.stream().map(Pair::getX)
                .map(aMethod -> new Pair<>(aMethod.getAcr(), Labels.getLabel(aMethod.getUINameKey(), aMethod.getName())))
                .collect(Collectors.toList());

        int totalCreds = userMethodsCount.stream().mapToInt(Pair::getY).sum();
        logger.info("Number of credentials for user {}: {}", user.getUserName(), totalCreds);

        //Note: It may happen user already has enrolled credentials, but admin changed availability of method. In that
        //case user should not be able to edit
        uiEditable = totalCreds >= confHandler.getSettings().getMinCredsFor2FA() && availMethods.size() > 0;
        uiNotEnoughCredsFor2FA = totalCreds < confHandler.getSettings().getMinCredsFor2FA() && enabledMethods.size() > 0;

        availMethods.add(new Pair<>(null, noMethodName));

    }

    @NotifyChange({"uiEditing", "selectedMethod"})
    @Command
    public void cancel() {
        uiEditing = false;
        selectedMethod = prevSelectedMethod;
    }

    @NotifyChange({"uiEditing", "selectedMethod"})
    @Command
    public void update() {

        uiEditing = false;
        //saves to LDAP and updates user object afterwards
        if (userService.setPreferredMethod(user, selectedMethod)) {
            Utils.showMessageUI(true);
        } else {
            selectedMethod = prevSelectedMethod;
            Utils.showMessageUI(false);
        }

    }

    @NotifyChange({"uiEditing"})
    @Command
    public void prepareUpdateMethod() {
        prevSelectedMethod = selectedMethod;
        uiEditing = true;
    }

}
