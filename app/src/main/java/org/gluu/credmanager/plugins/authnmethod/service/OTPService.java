/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.plugins.authnmethod.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.io.BaseEncoding;
import com.lochbridge.oath.otp.HOTPValidationResult;
import com.lochbridge.oath.otp.HOTPValidator;
import com.lochbridge.oath.otp.TOTP;
import com.lochbridge.oath.otp.TOTPBuilder;
import com.lochbridge.oath.otp.keyprovisioning.OTPAuthURIBuilder;
import com.lochbridge.oath.otp.keyprovisioning.OTPKey;
import org.gluu.credmanager.core.ldap.gluuPersonOTP;
import org.gluu.credmanager.core.pojo.OTPDevice;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.plugins.authnmethod.OTPExtension;
import org.gluu.credmanager.plugins.authnmethod.conf.OTPConfig;
import org.slf4j.Logger;
import org.zkoss.util.Pair;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author jgomer
 * An app. scoped bean that encapsulates logic related to generating and validating OTP keys.
 * See https://tools.ietf.org/html/rfc6238 and https://tools.ietf.org/html/rfc4226.
 */
@ApplicationScoped
public class OTPService extends BaseService {

    @Inject
    private Logger logger;

    private OTPConfig conf;

    @PostConstruct
    private void inited() {
        reloadConfiguration();
    }

    public void reloadConfiguration() {

        Map<String, String> props = ldapService.getCustScriptConfigProperties(OTPExtension.ACR);
        if (props == null) {
            logger.warn("Config. properties for custom script '{}' could not be read. Features related to {} will not be accessible",
                    OTPExtension.ACR, OTPExtension.ACR.toUpperCase());
        } else {
            conf = OTPConfig.get(props);
        }

    }

    public int getDevicesTotal(String userId) {

        int total = 0;
        try {
            gluuPersonOTP person = ldapService.get(gluuPersonOTP.class, ldapService.getPersonDn(userId));
            total = (int) person.getExternalUidAsList().stream().filter(uid -> uid.startsWith("totp:") || uid.startsWith("hotp:")).count();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return total;

    }

    public List<OTPDevice> getOTPDevices(String userId) {

        List<OTPDevice> devices = new ArrayList<>();
        try {
            gluuPersonOTP person = ldapService.get(gluuPersonOTP.class, ldapService.getPersonDn(userId));
            String json = person.getOTPDevices();
            json = Utils.isEmpty(json) ? "[]" : mapper.readTree(json).get("devices").toString();

            List<OTPDevice> devs = mapper.readValue(json, new TypeReference<List<OTPDevice>>() { });
            devices = person.getExternalUidAsList().stream().filter(uid -> uid.startsWith("totp:") || uid.startsWith("hotp:"))
                    .map(uid -> getExtraOTPInfo(uid, devs)).sorted().collect(Collectors.toList());
            logger.trace("getOTPDevices. User '{}' has {}", userId, devices.stream().map(OTPDevice::getId).collect(Collectors.toList()));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return devices;

    }

    public boolean updateDevicesAdd(String userId, List<OTPDevice> devices, OTPDevice newDevice) {

        boolean success = false;
        try {
            List<OTPDevice> vdevices = new ArrayList<>(devices);
            if (newDevice != null) {
                vdevices.add(newDevice);
            }
            String[] uids = vdevices.stream().map(OTPDevice::getUid).toArray(String[]::new);
            String json = uids.length == 0 ? null : mapper.writeValueAsString(Collections.singletonMap("devices", vdevices));

            logger.debug("Updating otp devicces for user '{}'", userId);
            gluuPersonOTP person = ldapService.get(gluuPersonOTP.class, userId);
            person.setOTPDevices(json);
            person.setExternalUid(uids);

            success = ldapService.modify(person, gluuPersonOTP.class);

            if (success && newDevice != null) {
                devices.add(newDevice);
                logger.debug("Added {}", newDevice.getNickName());
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return success;

    }

    public boolean addDevice(String userId, OTPDevice newDevice) {
        return updateDevicesAdd(userId, getOTPDevices(userId), newDevice);
    }

    public byte[] generateSecretKey() {

        int keyLen = conf.getKeyLength();
        byte[] bytes = new byte[keyLen];
        SecureRandom random = new SecureRandom();
        random.nextBytes(bytes);
        return bytes;

    }

    public String generateSecretKeyUri(byte[] secretKey, String displayName) {

        String secretKeyBase32 = BaseEncoding.base32().omitPadding().encode(secretKey);
        OTPKey otpKey = new OTPKey(secretKeyBase32, conf.getType());

        OTPAuthURIBuilder uribe = OTPAuthURIBuilder.fromKey(otpKey).label(displayName);
        uribe = uribe.issuer(conf.getIssuer()).digits(conf.getDigits());
        if (conf.getType().equals(OTPKey.OTPType.TOTP)) {
            uribe = uribe.timeStep(TimeUnit.SECONDS.toMillis(conf.getTimeStep()));
        }

        return uribe.build().toUriString();

    }

    public String validateKey(byte[] secretKey, String code) {

        String uid = null;
        if (code != null) {
            //Determines if numeric code is valid with respect to QR's secret key
            switch (conf.getType()) {
                case HOTP:
                    Pair<Boolean, Long> result = validateHOTPKey(secretKey, 1, code);
                    if (result.getX()) {
                        uid = getExternalHOTPUid(secretKey, result.getY());
                    }
                    break;
                case TOTP:
                    if (validateTOTPKey(secretKey, code)) {
                        uid = getExternalTOTPUid(secretKey);
                    }
                    break;
            }
        }
        return uid;

    }

    /**
     * Creates an instance of OTPDevice by looking up in the list of OTPDevices passed. If the item is not found in the
     * in the list, it means the device was previously enrolled by using a different application. In this case the resulting
     * object will not have properties like nickname, etc. Just a basic ID
     * @param uid Identifier of an OTP device (LDAP attribute "oxExternalUid" inside a user entry)
     * @param list List of existing OTP devices enrolled. Ideally, there is an item here corresponding to the uid passed
     * @return OTPDevice object
     */
    private OTPDevice getExtraOTPInfo(String uid, List<OTPDevice> list) {
        //Complements current otp device with extra info in the list if any

        OTPDevice device = new OTPDevice(uid);
        int hash = device.getId();

        Optional<OTPDevice> extraInfoOTP = list.stream().filter(dev -> dev.getId() == hash).findFirst();
        if (extraInfoOTP.isPresent()) {
            device.setAddedOn(extraInfoOTP.get().getAddedOn());
            device.setNickName(extraInfoOTP.get().getNickName());
        }
        return device;

    }

    private Pair<Boolean, Long> validateHOTPKey(byte[] secretKey, int movingFactor, String otpCode) {
        int digits = conf.getDigits();
        HOTPValidationResult result = HOTPValidator.lookAheadWindow(conf.getLookAheadWindow()).validate(secretKey, movingFactor, digits, otpCode);
        return result.isValid() ? new Pair<>(true, result.getNewMovingFactor()) : new Pair<>(false, null);
    }

    private boolean validateTOTPKey(byte[] secretKey, String otpCode) {
        TOTPBuilder builder = TOTP.key(secretKey).digits(conf.getDigits()).hmacSha(conf.getHmacShaAlgorithm());
        String localTotpKey = builder.timeStep(TimeUnit.SECONDS.toMillis(conf.getTimeStep())).build().value();
        return otpCode.equals(localTotpKey);
    }

    private String getExternalHOTPUid(byte[] secretKey, long factor) {
        return String.format("%s:%s;%s", OTPKey.OTPType.HOTP.getName().toLowerCase(), BaseEncoding.base64Url().encode(secretKey), factor);
    }

    private String getExternalTOTPUid(byte[] secretKey) {
        return String.format("%s:%s", OTPKey.OTPType.TOTP.getName().toLowerCase(), BaseEncoding.base64Url().encode(secretKey));
    }

}
