package org.gluu.credmanager.core.init;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.core.WebUtils;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.au.AuRequest;
import org.zkoss.zk.au.AuService;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.util.DesktopInit;

import java.time.ZoneOffset;

/**
 * Created by jgomer on 2017-08-22.
 * Intercepts all ZK AuRequests (see ZK 8 Client-side Reference manual, section "Communication")
 */
class CustomAuService implements AuService {

    private Logger logger = LogManager.getLogger(getClass());

    public boolean service(AuRequest request, boolean everError) {
        boolean processed = false;

        if (request.getCommand().equals("onAfterLoad")) {
            Session se=request.getDesktop().getExecution().getSession();
            ZoneOffset zoffset=ZoneOffset.ofTotalSeconds((int) request.getData().get("offset"));
            WebUtils.setUserOffset(se, zoffset);
            logger.info(Labels.getLabel("app.user_offset"), zoffset.toString());
            processed = true;
        }
        return processed;

    }

}

/**
 * Created by jgomer on 2017-08-22.
 * Listens the initialization events of ZK Desktops
 */
public class DesktopInitializer implements DesktopInit {

    public void init(Desktop desktop, Object request) throws Exception {
        desktop.addListener(new CustomAuService());
    }

}