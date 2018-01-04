/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.misc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.AppConfiguration;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.util.Date;

import static java.util.concurrent.TimeUnit.*;

/**
 * Created by jgomer on 2017-09-17.
 * ...couldn't find a working way to serve a specific custom path with static files via simple jetty config files
 */
@WebServlet(urlPatterns = {AppConfiguration.BASE_URL_BRANDING_PATH + "/*"})
public class StaticServlet extends HttpServlet {

    private Logger logger = LogManager.getLogger(getClass());
    private String contextUrl;
    private String prefix;
    private long EXPIRATION_LAG_MS=MILLISECONDS.convert(1L, DAYS);

    @Inject
    AppConfiguration appCfg;

    @Inject
    ServletContext context;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        if (prefix==null)
            resp.flushBuffer();
        else
            try {
                StringBuffer url = req.getRequestURL();
                //takes from full URL the part after the context, eg. /cred-manager
                //removes the pattern of this servlet, eg. /custom, glues both parts and obtains a File
                url.delete(0, url.indexOf(contextUrl)+contextUrl.length()).delete(0, appCfg.BASE_URL_BRANDING_PATH.length());
                File f=Paths.get(prefix, url.toString().split("/")).toFile();

                try(
                        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f));
                        BufferedOutputStream bos = new BufferedOutputStream(resp.getOutputStream()))
                {
                    int nextbyte;
                    while ((nextbyte=bis.read())!=-1)
                        bos.write(nextbyte);

                    String contentType=URLConnection.guessContentTypeFromName(f.getName());

                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.setContentType(contentType);
                    resp.addHeader("Cache-Control", "max-age=" + SECONDS.convert(EXPIRATION_LAG_MS, MILLISECONDS));
                    resp.setDateHeader("Expires", new Date().getTime() + EXPIRATION_LAG_MS);
                }
                catch (Exception e){
                    logger.error(e.getMessage());
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                }
                finally {
                    resp.flushBuffer();
                }
            }
            catch (Exception e){
                logger.error(e.getMessage(), e);
            }
    }

    @PostConstruct
    public void setup(){
        contextUrl=context.getContextPath();
        prefix=appCfg.getConfigSettings().getBrandingPath();
    }

}
