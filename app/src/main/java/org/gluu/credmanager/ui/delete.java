package org.gluu.credmanager.ui;

import org.gluu.credmanager.core.ExtensionsManager;
import org.gluu.credmanager.extension.AuthnMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.image.Image;
import org.zkoss.util.media.Media;
import org.zkoss.zk.ui.event.UploadEvent;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zkplus.cdi.DelegatingVariableResolver;

@VariableResolver(DelegatingVariableResolver.class)
public class delete {

    private Logger LOGGER = LoggerFactory.getLogger(getClass());

    @WireVariable
    private ExtensionsManager extensionsManager;

    @Init
    public void cinit() throws Exception {
        AuthnMethod twilioHandler = extensionsManager.getExtensionForAuthnMethod("twilio_sms");
        twilioHandler.getEnrolledCreds("@!3245.DF39.6A34.9E97!0001!513A.9888!0000!A8F2.DE1E.D7FB");

        AuthnMethod otpHandler = extensionsManager.getExtensionForAuthnMethod("otp");
        otpHandler.getEnrolledCreds("@!3245.DF39.6A34.9E97!0001!513A.9888!0000!A8F2.DE1E.D7FB");
    }

    @Command
    public void uploaded(@BindingParam("uplEvent") UploadEvent evt) {
        Media media = evt.getMedia();
        if (media instanceof Image) {
            LOGGER.debug("size of {}", media.getByteData().length);
        }
    }

}
