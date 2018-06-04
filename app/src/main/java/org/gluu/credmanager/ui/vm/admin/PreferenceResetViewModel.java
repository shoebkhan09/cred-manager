/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.ui.vm.admin;

import org.gluu.credmanager.core.UserService;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.ui.model.PersonSearchMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.cdi.DelegatingVariableResolver;
import org.zkoss.zul.Checkbox;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jgomer
 */
@VariableResolver(DelegatingVariableResolver.class)
public class PreferenceResetViewModel extends MainViewModel {

    private static final int MINLEN_SEARCH_PATTERN = 3;

    private Logger logger = LoggerFactory.getLogger(getClass());

    @WireVariable
    private UserService userService;

    private String searchPattern;
    private List<PersonSearchMatch> users;

    public String getSearchPattern() {
        return searchPattern;
    }

    public void setSearchPattern(String searchPattern) {
        this.searchPattern = searchPattern;
    }

    public List<PersonSearchMatch> getUsers() {
        return users;
    }

    @Init//(superclass = true)
    public void init() {

    }

    @Command
    public void search(@BindingParam("box") Component box) {

        //Validates if input conforms to requirement of length
        if (Utils.isNotEmpty(searchPattern) && searchPattern.trim().length() < MINLEN_SEARCH_PATTERN) {
            Clients.showNotification(Labels.getLabel("adm.resets_textbox_hint", new Integer[] { MINLEN_SEARCH_PATTERN }),
                    Clients.NOTIFICATION_TYPE_WARNING, box, "before_center", Utils.FEEDBACK_DELAY_ERR);
        } else {
            users = userService.searchUsers(searchPattern.trim()).stream() //avoid UI cheaters by trimming
                    .map(person -> {
                        PersonSearchMatch p = new PersonSearchMatch();
                        p.setGivenName(person.getFirstGivenName());
                        p.setLastName(person.getFirstSn());
                        p.setUserName(person.getUid());
                        p.setId(person.getInum());
                        return p;
                    }).sorted(Comparator.comparing(PersonSearchMatch::getUserName)).collect(Collectors.toList());

            //triggers update of interface
            BindUtils.postNotifyChange(null, null, this, "users");
        }

    }

    @NotifyChange({"users"})
    @Command
    public void doReset() {

        //Pick those that haven't been reset before and that are checked in the grid currently
        List<String> userInums = users.stream().filter(u -> !u.isAlreadyReset() && u.isChecked())
                .map(PersonSearchMatch::getId).collect(Collectors.toList());

        if (userInums.size() > 0) { //proceed only if there is some fresh selection in the grid
            //Perform the actual resetting
            int total = userService.resetPreference(userInums);
            if (total == userInums.size()) {      //Check the no. of users changed matches the expected
                users.forEach(usr -> usr.setAlreadyReset(usr.isChecked()));
                Utils.showMessageUI(true);
            } else {
                //Flush list if something went wrong
                users = null;
                String msg = Labels.getLabel("adm.resets_only_updated", new Integer[] { total });
                Utils.showMessageUI(false, Labels.getLabel("general.error.detailed", new String[] { msg }));
            }
        } else {
            Utils.showMessageUI(false, Labels.getLabel("adm.resets_noselection"));
        }

    }

    @Command
    //This simulates a click on a checkbox (although the click is coming from one made upon a row)
    public void rowClicked(@BindingParam("evt") Event event, @BindingParam("val") PersonSearchMatch user) {

        try {
            //IMPORTANT: Assuming the check is the first child of row!
            Checkbox box = (Checkbox) event.getTarget().getFirstChild();
            if (!box.isDisabled()) {
                //Simulate check on the checkbox
                box.setChecked(!box.isChecked());
                //Sync the user paired to this checkbox
                user.setChecked(box.isChecked());
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

    }

    @NotifyChange({"users", "searchPattern"})
    @Command
    public void cancelReset() {
        //Provoke the grid to disappear, and cleaning the search textbox
        users = null;
        searchPattern = null;
    }

}
