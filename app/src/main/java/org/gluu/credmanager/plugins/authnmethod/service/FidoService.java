/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.plugins.authnmethod.service;

import org.gluu.credmanager.conf.MainSettings;
import org.gluu.credmanager.core.ldap.oxDeviceRegistration;
import org.gluu.credmanager.core.pojo.FidoDevice;
import org.gluu.credmanager.core.pojo.SuperGluuDevice;
import org.slf4j.Logger;
import org.xdi.oxauth.model.fido.u2f.DeviceRegistrationStatus;
import org.zkoss.util.Pair;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jgomer
 */
public class FidoService extends BaseService {

    @Inject
    private Logger logger;

    @Inject
    MainSettings settings;

    public boolean updateDevice(FidoDevice device) {

        boolean success = false;
        oxDeviceRegistration deviceRegistration = getDeviceRegistrationFor(device);
        if (deviceRegistration != null) {
            deviceRegistration.setDisplayName(device.getNickName());
            success = ldapService.modify(deviceRegistration, oxDeviceRegistration.class);
        }
        return success;

    }

    public boolean removeDevice(FidoDevice device) {

        boolean success = false;
        oxDeviceRegistration deviceRegistration = getDeviceRegistrationFor(device);
        if (deviceRegistration != null) {
            success = ldapService.delete(deviceRegistration, oxDeviceRegistration.class);
        }
        return success;

    }

    public int getDevicesTotal(String appId, String userId, boolean active) {

        int total = 0;
        try {
            total = getRegistrations(appId, userId, active).size();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return total;

    }

    List<oxDeviceRegistration> getRegistrations(String appId, String userId, boolean active) {

        String parentDn = String.format("ou=fido,%s", ldapService.getPersonDn(userId));

        oxDeviceRegistration deviceRegistration = new oxDeviceRegistration();
        deviceRegistration.setOxApplication(appId);
        deviceRegistration.setOxStatus(active ? DeviceRegistrationStatus.ACTIVE.getValue() : DeviceRegistrationStatus.COMPROMISED.getValue());

        return ldapService.find(deviceRegistration, oxDeviceRegistration.class, parentDn);

    }

    public <T extends FidoDevice> T getFidoDevice(String userId, long time, String oxApp, Class<T> clazz) throws Exception {
        List<T> list = getU2FDevices(userId, oxApp, clazz);
        logger.debug("getFidoDevice. list is {}", list.stream().map(FidoDevice::getId).collect(Collectors.toList()).toString());
        return getRecentlyCreatedDevice(list, time);
    }

    private oxDeviceRegistration getDeviceRegistrationFor(FidoDevice device) {

        //TODO: will it work with people dn?
        //String parentDn = String.format("ou=fido,%s", ldapService.getPersonDn(userId));
        String id = device.getId();
        oxDeviceRegistration deviceRegistration = new oxDeviceRegistration();
        deviceRegistration.setOxId(device.getId());
        List<oxDeviceRegistration> list = ldapService.find(deviceRegistration, oxDeviceRegistration.class, ldapService.getPeopleDn());
        if (list.size() == 1) {
            return list.get(0);
        } else {
            logger.warn("Search for fido device rgistration with oxId {} returned {} results!", id, list.size());
            return null;
        }

    }
    /**
     * Returns a list of FidoDevice instances found under the given branch that matches the oxApplication value given and
     * whose oxStatus attribute equals to "active"
     * @param userId
     * @param oxApplication Value to match for oxApplication attribute (see LDAP object class oxDeviceRegistration)
     * @param clazz Any subclass of FidoDevice
     * @param <T>
     * @return List of FidoDevices
     */
    private <T extends FidoDevice> List<T> getU2FDevices(String userId, String oxApplication, Class<T> clazz) throws Exception {

        List<T> devices = new ArrayList<>();
        List<oxDeviceRegistration> list = getRegistrations(oxApplication, userId, true);

        for (oxDeviceRegistration deviceRegistration : list) {
            T device = clazz.getConstructor().newInstance();

            if (clazz.equals(SuperGluuDevice.class)) {
                ((SuperGluuDevice) device).setDeviceData(deviceRegistration.getDeviceData());
            }
            device.setApplication(deviceRegistration.getOxApplication());
            device.setNickName(deviceRegistration.getDisplayName());
            device.setStatus(deviceRegistration.getOxStatus());
            device.setId(deviceRegistration.getOxId());
            device.setCreationDate(deviceRegistration.getCreationDate());
            device.setCounter(deviceRegistration.getOxCounter());

            devices.add(device);
        }
        return devices;

    }
    /**
     * Chooses one device from a list of devices, such that its creation time is the closest to the timestamp given
     * @param devices A non-null list of fido devices
     * @param time A timestamp as milliseconds elapsed from the "epoch"
     * @param <T>
     * @return The best matching device (only devices added before the time supplied are considered). Null if no suitable
     * device could be found
     */
    private <T extends FidoDevice> T getRecentlyCreatedDevice(List<T> devices, long time) {

        long diffs[] = devices.stream().mapToLong(key -> time - key.getCreationDate().getTime()).toArray();

        logger.trace("getRecentlyCreatedDevice. diffs {}", Arrays.asList(diffs));
        //Search for the smallest time difference
        int i;
        Pair<Long, Integer> min = new Pair<>(Long.MAX_VALUE, -1);
        //it always holds that diffs.length==devices.size()
        for (i = 0; i < diffs.length; i++) {
            if (diffs[i] >= 0 && min.getX() > diffs[i]) {  //Only search non-negative differences
                min = new Pair<>(diffs[i], i);
            }
        }

        i = min.getY();
        return i == -1 ? null : devices.get(i);

    }

}
