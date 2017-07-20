package org.gluu.credmanager.core.ortho;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

/**
 * Created by jgomer on 2017-07-04.
 */
@Logged
@Interceptor
public class LoggedInterceptor {

    @AroundInvoke
    public Object manage(InvocationContext ctx) throws Exception {
        Logger logger = LogManager.getLogger(getClass());
        logger.info("Intercepted");
        return ctx.proceed();
    }

}
