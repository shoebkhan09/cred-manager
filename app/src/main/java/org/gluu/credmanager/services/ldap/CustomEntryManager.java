package org.gluu.credmanager.services.ldap;

import org.gluu.credmanager.misc.Utils;
import org.gluu.site.ldap.OperationsFacade;
import org.gluu.site.ldap.persistence.LdapEntryManager;

/**
 * Created by jgomer on 2017-08-10.
 */
public class CustomEntryManager extends LdapEntryManager {

    public CustomEntryManager(OperationsFacade facade){
        super(facade);
    }

    @Override
    public <T> T merge(T entry){
        Utils.nullEmptyLists(entry);
        return super.merge(entry);
    }

}