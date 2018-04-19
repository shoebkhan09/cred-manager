/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.conf;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by jgomer on 2017-09-06.
 */
public class QRConfig {

    private Logger logger = LogManager.getLogger(getClass());

    static ObjectMapper mapper=new ObjectMapper();

    private String label;
    private String registrationUri;

    private int qrSize;
    private double qrMSize;

    public void populate(Map<String, String> properties) throws Exception{

        setLabel(properties.get("label"));
        setRegistrationUri(properties.get("registration_uri"));

        String value=properties.get("qr_options");
        //value may come not properly formated (without quotes, for instance...)
        if (!value.contains("\""))
            value = value.replaceFirst("mSize", "\"mSize\"").replaceFirst("size", "\"size\"");

        JsonNode tree = mapper.readTree(value);

        if (tree.get("size") != null)
            setQrSize(tree.get("size").asInt());

        if (tree.get("mSize") != null)
            setQrMSize(tree.get("mSize").asDouble());

    }

    /**
     * Creates a string for a Json representation of two values: size and mSize for QR code
     * @return Json String
     */
    public String getFormattedQROptions(int browserWidth){

        List<String> list=new ArrayList<>();
        int ival=Math.min(getQrSize(), browserWidth-40);

        if (ival>0)
            list.add("size:" + ival);

        double dval=getQrMSize();
        if (dval>0)
            list.add("mSize: " + dval);

        return list.toString().replaceFirst("\\[","{").replaceFirst("\\]","}");

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
