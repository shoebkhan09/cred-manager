/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.core.init;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import static org.gluu.credmanager.core.init.RSInitializer.ROOT_PATH;

/**
 * @author jgomer
 */
@ApplicationPath(ROOT_PATH)
public class RSInitializer extends Application {
    public static final String ROOT_PATH = "/rest";
}
