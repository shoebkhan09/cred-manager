/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.AppConfiguration;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.WebUtils;
import org.gluu.credmanager.core.credential.SuperGluuDevice;
import org.gluu.credmanager.services.ldap.LdapService;
import org.zkoss.util.resource.Labels;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Created by jgomer on 2017-09-06.
 * An app. scoped bean that encapsulates logic needed to enroll supergluu devices
 */
@ApplicationScoped
public class SGService {

    @Inject
    AppConfiguration appConfig;

    @Inject
    LdapService ldapService;

    private static final String GEOLOCATION_URL_PATTERN="http://ip-api.com/json/{0}?fields=49177";
    private static final int GEO_REQ_TIMEOUT=5000;  //wait 5 secs at most

    private Logger logger= LogManager.getLogger(getClass());
    private ObjectMapper mapper = new ObjectMapper();

    public SGService(){}

    /**
     * Executes a geolocation call the to ip-api.com service
     * @param ip String representing an IP address
     * @return A JsonNode with the respone. Null if there was an error issuing or parsing the contents
     */
    public JsonNode getGeoLocation(String ip){

        JsonNode node=null;
        try{
            String ipApiResponse= WebUtils.getUrlContents(MessageFormat.format(GEOLOCATION_URL_PATTERN, ip), GEO_REQ_TIMEOUT);
            logger.debug(Labels.getLabel("app.ip-api_response"), ipApiResponse);
            if (ipApiResponse!=null){
                node=mapper.readTree(ipApiResponse);
                if (!node.get("status").asText().equals("success"))
                    node=null;
            }
        }
        catch (Exception e){
            node=null;
            logger.info(Labels.getLabel("app.remote_location_error"), e.getMessage());
            logger.error(e.getMessage(), e);
        }
        return node;
    }

    /**
     * Builds a string that encodes information in order to display a QR code
     * @param userName Username string
     * @param code An enrollment code associated to the code
     * @param remoteIp An IP address to encode in the request (possibly null)
     * @return A string encoded in JSon format with the information for QR code display
     */
    public String generateRequest(String userName, String code, String remoteIp){

        logger.info(Labels.getLabel("app.start_registration_request"), userName, remoteIp);

        Map<String, String> reqAsMap=new HashMap<>();
        reqAsMap.put("username", userName);
        reqAsMap.put("app", appConfig.getConfigSettings().getSgConfig().getRegistrationUri());
        reqAsMap.put("issuer", appConfig.getIssuerUrl());
        reqAsMap.put("created", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        reqAsMap.put("enrollment", code);
        reqAsMap.put("method", "enroll");

        if (remoteIp!=null) {   //Add  geolocation information only if we have an IP available
            reqAsMap.put("req_ip", remoteIp);

            JsonNode geolocation = getGeoLocation(remoteIp);
            if (geolocation != null) {
                Stream<String> stream = Arrays.asList("country", "regionName", "city").stream();
                String req_location = stream.map(key -> geolocation.get(key).asText()).reduce("", (acc, next) -> acc + ", " + next);
                reqAsMap.put("req_loc", req_location.substring(2)); //Drop space+comma at the beginning
            }
        }

        String request=null;
        try {
            request = mapper.writeValueAsString(reqAsMap);
            logger.debug(Labels.getLabel("app.sg_request"), request);
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        return request;

    }

    /**
     * Returns the most recently added (with respect to the timestamp passed) Super Gluu device for the user in question
     * @param user An User object
     * @param time Timestamp (milliseconds from the "epoch")
     * @return A SuperGluuDevice object or null if no device could be found. Device has to have counter=-1 and no displayName yet
     */
    public SuperGluuDevice getLatestSuperGluuDevice(User user, long time){

        SuperGluuDevice sg=null;
        try {
            String appId = appConfig.getConfigSettings().getSgConfig().getRegistrationUri();
            sg = ldapService.getFidoDevice(user.getRdn(), time, appId, SuperGluuDevice.class);

            logger.debug("getLatestSuperGluuDevice. sg id is {}", sg==null ? -1 : sg.getId());
            if (sg!=null && (sg.getNickName()!=null || sg.getCounter()>=0))
                sg=null;    //should have no name and counter must be -1
            logger.debug("getLatestSuperGluuDevice. sg is null {}", sg==null);
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        return sg;

    }

    /**
     * Determines if the device passed is enrolled exactly once or more times
     * @param dev A SuperGluuDevice instance
     * @return Boolean value indicating whether a device with this device's UUID is enrolled once for some user
     * @throws Exception If the device is not even enrolled
     */
    public boolean isSGDeviceUnique(SuperGluuDevice dev) throws Exception{

        boolean unique=false;
        String uiid=dev.getDeviceData().getUuid();
        List<String> uuids=ldapService.getSGDevicesIDs(appConfig.getConfigSettings().getSgConfig().getRegistrationUri());

        logger.trace("isSGDeviceUnique. All SG devices {}", uuids.toString());
        int size=(int) uuids.stream().filter(uuid -> uuid.equals(uiid)).count();
        if (size==0)
            throw new Exception(Labels.getLabel("app.error_uniqueness", new String[]{uiid}));
        else
        if (size==1)
            unique=true;

        return unique;

    }

}
