/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.services;

import com.lochbridge.oath.otp.*;
import com.lochbridge.oath.otp.keyprovisioning.OTPKey;
import com.lochbridge.oath.otp.keyprovisioning.OTPAuthURIBuilder;
import com.lochbridge.oath.otp.keyprovisioning.OTPKey.OTPType;
import com.google.common.io.BaseEncoding;
import org.gluu.credmanager.conf.AppConfiguration;
import org.gluu.credmanager.conf.OTPConfig;
import org.zkoss.util.Pair;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * Created by jgomer on 2017-08-01.
 * An app. scoped bean that encapsulates logic related to generating and validating OTP keys.
 * See https://tools.ietf.org/html/rfc6238 and https://tools.ietf.org/html/rfc4226.
 */
@ApplicationScoped
public class OTPService{

    @Inject
    AppConfiguration appConfig;

    private OTPConfig conf;

    public OTPService(){
    }

    @PostConstruct
    private void setup(){
        conf=appConfig.getConfigSettings().getOtpConfig();
    }

    public byte[] generateSecretKey(){

        int keyLen=conf.getKeyLength();
        byte bytes[]=new byte[keyLen];
        SecureRandom random=new SecureRandom();
        random.nextBytes(bytes);
        return bytes;

    }

    public String generateSecretKeyUri(byte[] secretKey, String displayName){

        String secretKeyBase32=BaseEncoding.base32().omitPadding().encode(secretKey);
        OTPKey otpKey = new OTPKey(secretKeyBase32, conf.getType());

        OTPAuthURIBuilder uribe=OTPAuthURIBuilder.fromKey(otpKey).label(displayName);
        uribe=uribe.issuer(conf.getIssuer()).digits(conf.getDigits());
        if (conf.getType().equals(OTPType.TOTP))
            uribe=uribe.timeStep(TimeUnit.SECONDS.toMillis(conf.getTimeStep()));

        return uribe.build().toUriString();

    }

    public Pair<Boolean, Long> validateHOTPKey(byte secretKey[], int movingFactor, String otpCode) {
        int digits=conf.getDigits();
        HOTPValidationResult result = HOTPValidator.lookAheadWindow(conf.getLookAheadWindow()).validate(secretKey, movingFactor, digits, otpCode);
        return result.isValid() ? new Pair<>(true, result.getNewMovingFactor()) : new Pair<>(false, null);
    }

    public boolean validateTOTPKey(byte secretKey[], String otpCode) {
        TOTPBuilder builder = TOTP.key(secretKey).digits(conf.getDigits()).hmacSha(conf.getHmacShaAlgorithm());
        String localTotpKey = builder.timeStep(TimeUnit.SECONDS.toMillis(conf.getTimeStep())).build().value();
        return otpCode.equals(localTotpKey);
    }

    public String getExternalHOTPUid(byte secretKey[], long factor){
        return String.format("%s:%s;%s", OTPType.HOTP.getName().toLowerCase(), BaseEncoding.base64Url().encode(secretKey), factor);
    }

    public String getExternalTOTPUid(byte secretKey[]){
        return String.format("%s:%s", OTPType.TOTP.getName().toLowerCase(), BaseEncoding.base64Url().encode(secretKey));
    }

}