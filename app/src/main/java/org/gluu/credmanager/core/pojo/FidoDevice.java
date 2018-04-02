package org.gluu.credmanager.core.pojo;

import java.util.Date;

/**
 * Represents a registered credential of u2f type.
 * @author jgomer
 */
public class FidoDevice extends RegisteredCredential implements Comparable<FidoDevice> {

    private String id;
    private long counter;
    private Date creationDate;
    private String status;
    private String application;
    private String deviceData;

    public String getId() {
        return id;
    }

    public long getCounter() {
        return counter;
    }

    public String getApplication() {
        return application;
    }

    public String getStatus() {
        return status;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public String getDeviceData() {
        return deviceData;
    }

    public void setDeviceData(String deviceData) {
        this.deviceData = deviceData;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public void setCounter(long counter) {
        this.counter = counter;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int compareTo(FidoDevice k) {
        long date1 = getCreationDate().getTime();
        long date2 = k.getCreationDate().getTime();
        return (date1 < date2) ? -1 : ((date1 > date2) ? 1 : 0);
    }

}
