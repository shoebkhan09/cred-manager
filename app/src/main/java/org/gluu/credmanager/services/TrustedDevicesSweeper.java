/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.sndfactor.TrustedDevice;
import org.gluu.credmanager.conf.sndfactor.TrustedOrigin;
import org.gluu.credmanager.services.ldap.LdapService;
import org.gluu.credmanager.services.ldap.pojo.GluuPerson;
import org.quartz.JobExecutionContext;
import org.quartz.listeners.JobListenerSupport;
import org.xdi.util.security.StringEncrypter;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by jgomer on 2018-04-22.
 */
@ApplicationScoped
public class TrustedDevicesSweeper extends JobListenerSupport {

    private Logger logger = LogManager.getLogger(getClass());

    private String quartzJobName;
    private long locationExpiration;
    private long deviceExpiration;

    private ObjectMapper mapper;

    @Inject
    private TimerService timerService;

    @Inject
    private LdapService ldapService;

    @PostConstruct
    private void inited() {
        mapper = new ObjectMapper();
        quartzJobName = getClass().getSimpleName() + "_sweep";
    }

    public void activate(long locationExpiration, long deviceExpiration) {
logger.debug("stts {} {}",locationExpiration, deviceExpiration);
        this.locationExpiration=locationExpiration;
        this.deviceExpiration=deviceExpiration;
        try {
            int oneDay = (int) TimeUnit.DAYS.toSeconds(1);
            timerService.addListener(this, quartzJobName);
            //Start in one second and repeat indefinitely once every day
            timerService.schedule(quartzJobName, 1, -1, oneDay);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

    }

    @Override
    public String getName() {
        return quartzJobName;
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {

        logger.info("TrustedDevicesSweeper. Running timer");
        long now = System.currentTimeMillis();
        StringEncrypter stringEncrypter = ldapService.getStringEncrypter();
        List<GluuPerson> people = ldapService.getPeopleTrustedDevices();

        for (GluuPerson person : people) {
            try {
                String trustedDevicesInfo = person.getTrustedDevices();
                if (stringEncrypter != null)
                    trustedDevicesInfo = stringEncrypter.decrypt(trustedDevicesInfo);

                List<TrustedDevice> list = mapper.readValue(trustedDevicesInfo, new TypeReference<List<TrustedDevice>>() { });
                if (removeExpiredData(list, now)) {
                    //update list
                    String jsonStr = mapper.writeValueAsString(list);
                    String rdn = person.getDn();
                    rdn = rdn.substring(0, rdn.indexOf(","));

                    logger.trace("TrustedDevicesSweeper. Cleaning expired trusted devices for user '{}'", rdn);
                    ldapService.updateTrustedDevices(rdn, jsonStr);
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }

    }

    private boolean removeExpiredData(List<TrustedDevice> list, long time) throws Exception {

        boolean changed = false;

        for (int i = 0; i < list.size(); i++) {
            TrustedDevice device = list.get(i);
            List<TrustedOrigin> origins = device.getOrigins();

            if (origins != null) {
                long oldest = Long.MAX_VALUE;

                for (int j = 0; j < origins.size(); j++) {

                    TrustedOrigin origin = origins.get(j);
                    if (origin.getTimestamp() < oldest) {
                        oldest = origin.getTimestamp();
                    }
                    if (time - origin.getTimestamp() > locationExpiration) {
                        origins.remove(j);
                        j--;
                        changed = true;
                    }
                }

                if (time - oldest > deviceExpiration){
                    list.remove(i);
                    i--;
                    changed = true;
                }
            }
        }
        return changed;

    }

}
