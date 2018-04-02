/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.ui;

import org.zkoss.bind.BindContext;
import org.zkoss.bind.Converter;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;

import javax.servlet.ServletRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * This class is a ZK converter employed to display dates appropriately according to user location.
 * It uses the time offset with respect to UTC time and a formatting pattern supplied to show dates
 * @author jgomer
 */
public class CustomDateConverter implements Converter {

    /**
     * This method is called when conversion is taking placing in .zul templates
     * @param val An object representing a date, namely a java.util.Date or a long value (milliseconds from the epoch)
     * @param comp The UI component associated to this converstion
     * @param ctx Binding context. It holds the conversion arguments: "format" and "offset" are used here
     * @return A string with the date properly formatted or null if val parameter is not a valid date
     */
    public Object coerceToUi(Object	val, Component comp, BindContext ctx) {

        long timeStamp = (val instanceof Date) ? ((Date) val).getTime() : (long) val;
        if (timeStamp > 0) {
            Object offset = ctx.getConverterArg("offset");
            ZoneId zid;

            if (offset != null && ZoneId.class.isAssignableFrom(offset.getClass())) {
                zid = (ZoneId) offset;
            } else {
                zid = TimeZone.getDefault().toZoneId();
            }
            Instant instant = Instant.ofEpochMilli(timeStamp);
            OffsetDateTime odt = OffsetDateTime.ofInstant(instant, zid);

            ServletRequest request = (ServletRequest) Executions.getCurrent().getNativeRequest();
            Locale locale = request.getLocale() == null ? Locale.getDefault() : request.getLocale();
            return odt.format(DateTimeFormatter.ofPattern((String) ctx.getConverterArg("format"), locale));
        } else {
            return null;
        }
    }

    public Object coerceToBean(Object val, Component comp, BindContext ctx) {
        return null;
    }

}
