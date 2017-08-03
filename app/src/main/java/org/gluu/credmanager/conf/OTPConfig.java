package org.gluu.credmanager.conf;

import com.lochbridge.oath.otp.*;
import com.lochbridge.oath.otp.keyprovisioning.OTPKey.OTPType;

/**
 * Created by jgomer on 2017-07-31.
 */
public class OTPConfig {

    private OTPType type;
    private int keyLength;
    private int digits;
    private int lookAheadWindow;
    private int timeStep;

    private String issuer;
    private String label;
    private String registrationUri;

    private HmacShaAlgorithm hmacShaAlgorithm;

    private int qrSize;
    private double qrMSize;

    public boolean isValidConfig(){
        boolean valid=false;

        switch (type){
            case HOTP:
                valid=lookAheadWindow>0;
                break;
            case TOTP:
                valid=timeStep>0 && hmacShaAlgorithm!=null;
                break;
        }
        return valid && keyLength>0 && digits>0;
    }

    public OTPConfig(){

    }

    public OTPType getType() {
        return type;
    }

    public void setType(OTPType type) {
        this.type = type;
    }

    public int getKeyLength() {
        return keyLength;
    }

    public void setKeyLength(int keyLength) {
        this.keyLength = keyLength;
    }

    public int getDigits() {
        return digits;
    }

    public void setDigits(int digits) {
        this.digits = digits;
    }

    public int getLookAheadWindow() {
        return lookAheadWindow;
    }

    public void setLookAheadWindow(int lookAheadWindow) {
        this.lookAheadWindow = lookAheadWindow;
    }

    public int getTimeStep() {
        return timeStep;
    }

    public void setTimeStep(int timeStep) {
        this.timeStep = timeStep;
    }

    public HmacShaAlgorithm getHmacShaAlgorithm() {
        return hmacShaAlgorithm;
    }

    public void setHmacShaAlgorithm(HmacShaAlgorithm hmacShaAlgorithm) {
        this.hmacShaAlgorithm = hmacShaAlgorithm;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getQrSize() {
        return qrSize;
    }

    public void setQrSize(int qrSize) {
        this.qrSize = qrSize;
    }

    public double getQrMSize() {
        return qrMSize;
    }

    public void setQrMSize(double qrMSize) {
        this.qrMSize = qrMSize;
    }

    public String getRegistrationUri() {
        return registrationUri;
    }

    public void setRegistrationUri(String registrationUri) {
        this.registrationUri = registrationUri;
    }
}