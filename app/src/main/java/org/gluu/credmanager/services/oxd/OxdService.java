package org.gluu.credmanager.services.oxd;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.jsonized.OxdConfig;
import org.gluu.credmanager.services.UserService;
import org.xdi.oxd.client.CommandClient;
import org.xdi.oxd.common.Command;
import org.xdi.oxd.common.CommandType;
import org.xdi.oxd.common.params.*;
import org.xdi.oxd.common.response.*;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Execution;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by jgomer on 2017-07-11.
 */
@ApplicationScoped
public class OxdService {

    /*
    ACR value for user+password auth only. It is not necessarily equivalent to Gluu's default authn method which is found
    in the oxAuthenticationMode attribute of the appliance. Anyway, simpleAuthAcr should be part of acr_supported_values
     */
    private String simpleAuthAcr="auth_ldap_server";
    private Logger logger = LogManager.getLogger(getClass());

    private OxdConfig config;
    private CommandClient commandClient;

    public String getSimpleAuthAcr() {
        return simpleAuthAcr;
    }

    public void setSettings(OxdConfig config) throws Exception{

        this.config=config;
        commandClient=new CommandClient(config.getHost(), config.getPort());

    }

    public void modifyExpiration(CommandClient client, String oxdId) throws Exception{

        UpdateSiteParams cmdParams = new UpdateSiteParams();
        cmdParams.setOxdId(oxdId);
        /*
        oxd does not let me to set indefinite expiration :(
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
            cmdParams.setAcrValues(params.getAcrValues().stream().collect(Collectors.toList()));

            //TODO: oxd bug? setting scopes...
            cmdParams.setScope(Arrays.asList(UserService.requiredOpenIdScopes));

            cmdParams.setResponseTypes(Arrays.asList("code"));  //Use "token","id_token" for implicit flow
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

    public String getAuthzUrl(List<String> acrValues) throws Exception {
        return getAuthzUrl(acrValues, null);
    }

    public String getDefaultAuthzUrl() throws Exception{
        return getAuthzUrl(Collections.singletonList(getSimpleAuthAcr()));
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
