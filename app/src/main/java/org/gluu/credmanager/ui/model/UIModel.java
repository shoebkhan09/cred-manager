package org.gluu.credmanager.ui.model;

import org.gluu.credmanager.conf.CredentialType;
import org.zkoss.util.Pair;
import org.zkoss.zul.ListModelList;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by jgomer on 2017-07-22.
 * Utility class for creating specific models if they are needed ("models" in the MVVM pattern). Every public static
 * method here should account for a model. Often in ZK MVVM a list of objects suffices for use as a model...
 */
public class UIModel {

    public static ListModelList<Pair<CredentialType, String>> getCredentialList(Set<CredentialType> creds){

        Stream<Pair<CredentialType, String>> streamPairs=creds.stream().map(UIModel::createPair);
        List<Pair<CredentialType, String>> list=streamPairs.collect(Collectors.toList());
        return new ListModelList<>(list);
    }

    private static Pair<CredentialType, String> createPair(CredentialType cdtype){
        return new Pair(cdtype, cdtype.getUIName());
    }

}
