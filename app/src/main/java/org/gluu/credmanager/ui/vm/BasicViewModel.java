package org.gluu.credmanager.ui.vm;

import org.zkoss.bind.annotation.*;
import org.zkoss.zk.ui.Executions;

/**
 * Created by jgomer on 2017-07-22.
 */
public class BasicViewModel {

    private boolean onMobile;

    @Init
    public void init(){
        // Detects whether client is mobile device or not (such as Android or iOS device)
        onMobile=Executions.getCurrent().getBrowser("mobile") != null;
    }

    public boolean isOnMobile() {
        return onMobile;
    }

//TODO: in general.zul place width decision on css? merge this with pageinitiator?
}
