/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.core;

import org.gluu.credmanager.core.label.PluginLabelLocator;
import org.gluu.credmanager.core.label.SystemLabelLocator;
import org.gluu.credmanager.event.AppStateChangeEvent;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.slf4j.Logger;
import org.zkoss.util.resource.LabelLocator;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.WebApp;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.ServletContext;
import java.io.Closeable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author jgomer
 */
@ApplicationScoped
public class ZKService {

    public static final String EXTERNAL_LABELS_DIR = "labels";

    private static final String SYS_LABELS_LOCATION = "/WEB-INF/classes/labels/";
    private static final Class<Closeable> CLOSEABLE_CLS = Closeable.class;

    @Inject
    private Logger logger;

    @Inject
    private EventBus eventBus;

    @Inject
    private ConfigurationHandler confHandler;

    @Inject
    private RSRegistryHandler registryHandler;

    private WebApp app;

    private Map<String, LabelLocator> labelLocators;

    @PostConstruct
    private void inited() {
        eventBus.register(this);
        labelLocators = new HashMap<>();
        logger.info("ZK initialized");
    }

    public void init(WebApp app) {

        try {
            this.app = app;
            //This attribute is stored here for future use inside zul templates
            app.setAttribute("appName", app.getAppName());
            //app.setAttribute("extraCssSnipet", confHandler.getExtraCssSnippet());

            readSystemLabels();
            confHandler.init();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

    }

    private void readSystemLabels() {

        ServletContext context = app.getServletContext();
        Set<String> labelsListing = context.getResourcePaths(SYS_LABELS_LOCATION);

        if (labelsListing != null) {
            labelsListing.stream().filter(path -> path.endsWith(".properties"))
                    .map(path -> {
                        try {
                            return context.getResource(path).toString();
                        } catch (MalformedURLException e) {
                            logger.error("Error converting path {} to URL", path);
                            return "";
                        }
                    })
                    .forEach(strUrl -> labelLocators.put(Double.toString(Math.random()),
                            new SystemLabelLocator(strUrl.substring(0, strUrl.lastIndexOf(".")))));
        }

    }

    @Subscribe
    public void onAppStateChange(AppStateChangeEvent event) {

        //TODO: do some processing, use app member
        switch (event.getState()) {
            case FAIL:
                break;
            case LOADING:
                break;
            case OPERATING:
                break;
            default:

        }

    }

    void readPluginLabels(String id, Path path) {
        labelLocators.put(id, new PluginLabelLocator(path, EXTERNAL_LABELS_DIR));
    }

    void removePluginLabels(String id) {
        try {
            LabelLocator locator = labelLocators.get(id);
            if (locator != null) {
                if (CLOSEABLE_CLS.isAssignableFrom(locator.getClass())) {
                    CLOSEABLE_CLS.cast(locator).close();
                }
                labelLocators.remove(id);
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

    }

    void refreshLabels() {
        logger.info("");
        logger.info("Refreshing labels");
        labelLocators.values().forEach(Labels::register);
        Labels.reset();
    }

    String getAppFileSystemRoot() {
        return app.getServletContext().getRealPath("/");
    }

}
