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

import static net.simforge.airways.engine.Result.When.NextMinute;

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
    @Inject
    private TransportFlightService transportFlightService;

    @Override
    public Result act() {
        Collection<Journey> journeys = transportFlightService.getJourneys(transportFlight);

        // todo some stuff with journeys and persons

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, EventLog.make(transportFlight, "Check-in is in progress")); // todo amount of PAX
        }
        logger.info(transportFlight + " - Check-in is in progress"); // todo amount of PAX

        return Result.resume(NextMinute);
    }

    @Override
    public Result onExpiry() {
        return Result.nothing();
    }
}
