/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.services;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.gluu.credmanager.conf.ComputedOxdSettings;
import org.gluu.credmanager.conf.jsonized.OxdConfig;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.xdi.oxd.client.CommandClient;
import org.xdi.oxd.common.Command;
import org.xdi.oxd.common.CommandResponse;
import org.xdi.oxd.common.CommandType;
import org.xdi.oxd.common.ResponseStatus;
import org.xdi.oxd.common.params.*;
import org.xdi.oxd.common.response.*;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * Created by jgomer on 2017-07-11.
 * An app. scoped bean that encapsulates interactions with on oxd-server. Contains methods depicting the steps of the
 * authorization code flow of OpenId Connect spec
 */
@ApplicationScoped
public class OxdService {

    private Logger logger = LogManager.getLogger(getClass());

    private int consecutive=0;  //This is used only to debug and test
    private OxdConfig config;
    private CommandClient commandClient;
    private ResteasyClient client;

    private ObjectMapper mapper = new ObjectMapper();
    private ComputedOxdSettings computedSettings;

    public ComputedOxdSettings getComputedSettings() {
        return computedSettings;
    }

    public void setSettings(OxdConfig config) throws Exception{
        this.config=config;

        if (config.isUseHttpsExtension()) {
            client = new ResteasyClientBuilder().build();
            closeCommandClient();
        }
        else {
            commandClient=new CommandClient(config.getHost(), config.getPort());
            closeRSClient();
        }
        doRegister();

    }

    public void doRegister() throws Exception{

        //TODO: delete previous existing client?
        String clientName;
        logger.info(Labels.getLabel("app.updating_oxd_settings"), config.getHost(), config.getPort(), config.isUseHttpsExtension());

        try {
            if (config.isUseHttpsExtension()) {
                clientName="cred-manager-extension-" + consecutive;

                SetupClientParams cmdParams = new SetupClientParams();
                cmdParams.setOpHost(config.getOpHost());
                cmdParams.setAuthorizationRedirectUri(config.getRedirectUri());
                cmdParams.setPostLogoutRedirectUri(config.getPostLogoutUri());
                cmdParams.setAcrValues(config.getAcrValues());
                cmdParams.setClientName(clientName);

                //TODO: bug 3.1.1?
                List<String> scopes=new ArrayList<>(Arrays.asList(UserService.requiredOpenIdScopes));
                scopes.add("uma_protection");
                cmdParams.setScope(scopes);
                //END

                cmdParams.setResponseTypes(Collections.singletonList("code"));
                cmdParams.setTrustedClient(true);
                //cmdParams.setGrantType(Collections.singletonList("authorization_code"));      //this is the default grant

                SetupClientResponse setup = restResponse(cmdParams, "setup-client", null, SetupClientResponse.class);
                computedSettings=new ComputedOxdSettings(clientName, setup.getOxdId(), setup.getClientId(), setup.getClientSecret());
            }
            else{
                clientName="cred-manager-" + consecutive;

                RegisterSiteParams cmdParams = new RegisterSiteParams();
                cmdParams.setOpHost(config.getOpHost());
                cmdParams.setAuthorizationRedirectUri(config.getRedirectUri());
                cmdParams.setPostLogoutRedirectUri(config.getPostLogoutUri());
                cmdParams.setAcrValues(config.getAcrValues());
                cmdParams.setClientName(clientName);

                //These scopes should be set to default=true in LDAP (or using oxTrust). Otherwise the following will have no effect
                cmdParams.setScope(Arrays.asList(UserService.requiredOpenIdScopes));

                cmdParams.setResponseTypes(Collections.singletonList("code"));  //Use "token","id_token" for implicit flow
                cmdParams.setTrustedClient(true);
                //cmdParams.setGrantType(Collections.singletonList("authorization_code"));      //this is the default grant

                Command command = new Command(CommandType.REGISTER_SITE).setParamsObject(cmdParams);
                RegisterSiteResponse site = commandClient.send(command).dataAsResponse(RegisterSiteResponse.class);
                computedSettings=new ComputedOxdSettings(clientName, site.getOxdId(), null, null);
            }
            consecutive++;
            logger.info(Labels.getLabel("app.updated_oxd_settings"), computedSettings.getOxdId());
        }
        catch (Exception e){
            consecutive++;
            String msg=Labels.getLabel("app.oxd_settings_error");
            logger.fatal(msg, e);
            throw new Exception(msg, e);
        }

    }

    /**
     * Returns a string with an autorization URL to redirect an application (see OpenId connect "code" flow)
     * @param acrValues List of acr_values. See OpenId Connect core 1.0 (section 3.1.2.1)
     * @param prompt See OpenId Connect core 1.0 (section 3.1.2.1)
     * @return String consisting of an authentication request with desired parameters
     * @throws Exception
     */
    private String getAuthzUrl(List<String> acrValues, String prompt) throws Exception{

        GetAuthorizationUrlParams cmdParams = new GetAuthorizationUrlParams();
        cmdParams.setOxdId(computedSettings.getOxdId());
        cmdParams.setAcrValues(acrValues);
        cmdParams.setPrompt(prompt);

        GetAuthorizationUrlResponse resp;
        if (config.isUseHttpsExtension())
            resp=restResponse(cmdParams, "get-authorization-url", getPAT(), GetAuthorizationUrlResponse.class);
        else {
            Command command = new Command(CommandType.GET_AUTHORIZATION_URL).setParamsObject(cmdParams);
            resp = commandClient.send(command).dataAsResponse(GetAuthorizationUrlResponse.class);
        }
        return resp.getAuthorizationUrl();

    }

    public String getAuthzUrl(String acrValues) throws Exception {
        return getAuthzUrl(Collections.singletonList(acrValues), "login");  //null
    }

    public Pair<String, String> getTokens(String code, String state) throws Exception{

        GetTokensByCodeParams cmdParams = new GetTokensByCodeParams();
        cmdParams.setOxdId(computedSettings.getOxdId());
        cmdParams.setCode(code);
        cmdParams.setState(state);

        GetTokensByCodeResponse resp;
        if (config.isUseHttpsExtension())
            resp = restResponse(cmdParams, "get-tokens-by-code", getPAT(), GetTokensByCodeResponse.class);
        else {
            Command command = new Command(CommandType.GET_TOKENS_BY_CODE).setParamsObject(cmdParams);
            resp = commandClient.send(command).dataAsResponse(GetTokensByCodeResponse.class);
        }
        //TODO: validate accessToken with at_hash inside idToken: resp.getIdToken();
        return new Pair<>(resp.getAccessToken(), resp.getIdToken());
    }

    public Map<String, List<String>> getUserClaims(String accessToken) throws Exception{

        GetUserInfoParams cmdParams = new GetUserInfoParams();
        cmdParams.setOxdId(computedSettings.getOxdId());
        cmdParams.setAccessToken(accessToken);

        GetUserInfoResponse resp;
        if (config.isUseHttpsExtension())
            resp = restResponse(cmdParams, "get-user-info", getPAT(), GetUserInfoResponse.class);
        else {
            Command command = new Command(CommandType.GET_USER_INFO).setParamsObject(cmdParams);
            resp = commandClient.send(command).dataAsResponse(GetUserInfoResponse.class);
        }
        return resp.getClaims();

    }

    public String getLogoutUrl(String idTokenHint) throws Exception{

        GetLogoutUrlParams cmdParams = new GetLogoutUrlParams();
        cmdParams.setOxdId(computedSettings.getOxdId());
        cmdParams.setPostLogoutRedirectUri(config.getPostLogoutUri());
        cmdParams.setIdTokenHint(idTokenHint);

        LogoutResponse resp;
        if (config.isUseHttpsExtension())
            resp = restResponse(cmdParams, "get-logout-uri", getPAT(), LogoutResponse.class);
        else {
            Command command = new Command(CommandType.GET_LOGOUT_URI).setParamsObject(cmdParams);
            resp = commandClient.send(command).dataAsResponse(LogoutResponse.class);
        }
        return resp.getUri();

    }

    private String getPAT() throws Exception {

        GetClientTokenParams cmdParams = new GetClientTokenParams();
        cmdParams.setOpHost(config.getOpHost());
        cmdParams.setClientId(computedSettings.getClientId());
        cmdParams.setClientSecret(computedSettings.getClientSecret());
        cmdParams.setScope(Arrays.asList(UserService.requiredOpenIdScopes));

        GetClientTokenResponse resp = restResponse(cmdParams, "get-client-token", null, GetClientTokenResponse.class);
        String token=resp.getAccessToken();
        logger.trace("getPAT. token={}", token);

        return token;

    }

    private <T> T restResponse(IParams params, String path, String token, Class <T> responseClass) throws Exception{

        String payload = mapper.writeValueAsString(params);
        logger.trace("Sending /{} request to oxd-https-extension with payload \n{}", path, payload);

        String authz = StringUtils.isEmpty(token) ? null : "Bearer " + token;
        ResteasyWebTarget target = client.target(String.format("https://%s:%s/%s", config.getHost(), config.getPort(), path));
        Response response = target.request().header("Authorization", authz).post(Entity.json(payload));

        CommandResponse cmdResponse = response.readEntity(CommandResponse.class);
        logger.trace("Response received was \n{}", cmdResponse==null ? null : cmdResponse.getData().toString());

        return cmdResponse.getStatus().equals(ResponseStatus.OK) ? mapper.convertValue(cmdResponse.getData(), responseClass) : null;

    }

    private void closeCommandClient(){
        if (commandClient!=null)
            CommandClient.closeQuietly(commandClient);
    }

    private void closeRSClient(){
        if (client!=null)
            client.close();
    }

    @PreDestroy
    private void destroy(){
        closeCommandClient();
        closeRSClient();
    }

}