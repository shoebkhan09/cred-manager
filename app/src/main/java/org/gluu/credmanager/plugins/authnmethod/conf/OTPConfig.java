package org.gluu.credmanager.plugins.authnmethod.conf;

import com.fasterxml.jackson.databind.JsonNode;
import com.lochbridge.oath.otp.HmacShaAlgorithm;
import com.lochbridge.oath.otp.keyprovisioning.OTPKey;
import org.gluu.credmanager.misc.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;

/**
 * POJO storing values needed for HOTP/TOTP. Static methods of this class parse information belonging to the OTP custom
 * script to be able to get an instance of this class
 * @author jgomer
 */
public class OTPConfig extends QRConfig {

    private static Logger LOGGER = LoggerFactory.getLogger(OTPConfig.class);

    private OTPKey.OTPType type;
    private int keyLength;
    private int digits;
    private int lookAheadWindow;
    private int timeStep;

    private String issuer;

    private HmacShaAlgorithm hmacShaAlgorithm;

    public boolean isValidConfig() {
        boolean valid = false;

        switch (type) {
            case HOTP:
                valid = lookAheadWindow > 0;
                break;
            case TOTP:
                valid = timeStep > 0 && hmacShaAlgorithm != null;
                break;
        }
        return valid && keyLength > 0 && digits > 0;
    }
    public OTPKey.OTPType getType() {
        return type;
    }

    public void setType(OTPKey.OTPType type) {
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

    /**
     * Creates an OTPConfig object to hold all properties required for OTP key generation and QR code display
     * @param propsMap
     * @return null if an error or inconsistency is found while inspecting the configuration properties of the custom script.
     * Otherwise returns an OTPConfig object
     */
    public static OTPConfig get(Map<String, String> propsMap) {

        OTPConfig config;
        try {
            OTPConfig cfg = new OTPConfig();
            cfg.populate(propsMap);
            cfg.setType(OTPKey.OTPType.valueOf(propsMap.get("otp_type").toUpperCase()));
            cfg.setIssuer(propsMap.get("issuer"));

            //Do not change evaluation order of these 2 predicates
            if (readFileSettings(cfg, propsMap) && cfg.isValidConfig()) {
                config = cfg;
                LOGGER.info("OTP settings found were: {}", mapper.writeValueAsString(cfg));
            } else {
                config = null;
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            config = null;
        }
        return config;

    }

    private static boolean readFileSettings(OTPConfig cfg, Map<String, String> properties) {

        boolean invalid = false;
        try {
            //Find the property of the script related to json file
            String jsonFile = properties.get("otp_conf_file");
            if (Utils.isNotEmpty(jsonFile)) {

                File f = new File(jsonFile);

                if (f.exists()) {
                    JsonNode tree = mapper.readTree(f);
                    JsonNode subNode;

                    switch (cfg.getType()) {
                        case HOTP:
                            subNode = tree.get("htop");  //This is a common mistake in Gluu's opt_configuration.json
                            if (subNode == null) {
                                subNode = tree.get("hotp");
                            }

                            if (subNode == null) {
                                invalid = true;
                            } else {
                                //Read data as if it were HOTP
                                cfg.setKeyLength(subNode.get("keyLength").asInt());
                                cfg.setDigits(subNode.get("digits").asInt());
                                cfg.setLookAheadWindow(subNode.get("lookAheadWindow").asInt());
                            }
                            break;
                        case TOTP:
                            subNode = tree.get("totp");

                            if (subNode == null) {
                                invalid = true;
                            } else {
                                //Read data as if it were TOTP
                                cfg.setKeyLength(subNode.get("keyLength").asInt());
                                cfg.setDigits(subNode.get("digits").asInt());
                                cfg.setTimeStep(subNode.get("timeStep").asInt());

                                String algo = "Hmac" + subNode.get("hmacShaAlgorithm").asText().toUpperCase();
                                cfg.setHmacShaAlgorithm(HmacShaAlgorithm.from(algo));
                            }
                            break;
                        default:
                            invalid = true;
                    }
                } else {
                    throw new FileNotFoundException();
                }
            } else {
                invalid = true;
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            invalid = true;
        }
        return !invalid;

    }

}
