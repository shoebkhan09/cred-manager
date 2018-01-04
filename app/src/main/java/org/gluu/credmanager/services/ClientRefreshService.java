/*
 * cred-manager is available under the MIT License 2008. See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright c 2017, Gluu
 */
package org.gluu.credmanager.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.zkoss.util.resource.Labels;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Inject;

import java.util.Date;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

/**
 * Created by jgomer on 2017-11-30.
 */
@ApplicationScoped
public class ClientRefreshService implements Job {

    private Logger logger = LogManager.getLogger(getClass());

    private Scheduler scheduler;

    @Inject
    private OxdService oxdService;

    public ClientRefreshService(){
        try {
            scheduler = StdSchedulerFactory.getDefaultScheduler();
            scheduler.start();
        }
        catch (SchedulerException e){
            logger.error(e.getMessage(), e);
        }
    }

    public void schedule(int interval) throws SchedulerException{

        JobDetail job = JobBuilder.newJob(getClass()).withIdentity("job", "group").build();
        Trigger trigger = TriggerBuilder.newTrigger().withIdentity("trigger", "group")
                .startAt(new Date(System.currentTimeMillis() + interval*1000))
                .withSchedule(simpleSchedule().withIntervalInSeconds(interval).repeatForever())
                .build();
        scheduler.scheduleJob(job, trigger);
    }

    public void execute(JobExecutionContext context) throws JobExecutionException {

        try {
            OxdService service= CDI.current().select(OxdService.class).get();
            service.doRegister();
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            logger.warn(Labels.getLabel("app.refresh_clients_warn"));
        }

    }

    @PreDestroy
    private void destroy() {
        try {
            scheduler.shutdown();
        }
        catch (SchedulerException e) {
            logger.error(e.getMessage(), e);
        }
    }

}
