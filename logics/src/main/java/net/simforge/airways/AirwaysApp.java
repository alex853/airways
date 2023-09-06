package net.simforge.airways;

import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.RealTimeMachine;
import net.simforge.airways.processengine.TimeMachine;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AirwaysApp {
    private static final Logger log = LoggerFactory.getLogger(AirwaysApp.class.getName());

    private static SessionFactory sessionFactory;
    private static TimeMachine timeMachine;
    private static ProcessEngineScheduling scheduling;

    public static class StartupAction implements Runnable {
        @Override
        public void run() {
            log.info("creating session factory");
            sessionFactory = Airways.buildSessionFactory();
            timeMachine = new RealTimeMachine();
            scheduling = new ProcessEngineScheduling(sessionFactory, timeMachine);
        }
    }

    public static class ShutdownAction implements Runnable {
        @Override
        public void run() {
            log.info("killing session factory");
            scheduling = null;
            SessionFactory _sessionFactory = sessionFactory;
            sessionFactory = null;
            _sessionFactory.close();
        }
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public static TimeMachine getTimeMachine() {
        return timeMachine;
    }

    public static ProcessEngineScheduling getScheduling() {
        return scheduling;
    }
}
