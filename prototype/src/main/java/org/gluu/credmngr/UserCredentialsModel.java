package org.gluu.credmngr;

import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zul.Label;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Window;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.gluu.credmngr.Credential.CredentialType.VERIFIED_PHONE;

/**
 * Created by jgomer on 2017-06-23.
 */
public class UserCredentialsModel {
    private static List<Credential> credentials;
    private List<Credential> verifiedPhones;
    private static Phone newPhone;
    private static Credential editionCred;

    static {
        //newPhone=new Phone();
        Credential credArray[]=new Credential[]{

                  new Credential(VERIFIED_PHONE, "+1 555 555 5555", "Will's verizon", new Date(), null)
                , new Credential(VERIFIED_PHONE, "+1 123 555 5555", "Will's iPhone vodafone", new Date(), new Date())
        };
        credentials=new ArrayList<>(Arrays.asList(credArray));
        //credentials= Arrays.asList(new Credential[]{});
        resetNewPhone();
        resetEditCred();
    }

    public UserCredentialsModel(){
    }

    public static void resetNewPhone(){
        newPhone=new Phone();
    }

    public static void resetEditCred() { editionCred = new Credential(); }

    public List<Credential> getVerifiedPhones() {
        Stream<Credential> stream=credentials.stream().filter(cred -> cred.getType().equals(VERIFIED_PHONE));
        stream=stream.sorted(Comparator.comparing(Credential::getTimeAdded));
        return stream.collect(Collectors.toList());
    }

    public void setVerifiedPhones(List<Credential> verifiedPhones) {
        this.verifiedPhones = verifiedPhones;
    }

    public Phone getNewPhone() {
        return newPhone;
    }

    public void setNewPhone(Phone newPhone) {
        this.newPhone = newPhone;
    }

    public static Credential getEditionCred() {
        return editionCred;
    }

    public static void setEditionCred(Credential editionCred) {
        UserCredentialsModel.editionCred = editionCred;
    }

    @Command
    @NotifyChange({"verifiedPhones", "newPhone"})
    public void addPhone(){//@BindingParam("phone")	Phone phone
        credentials.add(new Credential(VERIFIED_PHONE, newPhone.getNumber(), newPhone.getName(), new Date(),null));
        resetNewPhone();
    }

    /*
    @Command
    @NotifyChange({"newPhone"})
    public void cancelAddPhone(@BindingParam("target")Component target){
        ((Textbox)target).setValue("");
        resetNewPhone();
    }
    */
    @Command
    public void checkCode(@BindingParam("target1")Component target1, @BindingParam("target2")Component target2, @BindingParam("code") String code){
        if (code!=null) {
            boolean match=code.matches("^\\d{6}$");
            target1.setVisible(match);
            target2.setVisible(match);
        }
    }

    @Command
    //@NotifyChange({"{verifiedPhones"})    Has to be done dynamically
    public void delCredential(@BindingParam("id") String id, @BindingParam("type") Credential.CredentialType type, @BindingParam("nick") String nick){

        String title=null;
        String text=null;
        String success=null;
        switch (type) {
            case VERIFIED_PHONE:
                title=Labels.getLabel("general.phonenumber") + " " + id;
                text=Labels.getLabel("usr.mobile_delete", new Object[]{nick});
                success= Labels.getLabel("usr.mobile_deleted");
            break;
        }

        //Change with lambda
        Messagebox.show(text, title, Messagebox.YES | Messagebox.NO, Messagebox.QUESTION,
                new org.zkoss.zk.ui.event.EventListener<Event>() {
                    public void onEvent(Event event) {
                        if (Messagebox.ON_YES.equals(event.getName())){
                            Stream<Credential> stream= credentials.stream().filter(cred -> (cred.getType()!=type) || (cred.getIdentifier()!=id));
                            credentials=stream.collect(Collectors.toList());

                            //trigger refresh (this method is asynchronous...)
                            BindUtils.postNotifyChange(null,	null, UserCredentialsModel.this, "verifiedPhones");

                            //Notify deletion: use variable success

                        }
                    }
                });

    }

    public String getTextCountPhones(){
        return Labels.getLabel("usr.mobile_summary", new Object[]{getVerifiedPhones().size()});
    }

    @Command
    public void measureStrength(@BindingParam("target")Component target, @BindingParam("pwd") String pwd) {

        String strength=null;
        if (pwd==null)
            pwd="";
        switch (pwd.length()/3){
            case 0: case 1:
                strength="weak";
                break;
            case 2: strength="moderate";
            break;
            case 3: strength="good";
            break;
            default: strength="superb";
        }
        String tmp=Labels.getLabel("usr.passreset_strength_title", new Object[]{strength});
        ((Label) target).setValue(tmp);

    }

    @Command
    public void prepareOnEditionCred(@BindingParam("cred") Credential cred, @BindingParam("target")Component target){
        setEditionCred(cred);
        ((Window)target).doModal();
    }

    @Command
    //@NotifyChange({"{verifiedPhones"})
    public void updateCredential(@BindingParam("nick") String nick, @BindingParam("target")Component target){

        Stream<Credential> stream= credentials.stream().filter(cred -> (cred.getType()!=editionCred.getType()) || (cred.getIdentifier()!=editionCred.getIdentifier()));
        credentials=stream.collect(Collectors.toList());
        editionCred.setNickname(nick);
        credentials.add(editionCred);
        target.setVisible(false);
        //why this:
        BindUtils.postNotifyChange(null,	null, UserCredentialsModel.this, "verifiedPhones");
    }

}