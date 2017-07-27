package org.gluu.credmanager.ui.model;

import org.gluu.credmanager.conf.CredentialType;
import org.zkoss.util.Pair;
import org.zkoss.zul.ListModelSet;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by jgomer on 2017-07-22.
 */
public class UIModel {

    public static ListModelSet<Pair<CredentialType, String>> getCredentialList(Set<CredentialType> creds){

        Stream<Pair<CredentialType, String>> streamPairs=creds.stream().map(UIModel::createPair);
        List<Pair<CredentialType, String>> list=streamPairs.collect(Collectors.toList());
        return new ListModelSet<>(list);
    }

    public static Pair<CredentialType, String> createPair(CredentialType cdtype){
        return new Pair(cdtype, cdtype.getUIName());
    }
}
