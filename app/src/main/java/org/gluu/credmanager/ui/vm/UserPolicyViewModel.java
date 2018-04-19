/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.ui.vm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.sndfactor.EnforcementPolicy;
import org.gluu.credmanager.conf.sndfactor.TrustedDevice;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zul.Checkbox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gluu.credmanager.conf.sndfactor.EnforcementPolicy.EVERY_LOGIN;

/**
 * Created by jgomer on 2018-04-15.
 */
public class UserPolicyViewModel extends UserViewModel {

    private Logger logger = LogManager.getLogger(getClass());

    private boolean uiHasPreferredMethod;
    private boolean uiAllowedToSetPolicy;
    private Set<String> enforcementPolicies;
    private List<TrustedDevice> trustedDevices;

    public boolean isUiHasPreferredMethod() {
        return uiHasPreferredMethod;
    }

    public boolean isUiAllowedToSetPolicy() {
        return uiAllowedToSetPolicy;
    }

    public Set<String> getEnforcementPolicies() {
        return enforcementPolicies;
    }

    public List<TrustedDevice> getTrustedDevices() {
        return trustedDevices;
    }

    @Init(superclass = true)
    public void childInit() throws Exception {
        uiHasPreferredMethod = user.getPreference()!=null;
        uiAllowedToSetPolicy = services.getAppConfig().getConfigSettings().getEnforcement2FA().contains(EnforcementPolicy.CUSTOM);
        enforcementPolicies = new HashSet<>(user.getEnforcementPolicies());
        trustedDevices = new ArrayList<>(user.getTrustedDevices());

        if (enforcementPolicies.isEmpty())
            resetToDefaultPolicy();
    }

    @NotifyChange("enforcementPolicies")
    @Command
    public void checkPolicy(@BindingParam("evt") Event event) {

        Checkbox box = (Checkbox) event.getTarget();
        String policy = box.getId();
        if (box.isChecked())
            enforcementPolicies.add(policy);
        else
            enforcementPolicies.remove(policy);

        if (enforcementPolicies.contains(EVERY_LOGIN.toString()))
            resetToDefaultPolicy();

    }

    @Command
    public void updatePolicy() {

        if (userService.update2FAPolicies(user, enforcementPolicies)) {
            user.setEnforcementPolicies(enforcementPolicies);
            showMessageUI(true);
        } else {
            showMessageUI(false);
        }

    }

    @NotifyChange("trustedDevices")
    @Command
    public void deleteDevice(@BindingParam("idx") int index) {

        if (userService.deleteTrustedDevice(user, trustedDevices, index)) {
            user.setTrustedDevices(trustedDevices);
            showMessageUI(true);
        } else {
            showMessageUI(false);
        }
    }


    @NotifyChange("enforcementPolicies")
    @Command
    public void cancel() {
        logger.debug(user.getEnforcementPolicies());
        enforcementPolicies = new HashSet<>(user.getEnforcementPolicies());
    }

    private void resetToDefaultPolicy() {
        enforcementPolicies = Stream.of(EVERY_LOGIN.toString()).collect(Collectors.toSet());
    }

}
