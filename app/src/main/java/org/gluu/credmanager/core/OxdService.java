/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import org.apache.commons.lang.StringUtils;
import org.gluu.credmanager.conf.MainSettings;
import org.gluu.credmanager.conf.OxdClientSettings;
import org.gluu.credmanager.conf.OxdSettings;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.service.LdapService;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.slf4j.Logger;
import org.xdi.oxd.client.CommandClient;
import org.xdi.oxd.common.Command;
import org.xdi.oxd.common.CommandResponse;
import org.xdi.oxd.common.CommandType;
import org.xdi.oxd.common.ResponseStatus;
import org.xdi.oxd.common.params.GetAuthorizationUrlParams;
import org.xdi.oxd.common.params.GetClientTokenParams;
import org.xdi.oxd.common.params.GetLogoutUrlParams;
import org.xdi.oxd.common.params.GetTokensByCodeParams;
import org.xdi.oxd.common.params.GetUserInfoParams;
import org.xdi.oxd.common.params.IParams;
import org.xdi.oxd.common.params.RegisterSiteParams;
import org.xdi.oxd.common.params.RemoveSiteParams;
import org.xdi.oxd.common.params.SetupClientParams;
import org.xdi.oxd.common.params.UpdateSiteParams;
import org.xdi.oxd.common.response.*;
import org.zkoss.util.Pair;

import java.util.*;

import static org.gluu.credmanager.core.ConfigurationHandler.DEFAULT_ACR;

/**
 * An app. scoped bean that encapsulates interactions with an oxd-server. Contains methods depicting the steps of the
 * authorization code flow of OpenId Connect spec
 * @author jgomer
 */
@Named
@ApplicationScoped
public class OxdService {

    private static final String LOGOUT_PAGE_URL = "bye.zul";

    @Inject
    private Logger logger;

    @Inject
    private MainSettings settings;

    @Inject
    private LdapService ldapService;

    private int consecutive = 0;  //This is used only to debug and test
    private OxdSettings config;
    private ResteasyClient client;
    private ObjectMapper mapper;

    @PostConstruct
    public void inited() {
        mapper =  new ObjectMapper();
        client = new ResteasyClientBuilder().build();
    }

    public boolean initialize() {

        boolean success = false;

        OxdSettings oxdConfig = settings.getOxdSettings();
        if (oxdConfig == null) {
            logger.error("No oxd configuration was provided");
        } else {
            String issuerUrl = ldapService.getIssuerUrl();
            String oxdHost = oxdConfig.getHost();
            String oxdRedirectUri = oxdConfig.getRedirectUri();

            if (oxdConfig.getPort() <= 0 || Utils.isEmpty(oxdHost) || Utils.isEmpty(oxdRedirectUri)) {
                logger.error("Host, port, and URI for redirect must be present in configuration file");
            } else {

                String tmp = oxdConfig.getPostLogoutUri();
                if (Utils.isEmpty(tmp)) {   //Use default post logout if not in config settings
                    tmp = oxdRedirectUri;    //Remove trailing slash if any in redirect URI
                    tmp = tmp.endsWith("/") ? tmp.substring(0, tmp.length() - 1) : tmp;
                    oxdConfig.setPostLogoutUri(tmp + "/" + LOGOUT_PAGE_URL);
                }
                //TODO: bug 3.1.1?  https://github.com/GluuFederation/oxd/issues/124
                oxdConfig.setOpHost(issuerUrl);
                //END
                oxdConfig.setAcrValues(Collections.singletonList(DEFAULT_ACR));

                try {
                    if (ldapService.getDynamicClientExpirationTime() > 0) {
                        Optional<String> oxdIdOpt = Optional.ofNullable(oxdConfig.getClient()).map(OxdClientSettings::getOxdId);
                        if (oxdIdOpt.isPresent()) {
                            setSettings(oxdConfig);

                            if (!extendSiteLifeTime()) {
                                logger.warn("An error occured while extending the lifetime of the associated oxd client.");
                                logger.info("Attempting a new site registration");
                                setSettings(oxdConfig, true);
                            }
                        } else {
                            //trigger registration
                            setSettings(oxdConfig, true);

                            if (!extendSiteLifeTime()) {
                                logger.warn("An error occured while extending the lifetime of the associated oxd client.");
                            }
                        }
                        settings.save();
                        success = true;

                    } else {
                        logger.error("Dynamic registration of OpenId Connect clients must be enabled in the server. "
                                + "Check expiration time is greater than zero");
                    }
                } catch (Exception e) {
                    logger.warn("Users will not be able to login until a new sucessful attempt to refresh oxd-associated "
                            + "clients takes place. Restart the app to trigger the update immediately");
                }
            }
        }
        return success;

    }

    public void setSettings(OxdSettings config) throws Exception {
        setSettings(config, false);
    }

    public void setSettings(OxdSettings config, boolean triggerRegistration) throws Exception {
        this.config = config;
        if (triggerRegistration) {
            config.setClient(doRegister());
        }
    }

    public OxdClientSettings doRegister() throws Exception {

        OxdClientSettings computedSettings;
        String clientName;
        logger.info("Setting oxd configs (host: {}, port: {}, https extension: {}, post logout: {})",
                config.getHost(), config.getPort(), config.isUseHttpsExtension(), config.getPostLogoutUri());

        try {
            if (config.isUseHttpsExtension()) {
                clientName = "cred-manager-extension-" + consecutive;

                SetupClientParams cmdParams = new SetupClientParams();
                cmdParams.setOpHost(config.getOpHost());
                cmdParams.setAuthorizationRedirectUri(config.getRedirectUri());
                cmdParams.setPostLogoutRedirectUri(config.getPostLogoutUri());
                cmdParams.setAcrValues(config.getAcrValues());
                cmdParams.setClientName(clientName);

                //TODO: bug 3.1.1?
                List<String> scopes = new ArrayList<>(Arrays.asList(UserService.OPEN_ID_SCOPES));
                scopes.add("uma_protection");
                cmdParams.setScope(scopes);
                //END

                cmdParams.setResponseTypes(Collections.singletonList("code"));
                cmdParams.setTrustedClient(true);
                //cmdParams.setGrantType(Collections.singletonList("authorization_code"));      //this is the default grant

                SetupClientResponse setup = restResponse(cmdParams, "setup-client", null, SetupClientResponse.class);
                computedSettings = new OxdClientSettings(clientName, setup.getOxdId(), setup.getClientId(), setup.getClientSecret());
            } else {
                clientName = "cred-manager-" + consecutive;

                RegisterSiteParams cmdParams = new RegisterSiteParams();
                cmdParams.setOpHost(config.getOpHost());
                cmdParams.setAuthorizationRedirectUri(config.getRedirectUri());
                cmdParams.setPostLogoutRedirectUri(config.getPostLogoutUri());
                cmdParams.setAcrValues(config.getAcrValues());
                cmdParams.setClientName(clientName);

                //These scopes should be set to default=true in LDAP (or using oxTrust). Otherwise the following will have no effect
                cmdParams.setScope(Arrays.asList(UserService.OPEN_ID_SCOPES));

                cmdParams.setResponseTypes(Collections.singletonList("code"));  //Use "token","id_token" for implicit flow
                cmdParams.setTrustedClient(true);
                //cmdParams.setGrantType(Collections.singletonList("authorization_code"));      //this is the default grant

                CommandClient commandClient = null;
                try {
                    commandClient = new CommandClient(config.getHost(), config.getPort());
                    Command command = new Command(CommandType.REGISTER_SITE).setParamsObject(cmdParams);
                    RegisterSiteResponse site = commandClient.send(command).dataAsResponse(RegisterSiteResponse.class);
                    computedSettings = new OxdClientSettings(clientName, site.getOxdId(), null, null);
                } finally {
                    CommandClient.closeQuietly(commandClient);
                }
            }
            consecutive++;
            logger.info("oxd client registered successfully, oxd-id={}", computedSettings.getOxdId());
        } catch (Exception e) {
            consecutive++;
            String msg = "Setting oxd-server configs failed";
            logger.error(msg, e);
            throw new Exception(msg, e);
        }
        return computedSettings;

    }

    public void removeSite(String oxdId) {

        try {
            RemoveSiteParams cmdParams = new RemoveSiteParams(oxdId);
            RemoveSiteResponse resp;

            if (config.isUseHttpsExtension()) {
                resp = restResponse(cmdParams, "remove-site", getPAT(), RemoveSiteResponse.class);
            } else {
                CommandClient commandClient = null;
                try {
                    commandClient = new CommandClient(config.getHost(), config.getPort());
                    Command command = new Command(CommandType.REMOVE_SITE).setParamsObject(cmdParams);
                    resp = commandClient.send(command).dataAsResponse(RemoveSiteResponse.class);
                } finally {
                    CommandClient.closeQuietly(commandClient);
                }
            }
            logger.info("Site removed {}", resp.getOxdId());
        } catch (Exception e) {
            logger.debug(e.getMessage(), e);
        }

    }

    /**
     * Returns a string with an autorization URL to redirect an application (see OpenId connect "code" flow)
     * @param acrValues List of acr_values. See OpenId Connect core 1.0 (section 3.1.2.1)
     * @param prompt See OpenId Connect core 1.0 (section 3.1.2.1)
     * @return String consisting of an authentication request with desired parameters
     * @throws Exception
     */
    private String getAuthzUrl(List<String> acrValues, String prompt) throws Exception {

        GetAuthorizationUrlParams cmdParams = new GetAuthorizationUrlParams();
        cmdParams.setOxdId(config.getClient().getOxdId());
        cmdParams.setAcrValues(acrValues);
        cmdParams.setPrompt(prompt);

        GetAuthorizationUrlResponse resp;
        if (config.isUseHttpsExtension()) {
            resp = restResponse(cmdParams, "get-authorization-url", getPAT(), GetAuthorizationUrlResponse.class);
        } else {
            CommandClient commandClient = null;
            try {
                commandClient = new CommandClient(config.getHost(), config.getPort());
                Command command = new Command(CommandType.GET_AUTHORIZATION_URL).setParamsObject(cmdParams);
                resp = commandClient.send(command).dataAsResponse(GetAuthorizationUrlResponse.class);
            } finally {
                CommandClient.closeQuietly(commandClient);
            }
        }
        return resp.getAuthorizationUrl();

    }

    public String getAuthzUrl(String acrValues) throws Exception {
        return getAuthzUrl(Collections.singletonList(acrValues), null);
    }

    public Pair<String, String> getTokens(String code, String state) throws Exception {

        GetTokensByCodeParams cmdParams = new GetTokensByCodeParams();
        cmdParams.setOxdId(config.getClient().getOxdId());
        cmdParams.setCode(code);
        cmdParams.setState(state);

        GetTokensByCodeResponse resp;
        if (config.isUseHttpsExtension()) {
            resp = restResponse(cmdParams, "get-tokens-by-code", getPAT(), GetTokensByCodeResponse.class);
        } else {
            CommandClient commandClient = null;
            try {
                commandClient = new CommandClient(config.getHost(), config.getPort());
                Command command = new Command(CommandType.GET_TOKENS_BY_CODE).setParamsObject(cmdParams);
                resp = commandClient.send(command).dataAsResponse(GetTokensByCodeResponse.class);
            } finally {
                CommandClient.closeQuietly(commandClient);
            }
        }
        //validate accessToken with at_hash inside idToken: resp.getIdToken();
        return new Pair<>(resp.getAccessToken(), resp.getIdToken());

    }

    public Map<String, List<String>> getUserClaims(String accessToken) throws Exception {

        GetUserInfoParams cmdParams = new GetUserInfoParams();
        cmdParams.setOxdId(config.getClient().getOxdId());
        cmdParams.setAccessToken(accessToken);

        GetUserInfoResponse resp;
        if (config.isUseHttpsExtension()) {
            resp = restResponse(cmdParams, "get-user-info", getPAT(), GetUserInfoResponse.class);
        } else {
            CommandClient commandClient = null;
            try {
                commandClient = new CommandClient(config.getHost(), config.getPort());
                Command command = new Command(CommandType.GET_USER_INFO).setParamsObject(cmdParams);
                resp = commandClient.send(command).dataAsResponse(GetUserInfoResponse.class);
            } finally {
                CommandClient.closeQuietly(commandClient);
            }
        }
        return resp.getClaims();

    }

    public String getLogoutUrl(String idTokenHint) throws Exception {

        GetLogoutUrlParams cmdParams = new GetLogoutUrlParams();
        cmdParams.setOxdId(config.getClient().getOxdId());
        cmdParams.setPostLogoutRedirectUri(config.getPostLogoutUri());
        cmdParams.setIdTokenHint(idTokenHint);

        LogoutResponse resp;
        if (config.isUseHttpsExtension()) {
            resp = restResponse(cmdParams, "get-logout-uri", getPAT(), LogoutResponse.class);
        } else {
            CommandClient commandClient = null;
            try {
                commandClient = new CommandClient(config.getHost(), config.getPort());
                Command command = new Command(CommandType.GET_LOGOUT_URI).setParamsObject(cmdParams);
                resp = commandClient.send(command).dataAsResponse(LogoutResponse.class);
            } finally {
                CommandClient.closeQuietly(commandClient);
            }
        }
        return resp.getUri();

    }

    public boolean updateSite(String postLogoutUri, Long expiration) throws Exception {

        UpdateSiteParams cmdParams = new UpdateSiteParams();
        cmdParams.setOxdId(config.getClient().getOxdId());
        if (postLogoutUri != null) {
            cmdParams.setPostLogoutRedirectUri(postLogoutUri);
        }
        if (expiration != null) {
            cmdParams.setClientSecretExpiresAt(new Date(expiration));
        }
        return doUpdate(cmdParams);

    }

    public boolean extendSiteLifeTime() {
        /*
        //Extending the life time cannot take place because scopes are changed by those defaulted in Gluu Server
        //Unfortunately, the custom script that turns on the scopes is only run upon registration (not updates)
        //On the other hand, oxd site registration does not allow to set the expiration. To workaround it, the
        //associated client registration cust script is in charge of extending the lifetime
        GregorianCalendar cal=new GregorianCalendar();
        cal.add(Calendar.YEAR, 1);

        UpdateSiteParams cmdParams = new UpdateSiteParams();
        cmdParams.setOxdId(config.getClient().getOxdId());
        cmdParams.setClientSecretExpiresAt(new Date(cal.getTimeInMillis()));

        return doUpdate(cmdParams);
        */
        return true;

    }

    private boolean doUpdate(UpdateSiteParams cmdParams) throws Exception {

        UpdateSiteResponse resp = null;
        if (config.isUseHttpsExtension()) {
            resp = restResponse(cmdParams, "update-site", getPAT(), UpdateSiteResponse.class);
        } else {
            CommandClient commandClient = null;
            try {
                commandClient = new CommandClient(config.getHost(), config.getPort());
                Command command = new Command(CommandType.UPDATE_SITE).setParamsObject(cmdParams);
                resp = commandClient.send(command).dataAsResponse(UpdateSiteResponse.class);
            } finally {
                CommandClient.closeQuietly(commandClient);
            }
        }
        return resp != null;

    }

    private String getPAT() throws Exception {

        GetClientTokenParams cmdParams = new GetClientTokenParams();
        cmdParams.setOpHost(config.getOpHost());
        cmdParams.setClientId(config.getClient().getClientId());
        cmdParams.setClientSecret(config.getClient().getClientSecret());
        cmdParams.setScope(Arrays.asList(UserService.OPEN_ID_SCOPES));

        GetClientTokenResponse resp = restResponse(cmdParams, "get-client-token", null, GetClientTokenResponse.class);
        String token = resp.getAccessToken();
        logger.trace("getPAT. token={}", token);

        return token;

    }

    private <T> T restResponse(IParams params, String path, String token, Class<T> responseClass) throws Exception {

        String payload = mapper.writeValueAsString(params);
        logger.trace("Sending /{} request to oxd-https-extension with payload \n{}", path, payload);

        String authz = StringUtils.isEmpty(token) ? null : "Bearer " + token;
        ResteasyWebTarget target = client.target(String.format("https://%s:%s/%s", config.getHost(), config.getPort(), path));
        Response response = target.request().header("Authorization", authz).post(Entity.json(payload));

        CommandResponse cmdResponse = response.readEntity(CommandResponse.class);
        logger.trace("Response received was \n{}", cmdResponse == null ? null : cmdResponse.getData().toString());

        return cmdResponse.getStatus().equals(ResponseStatus.OK) ? mapper.convertValue(cmdResponse.getData(), responseClass) : null;

    }

    @PreDestroy
    private void destroy() {
        if (client != null) {
            client.close();
        }
    }

}
