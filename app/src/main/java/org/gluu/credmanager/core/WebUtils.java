/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.core;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ServiceMashup;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Session;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

//TODO: instead of setting an retrieving properties in session this should be handled using a session scoped bean
/**
 * Created by jgomer on 2017-07-16.
 * Web utility class. It contains methods to be able to get session attributes, query headers, do URL redirections, etc.
 */
public class WebUtils {

    public enum RedirectStage {NONE, INITIAL, BYPASS};
    public static final String USER_PAGE_URL ="user.zul";
    public static final String ADMIN_PAGE_URL ="admin.zul";
    public static final String LOGOUT_PAGE_URL ="bye.zul";
    //public static final String HOME_PAGE_URL ="index.zul";

    public static final String SERVICES_ATTRIBUTE="SRV";
    public static final String USER_ATTRIBUTE="USR";
    public static final String REDIRECT_STAGE_ATTRIBUTE="REDIR_ST";
    private static final String OFFSET_ATTRIBUTE ="TZ";
    private static final String IDTOKEN_ATTRIBUTE="IDTOKEN";

    private static Logger logger = LogManager.getLogger(WebUtils.class);

    public static User getUser(Session session){
        return (User)session.getAttribute(USER_ATTRIBUTE);
    }

    public static void setUser(Session session, User user){
        session.setAttribute(USER_ATTRIBUTE, user);
    }

    public static ZoneOffset getUserOffset(Session session){
        return (ZoneOffset) session.getAttribute(OFFSET_ATTRIBUTE);
    }

    public static void setUserOffset(Session session, ZoneOffset zone){
        session.setAttribute(OFFSET_ATTRIBUTE, zone);
    }

    public static String getIdToken(Session session){
        return (String) session.getAttribute(IDTOKEN_ATTRIBUTE);
    }

    public static void setIdToken(Session session, String idToken){
        session.setAttribute(IDTOKEN_ATTRIBUTE, idToken);
    }

    public static RedirectStage getRedirectStage(Session session){
        RedirectStage stage=(RedirectStage)session.getAttribute(REDIRECT_STAGE_ATTRIBUTE);
        if (stage==null){
            stage=RedirectStage.NONE;
            setRedirectStage(session, stage);
        }
        return stage;
    }

    public static void setRedirectStage(Session session, RedirectStage stage){
        session.setAttribute(REDIRECT_STAGE_ATTRIBUTE, stage);
    }

    public static String getQueryParam(String param){
        Optional<String[]> values=Utils.arrayOptional(Executions.getCurrent().getParameterValues(param));
        return values.isPresent() ? values.get()[0] : null;
    }

    public static HttpServletRequest getServletRequest(){
        return (HttpServletRequest)Executions.getCurrent().getNativeRequest();
    }

    public static void execRedirect(String url){
        execRedirect(url, true);
    }

    public static void execRedirect(String url, boolean voidUI){

        try {
            Execution exec = Executions.getCurrent();
            HttpServletResponse response = (HttpServletResponse) exec.getNativeResponse();

            logger.debug(Labels.getLabel("app.redirecting_to"),url);
            response.sendRedirect(response.encodeRedirectURL(url));
            if (voidUI)
                exec.setVoided(voidUI); //no need to create UI since redirect will take place
        }
        catch (IOException e){
            logger.error(e.getMessage(),e);
        }

    }

    public static boolean isCurrentBrowserMobile(){
        return Executions.getCurrent().getBrowser("mobile") != null;
    }

    public static int getPageWidth(){
        return (int) Executions.getCurrent().getDesktop().getAttribute("pageWidth");
    }

    public static String getRequestHeader(String headerName){
        return getServletRequest().getHeader(headerName);
    }

    public static String getRemoteIP(){

        String ip=getRequestHeader("X-Forwarded-For");
        if (ip==null)
            ip=getServletRequest().getRemoteAddr();
        else{
            String ips[]=ip.split(",\\s*");
            if (Utils.arrayOptional(ips).isPresent())
                ip=ips[0];
            else
                ip=null;
        }
        return ip;

    }

    public static String getCookie(String name){
        String val=null;
        Cookie cookies[]= getServletRequest().getCookies();

        if (cookies!=null) {
            Stream<Cookie> cooks = Arrays.asList(cookies).stream();
            Optional<Cookie> cookie = cooks.filter(cook -> cook.getName().equals(name)).findFirst();
            val = cookie.isPresent() ? cookie.get().getValue() : null;
        }
        return val;

    }

    public static ServiceMashup getServices(Session session){
        return (ServiceMashup) session.getAttribute(SERVICES_ATTRIBUTE);
    }

    public static String getBrandingPath(Session session){
        return getServices(session).getAppConfig().getConfigSettings().getBrandingPath();
    }

    public static void purgeSession(Session session){
        session.removeAttribute(USER_ATTRIBUTE);
        session.removeAttribute(REDIRECT_STAGE_ATTRIBUTE);
        session.removeAttribute(OFFSET_ATTRIBUTE);
    }

    /**
     * zk implicit object (see ZUML Reference PDF) is not suitable for browser detection as the word "Chrome" is present
     * in the majority of user agent strings that are not Chrome nowadays
     * @param userAgent
     * @return
     */
    public static boolean u2fSupportedBrowser(String userAgent){

        boolean supported=false;

        Pattern p=Pattern.compile("Chrome/[\\d\\.]+ Safari/[\\d\\.]+(.*)");
        Matcher matcher=p.matcher(userAgent);
        if (matcher.find()){
            String rest=matcher.group(1);

            if (!Utils.stringOptional(rest).isPresent()) //It's chrome
                supported=true;
            else{
                p=Pattern.compile(" OPR/(\\d+)[\\d\\.]*$");
                matcher=p.matcher(rest);
                if (matcher.find()) { //it's opera
                    rest = matcher.group(1);
                    supported = Integer.valueOf(rest) > 40;     //Verify Opera version supports u2f
                }
            }
        }
        return supported;

    }

    public static String getUrlContents(String url, int timeout) throws Exception{
        return getUrlContents(url, Collections.emptyList(), timeout);
    }

    public static String getUrlContents(String url, List<NameValuePair> nvPairList, int timeout) throws Exception{

        String contents=null;

        DefaultHttpClient client = new DefaultHttpClient();
        HttpParams params = client.getParams();
        HttpConnectionParams.setConnectionTimeout(params, timeout);
        HttpConnectionParams.setSoTimeout(params, timeout);

        HttpGet httpGet = new HttpGet(url);
        URIBuilder uribe = new URIBuilder(httpGet.getURI());
        nvPairList.stream().forEach(pair -> uribe.addParameter(pair.getName(), pair.getValue()));

        httpGet.setURI(uribe.build());
        httpGet.setHeader("Accept", "application/json");
        HttpResponse response = client.execute(httpGet);
        HttpEntity entity = response.getEntity();

        logger.debug("GET request is {}", httpGet.getURI());
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
            contents=EntityUtils.toString(entity);

        EntityUtils.consume(entity);

        return contents;

    }

    public static boolean hostAvailabilityCheck(SocketAddress address, int timeout) {
        boolean available=false;
        try (
                Socket socket = new Socket()
        ){
            socket.connect(address, timeout);
            available=true;
        }
        catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return available;
    }

    public static boolean isValidUrl(String strUrl) {

        boolean valid = false;
        try {
            URL url = new URL(strUrl);
            valid = Utils.stringOptional(url.getHost()).isPresent();
        } catch (Exception e) {
            //Intentionally left empty
        }
        if (!valid) {
            logger.warn("Error validating url: {}", strUrl);
        }
        return valid;

    }

}