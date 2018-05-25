/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.ui.vm.admin;

import org.gluu.credmanager.core.LogService;
import org.gluu.credmanager.misc.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zkplus.cdi.DelegatingVariableResolver;
import org.zkoss.zul.Messagebox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author jgomer
 */
@VariableResolver(DelegatingVariableResolver.class)
public class LogLevelViewModel extends MainViewModel {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private List<String> logLevels;
    private String selectedLogLevel;

    @WireVariable
    private LogService logService;

    public List<String> getLogLevels() {
        return logLevels;
    }

    public String getSelectedLogLevel() {
        return selectedLogLevel;
    }

    @Init//(superclass = true)
    public void init() {
        //it seems ZK doesn't like ummodifiable lists
        logLevels = Stream.of("ERROR", "WARN", "INFO", "DEBUG", "TRACE").collect(Collectors.toList());  //SLF4J_LEVELS
        selectedLogLevel = getSettings().getLogLevel();
    }

    @NotifyChange({"selectedLogLevel"})
    @Command
    public void change(@BindingParam("level") String newLevel){

        //here it is assumed that changing log level is always a successful operation
        logService.updateLoggingLevel(newLevel);
        selectedLogLevel = newLevel;
        getSettings().setLogLevel(newLevel);

        if (updateMainSettings()) {
            logger.info("Log level changed to {}", newLevel);
        }

    }

}
