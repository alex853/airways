/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.activity;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.Result;
import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.Journey;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.service.TransportFlightService;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Collection;

/**
 * 'Checkin' activity starts in 'CheckinStarted' event.
 */
public class Checkin implements Activity {
    private static Logger logger = LoggerFactory.getLogger(Checkin.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private Engine engine;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public Result act() {
        TransportFlightService service = null;
        Collection<Journey> journeys = service.getJourneys(transportFlight);

        // todo some stuff with journeys and persons

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, EventLog.make(transportFlight, "Check-in in progress")); // todo amount of PAX
        }
        logger.info(transportFlight + " - Check-in in progress"); // todo amount of PAX

        return Result.ok(Result.NextRun.NextMinute);
    }

    @Override
    public Result afterExpired() {
        return null;
    }
}
