package org.gluu.credmanager.services.ldap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.jsonized.LdapSettings;
import org.gluu.credmanager.services.ldap.pojo.GluuOrganization;
import org.gluu.credmanager.services.ldap.pojo.GluuPerson;
import org.gluu.credmanager.services.ldap.pojo.OxAuthConfiguration;
import org.gluu.credmanager.services.ldap.pojo.OxTrustConfiguration;
import org.gluu.site.ldap.LDAPConnectionProvider;
import org.gluu.site.ldap.OperationsFacade;
import org.gluu.site.ldap.persistence.LdapEntryManager;
import org.xdi.util.properties.FileConfiguration;
import org.xdi.util.security.PropertiesDecrypter;
import org.xdi.util.security.StringEncrypter;
import org.zkoss.util.resource.Labels;

import javax.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.List;

/**
 * Created by jgomer on 2017-07-06.
 */
@ApplicationScoped
public class LdapService {

    private Logger logger = LogManager.getLogger(getClass());

    private Properties ldapProperties;
    private LdapEntryManager ldapEntryManager;
    private LdapSettings ldapSettings;
    private ObjectMapper mapper;

    private String strOxTrustConfig =null;
    private String strOxAuthConfig =null;

    public void setup(LdapSettings settings) throws Exception{

        mapper = new ObjectMapper();
        ldapSettings=settings;

        String saltFile=ldapSettings.getSaltLocation();
        ldapProperties = new FileConfiguration(ldapSettings.getOxLdapLocation()).getProperties();
        if (saltFile != null){
            String salt = new FileConfiguration(saltFile).getProperties().getProperty("encodeSalt");
            ldapProperties = PropertiesDecrypter.decryptProperties(StringEncrypter.instance(salt), ldapProperties);
        }
        LDAPConnectionProvider connProvider = new LDAPConnectionProvider(ldapProperties);

        OperationsFacade facade = new OperationsFacade(connProvider);   //bindConnProvider??
        ldapEntryManager= new LdapEntryManager(facade);

        if (ldapEntryManager==null)
            throw new Exception(Labels.getLabel("app.bad_ldapentrymanager"));
    }

    //TODO: refactor these methods to call .find(class,dn) - search in oxauth proper class with the suitable annotations
    public String getOrganizationName() throws Exception{

        String dn=String.format("o=%s,o=gluu", ldapSettings.getOrgInum());
        List<GluuOrganization> list=ldapEntryManager.findEntries(dn, GluuOrganization.class,null);
        return list.get(0).getName();

    }

    private String getStrOxTrustConfig(){

        if (strOxTrustConfig ==null){
            String dn=String.format("ou=oxtrust,ou=configuration,inum=%s,ou=appliances,o=gluu", ldapSettings.getApplianceInum());
            List<OxTrustConfiguration> list=ldapEntryManager.findEntries(dn, OxTrustConfiguration.class,null);
            strOxTrustConfig =list.get(0).getStrConfCacheRefresh();
        }
        return strOxTrustConfig;
    }

    private String getStrOxAuthConfig(){

        if (strOxAuthConfig ==null){
            String dn=String.format("ou=oxauth,ou=configuration,inum=%s,ou=appliances,o=gluu", ldapSettings.getApplianceInum());
            List<OxAuthConfiguration> list=ldapEntryManager.findEntries(dn, OxAuthConfiguration.class,null);
            strOxAuthConfig =list.get(0).getStrConfDynamic();
        }
        return strOxAuthConfig;
    }

    public GluuPerson getGluuPerson(String inum) throws Exception{
        String dn=String.format("inum=%s,ou=people,o=%s,o=gluu", inum, ldapSettings.getOrgInum());
        return ldapEntryManager.find(GluuPerson.class, dn);
    }

    public boolean isBackendLdapEnabled() throws Exception{

        JsonNode tree=mapper.readTree(getStrOxTrustConfig());

        List<Boolean> enabledList=new ArrayList<>();
        tree.get("sourceConfigs").forEach(node -> enabledList.add(node.get("enabled").asBoolean()));
        return enabledList.stream().anyMatch(item -> item);

    }

    public String getOIDCEndpoint() throws Exception{

        JsonNode tree=mapper.readTree(getStrOxAuthConfig());
        return tree.get("openIdConfigurationEndpoint").asText();

    }


    //String here=ldapService.getEntryValue("ou=oxauth,ou=configuration,inum=@!3245.DF39.6A34.9E97!0002!CFBE.8F9E,ou=appliances,o=gluu");
    //logger.info("HERE: "+ here);
    public String getEntryValue(String dn){
        return Arrays.asList(ldapEntryManager.getLDIF(dn)).toString();
    }

}
