package org.gluu.credmanager.services;

import com.lochbridge.oath.otp.*;
import com.lochbridge.oath.otp.keyprovisioning.OTPKey;
import com.lochbridge.oath.otp.keyprovisioning.OTPAuthURIBuilder;
import com.lochbridge.oath.otp.keyprovisioning.OTPKey.OTPType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.BaseEncoding;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.conf.OTPConfig;
import org.gluu.credmanager.core.credential.RegisteredCredential;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ldap.pojo.CustomScript;
import org.xdi.model.SimpleCustomProperty;
import org.zkoss.json.JavaScriptValue;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;

import javax.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Created by jgomer on 2017-08-01.
 * An app. scoped bean that encapsulates logic related to generating and validating OTP keys.
 * See https://tools.ietf.org/html/rfc6238 and https://tools.ietf.org/html/rfc4226.
 * Contains functionalities to compute/parse settings related to QR code generation
 */
@ApplicationScoped
public class OTPService{

    ObjectMapper mapper=new ObjectMapper();
    private Logger logger = LogManager.getLogger(getClass());

    public OTPService(){
    }

    /**
     * Creates an OTPConfig object to hold all properties required for OTP key generation and QR code display
     * @param otpScript Represents the LDAP entry corresponding to the OTP custom script
     * @return null if an error or inconsistency is found while inspecting the configuration properties of the custom script.
     * Otherwise returns a OTPConfig object
     */
    public OTPConfig getConfig(CustomScript otpScript) {

        OTPConfig config=null;

        //Check by description or displayName if this is really OTP script
        if (Utils.stringContains(otpScript.getDescription(), "otp", true) || Utils.stringContains(otpScript.getName(), "otp", true)){

            final OTPConfig cfg = new OTPConfig();
            List<SimpleCustomProperty> properties=otpScript.getProperties();

            properties.stream().forEach(prop -> {
                try {
                    String name = prop.getValue1().toLowerCase();
                    String value = prop.getValue2();

                    switch (name) {
                        case "otp_type":
                            cfg.setType(OTPType.valueOf(value.toUpperCase()));
                            break;
                        case "issuer":
                            cfg.setIssuer(value);
                            break;
                        case "label":
                            cfg.setLabel(value);
                            break;
                        case "registration_uri":
                            cfg.setRegistrationUri(value);
                            break;
                        case "qr_options":
                            //value may come not properly formated (without quotes, for instance...)
                            if (!value.contains("\""))
                                value=value.replaceFirst("mSize","\"mSize\"").replaceFirst("size","\"size\"");

                            JsonNode tree = mapper.readTree(value);

                            if (tree.get("size")!=null)
                                cfg.setQrSize(tree.get("size").asInt());

                            if (tree.get("mSize")!=null)
                                cfg.setQrMSize(tree.get("mSize").asDouble());
                            break;
                    }
                }
                catch(Exception e){
                    logger.error(e.getMessage(), e);
                    cfg.setType(null);
                }
            });

            try {
                //Do not change evaluation order of this 3 predicates
                if (cfg.getType()!=null && readFileSettings(cfg, properties) && cfg.isValidConfig()) {
                    config = cfg;
                    logger.info(Labels.getLabel("app.otp_settings"), mapper.writeValueAsString(cfg));
                }
                else
                    logger.error(Labels.getLabel("app.otp_settings_error"));
            }
            catch (Exception e){
                logger.error(e.getMessage(), e);
            }
        }
        else
            logger.error(Labels.getLabel("app.otp_settings_error"));

        return config;

    }

    private boolean readFileSettings(OTPConfig cfg, List<SimpleCustomProperty> properties){

        boolean invalid=false;
        try {
            //Find the property of the script related to json file
            Stream<SimpleCustomProperty> stream = properties.stream().filter(p -> p.getValue1().toLowerCase().equals("otp_conf_file"));
            Optional<SimpleCustomProperty> optCustProperty=stream.findFirst();
            if (optCustProperty.isPresent()) {

                File f = new File(optCustProperty.get().getValue2());

                if (f.exists()) {
                    JsonNode tree = mapper.readTree(f);
                    JsonNode subNode;

                    switch (cfg.getType()) {
                        case HOTP:
                            subNode = tree.get("htop");  //This is a common mistake in Gluu's opt_configuration.json
                            if (subNode == null)
                                subNode = tree.get("hotp");

                            if (subNode == null)
                                invalid=true;
                            else {
                                //Read data as if it were HOTP
                                cfg.setKeyLength(subNode.get("keyLength").asInt());
                                cfg.setDigits(subNode.get("digits").asInt());
                                cfg.setLookAheadWindow(subNode.get("lookAheadWindow").asInt());
                            }
                            break;
                        case TOTP:
                            subNode = tree.get("totp");

                            if (subNode == null)
                                invalid=true;
                            else {
                                //Read data as if it were TOTP
                                cfg.setKeyLength(subNode.get("keyLength").asInt());
                                cfg.setDigits(subNode.get("digits").asInt());
                                cfg.setTimeStep(subNode.get("timeStep").asInt());

                                String algo = "Hmac" + subNode.get("hmacShaAlgorithm").asText().toUpperCase();
                                cfg.setHmacShaAlgorithm(HmacShaAlgorithm.from(algo));
                            }
                            break;
                        default:
                            invalid=true;
                    }
                }
                else
                    throw new FileNotFoundException();
            }
            else
                invalid=true;
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            invalid=true;
        }
        return !invalid;

    }

    public static byte[] generateSecretKey(int keyLen){

        byte bytes[]=new byte[keyLen];
        SecureRandom random=new SecureRandom();
        random.nextBytes(bytes);
        return bytes;

    }

    public static String generateSecretKeyUri(byte[] secretKey, OTPConfig conf, String displayName){

        String secretKeyBase32=BaseEncoding.base32().encode(secretKey);
        OTPKey otpKey = new OTPKey(secretKeyBase32, conf.getType());

        OTPAuthURIBuilder uribe=OTPAuthURIBuilder.fromKey(otpKey).label(conf.getIssuer() + ":" + displayName);
        uribe=uribe.issuer(conf.getIssuer()).digits(conf.getDigits());
        if (conf.getType().equals(OTPType.TOTP))
            uribe=uribe.timeStep(TimeUnit.SECONDS.toMillis(conf.getTimeStep()));

        return uribe.build().toUriString();

    }

    public static Pair<Boolean, Long> validateHOTPKey(byte secretKey[], int movingFactor, String otpCode, int digits) {
        HOTPValidationResult result = HOTPValidator.lookAheadWindow(1).validate(secretKey, movingFactor, digits, otpCode);
        return result.isValid() ? new Pair<>(true, result.getNewMovingFactor()) : new Pair(false, null);
    }

    public static boolean validateTOTPKey(OTPConfig conf, byte secretKey[], String otpCode) {
        TOTPBuilder builder = TOTP.key(secretKey).digits(conf.getDigits()).hmacSha(conf.getHmacShaAlgorithm());
        String localTotpKey = builder.timeStep(TimeUnit.SECONDS.toMillis(conf.getTimeStep())).build().value();
        return otpCode.equals(localTotpKey);
    }

    public static String getExternalHOTPUid(byte secretKey[], long factor){
        return String.format("hotp:%s;%s", BaseEncoding.base64Url().encode(secretKey), factor);
    }

    public static String getExternalTOTPUid(byte secretKey[]){
        return String.format("totp:%s", BaseEncoding.base64Url().encode(secretKey));
    }

}