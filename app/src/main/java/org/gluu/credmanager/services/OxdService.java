package org.gluu.credmanager.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.jsonized.OxdConfig;
import org.xdi.oxd.client.CommandClient;
import org.xdi.oxd.common.Command;
import org.xdi.oxd.common.CommandType;
import org.xdi.oxd.common.params.*;
import org.xdi.oxd.common.response.*;
import org.zkoss.util.resource.Labels;

import javax.enterprise.context.ApplicationScoped;
import java.util.*;

/**
 * Created by jgomer on 2017-07-11.
 * An app. scoped bean that encapsulates interactions with on oxd-server. Contains methods depicting the steps of the
 * authorization code flow of OpenId Connect spec
 */
@ApplicationScoped
public class OxdService {

    private Logger logger = LogManager.getLogger(getClass());

    private OxdConfig config;
    private CommandClient commandClient;

    public void setSettings(OxdConfig config) throws Exception{
        this.config=config;
        commandClient=new CommandClient(config.getHost(), config.getPort());
    }

    private void modifyExpiration(CommandClient client, String oxdId) throws Exception{

        UpdateSiteParams cmdParams = new UpdateSiteParams();
        cmdParams.setOxdId(oxdId);
        /*
        oxd does not let me to set indefinite expiration :(
        The following apparently does not have effect. See https://github.com/GluuFederation/oxd/issues/85
        cmdParams.setClientSecretExpiresAt(null);
        cmdParams.setClientSecretExpiresAt(new Date(0));
        */
        GregorianCalendar cal=new GregorianCalendar();
        cal.add(Calendar.YEAR,1);
        cmdParams.setClientSecretExpiresAt(new Date(cal.getTimeInMillis()));
        logger.info(Labels.getLabel("app.extend_expiration_time"));

        Command command = new Command(CommandType.UPDATE_SITE).setParamsObject(cmdParams);
        client.send(command).dataAsResponse(UpdateSiteResponse.class);

    }

    public String doRegister(OxdConfig params) throws Exception{

        CommandClient client=null;
        String oxdId;
        try {
            logger.info(Labels.getLabel("app.registering_oxd"));
            client = new CommandClient(params.getHost(), params.getPort());

            RegisterSiteParams cmdParams = new RegisterSiteParams();
            cmdParams.setAuthorizationRedirectUri(params.getRedirectUri());
            cmdParams.setPostLogoutRedirectUri(params.getPostLogoutUri());
            cmdParams.setAcrValues(new ArrayList<>(params.getAcrValues()));

            //These scopes should be set to default=true in LDAP (or using oxTrust). Otherwise the following will have no effect
            cmdParams.setScope(Arrays.asList(UserService.requiredOpenIdScopes));

            cmdParams.setResponseTypes(Collections.singletonList("code"));  //Use "token","id_token" for implicit flow
            cmdParams.setTrustedClient(true);
            //cmdParams.setGrantType(Collections.singletonList("authorization_code"));      //this is the default grant
            cmdParams.setClientName(params.getClientName());

            Command command = new Command(CommandType.REGISTER_SITE).setParamsObject(cmdParams);

            RegisterSiteResponse site = client.send(command).dataAsResponse(RegisterSiteResponse.class);
            oxdId=site.getOxdId();

            logger.info(Labels.getLabel("app.register_oxd_ended"), oxdId);
            modifyExpiration(client,oxdId);
        }
        finally {
            CommandClient.closeQuietly(client);
        }
        return oxdId;
    }

    /**
     * Returns a string with an autorization URL to redirect an application (see OpenId connect "code" flow)
     * @param acrValues List of acr_values. See OpenId Connect core 1.0 (section 3.1.2.1)
     * @param prompt See OpenId Connect core 1.0 (section 3.1.2.1)
     * @return String consisting of an authentication request with desired parameters
     * @throws Exception
     */
    public String getAuthzUrl(List<String> acrValues, String prompt) throws Exception{

        GetAuthorizationUrlParams commandParams = new GetAuthorizationUrlParams();
        commandParams.setOxdId(config.getOxdId());
        commandParams.setAcrValues(acrValues);
        commandParams.setScope(Arrays.asList(UserService.requiredOpenIdScopes));

        if (prompt!=null)
            commandParams.setPrompt(prompt);

        Command command = new Command(CommandType.GET_AUTHORIZATION_URL).setParamsObject(commandParams);
        GetAuthorizationUrlResponse resp = commandClient.send(command).dataAsResponse(GetAuthorizationUrlResponse.class);
        return resp.getAuthorizationUrl();

    }

    public String getAuthzUrl(String acrValues) throws Exception {
        return getAuthzUrl(Collections.singletonList(acrValues), null);
    }

    public String getAccessToken(String code, String state) throws Exception{

        GetTokensByCodeParams commandParams = new GetTokensByCodeParams();
        commandParams.setOxdId(config.getOxdId());
        commandParams.setCode(code);
        commandParams.setState(state);

        Command command = new Command(CommandType.GET_TOKENS_BY_CODE).setParamsObject(commandParams);
        GetTokensByCodeResponse resp = commandClient.send(command).dataAsResponse(GetTokensByCodeResponse.class);

        //TODO: validate accessToken with at_hash inside idToken: resp.getIdToken();
        return resp.getAccessToken();
    }

    public Map<String, List<String>> getUserClaims(String accessToken) throws Exception{

        GetUserInfoParams params = new GetUserInfoParams();
        params.setOxdId(config.getOxdId());
        params.setAccessToken(accessToken);

        Command command=new Command(CommandType.GET_USER_INFO).setParamsObject(params);
        GetUserInfoResponse resp = commandClient.send(command).dataAsResponse(GetUserInfoResponse.class);
        return resp.getClaims();
    }

    public String getLogoutUrl() throws Exception{

        GetLogoutUrlParams params = new GetLogoutUrlParams();
        params.setOxdId(config.getOxdId());
        params.setPostLogoutRedirectUri(config.getPostLogoutUri());

        Command command=new Command(CommandType.GET_LOGOUT_URI).setParamsObject(params);
        LogoutResponse resp = commandClient.send(command).dataAsResponse(LogoutResponse.class);
        return resp.getUri();

    }

}