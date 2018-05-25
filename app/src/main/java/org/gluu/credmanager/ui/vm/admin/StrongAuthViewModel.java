/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.ui.vm.admin;

import org.gluu.credmanager.conf.sndfactor.EnforcementPolicy;
import org.gluu.credmanager.misc.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zkplus.cdi.DelegatingVariableResolver;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Messagebox;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gluu.credmanager.conf.sndfactor.EnforcementPolicy.CUSTOM;
import static org.gluu.credmanager.conf.sndfactor.EnforcementPolicy.EVERY_LOGIN;

/**
 * @author jgomer
 */
@VariableResolver(DelegatingVariableResolver.class)
public class StrongAuthViewModel extends MainViewModel {

    public static final Pair<Integer, Integer> BOUNDS_MINCREDS_2FA = new Pair<>(1,3);

    private Logger logger = LoggerFactory.getLogger(getClass());

    private int minCreds2FA;
    private List<Integer> minCredsList;
    private Set<String> enforcementPolicies;

    public List<Integer> getMinCredsList() {
        return minCredsList;
    }

    public int getMinCreds2FA() {
        return minCreds2FA;
    }

    public Set<String> getEnforcementPolicies() {
        return enforcementPolicies;
    }

    @Init//(superclass = true)
    public void init() {
        reloadConfig();
    }

    private void reloadConfig() {

        minCreds2FA = getSettings().getMinCredsFor2FA();
        enforcementPolicies = getSettings().getEnforcement2FA().stream().map(EnforcementPolicy::toString).collect(Collectors.toSet());

        if (minCredsList == null) {
            minCredsList = new ArrayList<>();
            for (int i = BOUNDS_MINCREDS_2FA.getX(); i <= BOUNDS_MINCREDS_2FA.getY(); i++) {
                minCredsList.add(i);
            }
        }

    }

    @NotifyChange("enforcementPolicies")
    @Command
    public void checkPolicy(@BindingParam("evt") Event event) {

        Checkbox box = (Checkbox) event.getTarget();
        String policy = box.getId();

        if (box.isChecked()) {
            enforcementPolicies.add(policy);
        } else {
            enforcementPolicies.remove(policy);
        }
        if (enforcementPolicies.contains(EVERY_LOGIN.toString())) {
            enforcementPolicies = Stream.of(EVERY_LOGIN.toString()).collect(Collectors.toSet());
        } else if (enforcementPolicies.contains(CUSTOM.toString())) {
            enforcementPolicies = Stream.of(CUSTOM.toString()).collect(Collectors.toSet());
        }
    }

    @Command
    public void change2FASettings(@BindingParam("val") Integer val){

        val += BOUNDS_MINCREDS_2FA.getX();

        if (val == 1) {     //only one sucks
            promptBefore2FAProceed(Labels.getLabel("adm.strongauth_warning_one"), val);
        } else if (val > minCreds2FA) {   //maybe problematic...
            promptBefore2FAProceed(Labels.getLabel("adm.strongauth_warning_up", new Integer[]{ minCreds2FA }), val);
        } else {
            processUpdate(val);
        }

    }

    private void promptBefore2FAProceed(String message, int newval){

        Messagebox.show(message, null, Messagebox.YES | Messagebox.NO, Messagebox.EXCLAMATION,
                event -> {
                    if (Messagebox.ON_YES.equals(event.getName())) {
                        processUpdate(newval);
                    } else {  //Revert to last known working (or accepted)
                        reloadConfig();
                        BindUtils.postNotifyChange(null, null, StrongAuthViewModel.this, "minCreds2FA");
                        BindUtils.postNotifyChange(null, null, StrongAuthViewModel.this, "enforcementPolicies");
                    }
                }
        );

    }

    private void processUpdate(int newval) {
        update2FASettings(newval, enforcementPolicies.stream().map(EnforcementPolicy::valueOf).collect(Collectors.toList()));
        reloadConfig();
    }

    private void update2FASettings(int minCreds, List<EnforcementPolicy> policies){

        getSettings().setMinCredsFor2FA(minCreds);
        getSettings().setEnforcement2FA(policies);
        if (updateMainSettings(Labels.getLabel("adm.methods_change_success"))) {
            logger.info("Changed minimum number of enrolled credentials for 2FA usage to {}", minCreds);
            logger.info("Changed 2FA enforcement policy to {}", policies);
        }

    }

}
