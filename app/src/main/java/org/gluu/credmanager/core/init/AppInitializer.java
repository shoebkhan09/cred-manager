package org.gluu.credmanager.core.init;

import javassist.*;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * This listener class can be removed when using a ZK CE version higher than 8.5.0. In this case, it suffices to uncomment
 * the corresponding library property in file zk.xml
 * @author jgomer
 */
//TODO: remove this class when using ZK CE > 8.5.0
@WebListener
public class AppInitializer implements ServletContextListener {

    @Inject
    private Logger logger;

    private static final String patch1 = "public ClassLoader getContextClassLoaderForName(String className);";

    private static final String patch2 = "private static org.zkoss.lang.ContextClassLoaderFactory factory;";

    private static final String patch3 = "factory = (org.zkoss.lang.ContextClassLoaderFactory) " +
            "newInstanceByThread(org.zkoss.lang.Library.getProperty(\"org.zkoss.lang.contextClassLoader.class\"));";

    private static final String patch4 = "public static ClassLoader getContextClassLoaderForName(String className) { " +
            "if (factory == null) " +
            "return Thread.currentThread().getContextClassLoader(); " +
            "return factory.getContextClassLoaderForName(className); " +
            "}";

    private static final String patch5 ="{ " +
            "String clsName = toInternalForm($1); " +
            "final Class cls = org.zkoss.lang.Primitives.toClass(clsName); " +
            "if (cls != null) " +
            "    return cls; " +
            "ClassLoader cl = org.zkoss.lang.Library.getProperty(\"org.zkoss.lang.contextClassLoader.class\") == null " +
            "        ? Thread.currentThread().getContextClassLoader() " +
            "        : getContextClassLoaderForName(clsName); " +
            "if (cl != null) " +
            "    try { " +
            "        return Class.forName(clsName, true, cl); " +
            "    } catch (ClassNotFoundException ex) { " +
            "    } " +
            "return Class.forName(clsName); " +
            "}";

    public void contextInitialized(ServletContextEvent sce)  {

        //This method modifies a couple of ZK classes before they are loaded by the JVM. This is required to achieve a
        //fix for this problem: http://tracker.zkoss.org/browse/ZK-3762
        //In summary every cred-manager plugin is loaded in a separate Java classloader, meaning that ZK ViewModel classes
        //bound in zuml templates won't be found in current thread's classloader unless this problem is tackled.
        //The patching here is inspired in code of ZK 8.5.1 EE (https://github.com/zkoss/zk/tree/master/zcommon)

        logger.trace("AppInitializer. Applying ZK classes customization");

        System.setProperty("org.zkoss.lang.contextClassLoader.class", "org.gluu.credmanager.misc.CustomClassLoader");
        try {
            ClassPool pool = ClassPool.getDefault();
            pool.insertClassPath(new ClassClassPath(this.getClass()));

            logger.trace("AppInitializer. Patching org.zkoss.lang.ContextClassLoaderFactory");
            //Patch ZK's ContextClassLoaderFactory by adding new method
            CtClass ctLoaderFactory = pool.get("org.zkoss.lang.ContextClassLoaderFactory");
            CtMethod ctMethod = CtMethod.make(patch1, ctLoaderFactory);
            ctLoaderFactory.addMethod(ctMethod);
            //Load the patched interface
            ctLoaderFactory.toClass();

            logger.trace("AppInitializer. Patching org.zkoss.lang.Classes");
            //Patch ZK's Classes
            CtClass ctClasses = pool.get("org.zkoss.lang.Classes");

            //Add factory field
            CtField f = CtField.make(patch2, ctClasses);
            ctClasses.addField(f, "null");

            //Initialize field at constructor
            CtConstructor constructor = ctClasses.getClassInitializer();
            constructor.insertAfter(patch3);

            //Add a getContextClassLoaderForName method to Classes class
            ctMethod = CtMethod.make(patch4, ctClasses);
            ctClasses.addMethod(ctMethod);

            //Rewrite method forNameByThread
            ctMethod = ctClasses.getDeclaredMethod("forNameByThread");
            ctMethod.setBody(patch5);

            //Load the patched class
            ctClasses.toClass();

            logger.trace("AppInitializer. Done");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            logger.warn("AppInitializer. Failure patching. UI pages of external plugins may not work properly");
        }

    }

    public void contextDestroyed(ServletContextEvent sce) {
    }

}