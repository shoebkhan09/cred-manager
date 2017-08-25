package org.gluu.credmanager.ui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.core.WebUtils;
import org.zkoss.bind.BindContext;
import org.zkoss.bind.Converter;
import org.zkoss.zk.ui.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

/**
 * Created by jgomer on 2017-08-05.
 * This class is a ZK converter employed to display dates appropriately according to user location.
 * It uses the time offset with respect to UTC time and a formatting pattern supplied to show dates
 */
public class CustomDateConverter implements Converter {

    private Logger logger= LogManager.getLogger(getClass());

    /**
     * This method is called when conversion is taking placing in .zul templates
     * @param val An object representing a date, namely a java.util.Date or a long value (milliseconds from the epoch)
     * @param comp The UI component associated to this converstion
     * @param ctx Binding context. It holds the conversion arguments. Only "format" is used here
     * @return A string with the date properly formatted or null if val parameter is not a valid date
     */
    public Object coerceToUi(Object	val, Component comp, BindContext ctx){

        long timeStamp=(val instanceof Date) ? ((Date) val).getTime() : (long) val;
        if (timeStamp>0) {
            ZoneId zid = WebUtils.getUserOffset(comp.getDesktop().getSession());
            Instant instant = Instant.ofEpochMilli(timeStamp);

            OffsetDateTime odt = OffsetDateTime.ofInstant(instant, zid);
            return odt.format(DateTimeFormatter.ofPattern((String) ctx.getConverterArg("format"), Locale.US));
        }
        else
            return null;
    }

    public Object coerceToBean(Object val, Component comp, BindContext ctx){
        return null;
    }

}