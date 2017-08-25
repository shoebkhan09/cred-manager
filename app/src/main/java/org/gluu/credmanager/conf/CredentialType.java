package org.gluu.credmanager.conf;

import org.zkoss.util.resource.Labels;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jgomer on 2017-07-10.
 */
public enum CredentialType {
    SUPER_GLUU      ("super_gluu", null),
    SECURITY_KEY    ("u2f", null),
    OTP             ("otp", null),
    VERIFIED_PHONE  ("twilio_sms", "twilio");

    private String name;
    private String alternativeName;

    //Array with all possible acr values that may exist in a Gluu serer installation and which are related to credential types for this app
    public static final List<String> ACR_NAMES_SUPPORTED;

    static{
        String alter;
        List<String> names=new ArrayList<>();
        //Accumulates names and alternative names in a list
        for (CredentialType cdtype : CredentialType.values()) {
            names.add(cdtype.getName());
            alter=cdtype.getAlternativeName();
            if (alter!=null)
                names.add(alter);
        }
        ACR_NAMES_SUPPORTED=names;
    }

    public String getName() {
        return name;
    }

    public String getAlternativeName() {
        return alternativeName;
    }


    CredentialType(String name, String altername){
        this.name=name;
        alternativeName=altername;
    }

    public static CredentialType get(String name){
        CredentialType type=null;

        for (CredentialType cdtype : CredentialType.values())
            if (cdtype.name.equals(name) || (cdtype.alternativeName!=null && cdtype.alternativeName.equals(name)))
                type=cdtype;
        return type;
    }

    public String getUIName(){
        return Labels.getLabel("general.credentials." + this);
    }

    public List<String> getAcrsList(){

        List<String> acrs=new ArrayList<>();
        acrs.add(name);
        if (alternativeName!=null)
            acrs.add(alternativeName);
        return acrs;
    }

}