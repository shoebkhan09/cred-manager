/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.ui.init;

import org.gluu.credmanager.core.ExtensionsManager;
import org.gluu.credmanager.misc.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.lang.ContextClassLoaderFactory;

import java.util.Arrays;

/**
 * @author jgomer
 */
public class CustomClassLoader implements ContextClassLoaderFactory {

    private static final String[] DEFAULT_PACKAGES = {"org.zkoss", "java", "javax"};

    private Logger logger = LoggerFactory.getLogger(getClass());
    private ExtensionsManager extManager;

    public ClassLoader getContextClassLoader(Class<?> reference) {
        return Thread.currentThread().getContextClassLoader();
    }

    public ClassLoader getContextClassLoaderForName(String className) {

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        //Filter out uninteresting classes
        if (Arrays.stream(DEFAULT_PACKAGES).anyMatch(pkg -> className.startsWith(pkg + "."))
                || !Character.isLetter(className.charAt(0))) {
            return loader;
        }

        try {
            logger.trace("Looking up a class loader for '{}'", className);
            loader.loadClass(className);
            return loader;
        } catch (ClassNotFoundException e) {

            logger.warn("Class not found in current thread's context class loader");
            if (extManager == null) {
                extManager = Utils.managedBean(ExtensionsManager.class);
            }

            loader = extManager.getPluginClassLoader(className);
            if (loader == null) {
                logger.error("Could not find a plugin class loader for class either");
            } else {
                logger.info("Class found in one of plugins class loaders");
            }
        }
        return loader;

    }

}
