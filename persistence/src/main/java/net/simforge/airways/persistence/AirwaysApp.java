package net.simforge.airways.persistence;

import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AirwaysApp {
    private static Logger logger = LoggerFactory.getLogger(AirwaysApp.class.getName());

    private static SessionFactory sessionFactory;

    public static class StartupAction implements Runnable {
        @Override
        public void run() {
            logger.info("creating session factory");
            sessionFactory = Airways.buildSessionFactory();
        }
    }

    public static class ShutdownAction implements Runnable {
        @Override
        public void run() {
            logger.info("killing session factory");
            SessionFactory _sessionFactory = sessionFactory;
            sessionFactory = null;
            _sessionFactory.close();
        }
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }
}
