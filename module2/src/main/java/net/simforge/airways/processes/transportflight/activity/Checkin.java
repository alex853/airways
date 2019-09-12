/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.activity;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.Result;
import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.Journey;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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

    @Override
    public Result act() {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {
                // todo p1 some stuff with journeys and persons
//                Collection<Journey> journeys = JourneyOps.loadJourneysForFlight(session, transportFlight);
//
//                List<Journey> journeysToBoard = journeys.stream().filter(journey -> journey.getStatus() == Journey.Status.ReadyForCheckin).collect(Collectors.toList());
//                if (journeysToBoard.isEmpty()) {
//                    return Result.done(); // todo p2 finish checkin!
//                }




                session.save(EventLog.make(transportFlight, "Check-in is in progress")); // todo amount of PAX
            });
        }
        logger.info(transportFlight + " - Check-in is in progress"); // todo amount of PAX

        return Result.resume(NextMinute);
    }

    @Override
    public Result onExpiry() {
        return Result.nothing();
    }
}
