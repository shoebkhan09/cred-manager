package org.gluu.credmanager.conf;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ldap.pojo.CustomScript;
import org.xdi.model.SimpleCustomProperty;
import org.zkoss.util.resource.Labels;

import java.util.List;
import java.util.Map;

/**
 * Created by jgomer on 2017-09-06.
 * POJO storing values needed for Supergluu. Static method of this class parse information belonging to the corresponding
 * custom script to be able to get an instance of this class.
 * Only the basic properties required for enrolling are parsed, so there is no need to inspect super_gluu_creds.json
 */
public class SGConfig extends QRConfig {

    private static Logger logger = LogManager.getLogger(SGConfig.class);

    /**
     * Creates an SGConfig object to hold all properties required for SuperGluu operation
     * @param sgScript Represents the LDAP entry corresponding to the SuperGluu custom script
     * @return null if an error or inconsistency is found while inspecting the configuration properties of the custom script.
     * Otherwise returns a SGConfig object
     */
    public static SGConfig get(CustomScript sgScript) {

        SGConfig cfg=new SGConfig();
        try{
            List<SimpleCustomProperty> properties=sgScript.getProperties();
            Map<String, String> propsMap=Utils.getScriptProperties(properties);
            cfg.populate(propsMap);

            logger.info(Labels.getLabel("app.sg_settings"), mapper.writeValueAsString(cfg));
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            cfg=null;
        }
        return cfg;

    }

}
