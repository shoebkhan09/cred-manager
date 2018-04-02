package org.gluu.credmanager.ui.vm;

import org.gluu.credmanager.core.SessionContext;
import org.gluu.credmanager.misc.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.bind.annotation.Init;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zkplus.cdi.DelegatingVariableResolver;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author jgomer
 */
@VariableResolver(DelegatingVariableResolver.class)
public class BasicViewModel {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @WireVariable
    private SessionContext sessionContext;

    /**
     * zk implicit object (see ZUML Reference PDF) is not suitable for browser detection as the word "Chrome" is present
     * in the majority of user agent strings that are not Chrome nowadays
     * @param userAgent
     * @return
     */
    private boolean u2fSupportedBrowser(String userAgent) {

        boolean supported = false;

        Pattern p = Pattern.compile("Chrome/[\\d\\.]+ Safari/[\\d\\.]+(.*)");
        Matcher matcher = p.matcher(userAgent);
        if (matcher.find()) {
            String rest = matcher.group(1);

            if (Utils.isEmpty(rest)) { //It's chrome
                supported = true;
            } else {
                p = Pattern.compile(" OPR/(\\d+)[\\d\\.]*$");
                matcher = p.matcher(rest);
                if (matcher.find()) { //it's opera
                    rest = matcher.group(1);
                    supported = Integer.valueOf(rest) > 40;     //Verify Opera version supports u2f
                }
            }
        }
        return supported;

    }

    @Init
    public void init() {
        if (sessionContext.getU2fSupported() == null) {
            sessionContext.setU2fSupported(u2fSupportedBrowser(Executions.getCurrent().getUserAgent()));
        }
    }

}
