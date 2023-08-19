/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.activity;

import net.simforge.airways.processengine.Result;
import net.simforge.airways.processengine.activity.Activity;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static net.simforge.airways.processengine.Result.When.NextMinute;

/**
 * 'Checkin' activity starts in 'CheckinStarted' event.
 */
public class Checkin implements Activity {
    private static final Logger log = LoggerFactory.getLogger(Checkin.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public Result act() {
        BM.start("Checkin.act");
        try (Session session = sessionFactory.openSession()) {

            transportFlight = session.load(TransportFlight.class, transportFlight.getId());
            if (transportFlight.getStatus() != TransportFlight.Status.Checkin) {
                log.warn("{} - Check-in terminated as Transport Flight is in '{}' status", transportFlight, transportFlight.getStatus());
                HibernateUtils.saveAndCommit(session, EventLog.make(transportFlight, String.format("Check-in terminated as Transport Flight is in '%s' status", transportFlight.getStatus())));
                return Result.done();
            }

            HibernateUtils.transaction(session, () -> {

                Collection<Journey> journeys = JourneyOps.loadJourneysForFlight(session, transportFlight);

                List<Journey> journeysWaitingToCheckin = journeys.stream().filter(journey -> journey.getStatus() == Journey.Status.WaitingForCheckin).collect(Collectors.toList());

                int minPaxPerRun = 10;
                int paxThisRun = 0;
                List<Journey> journeysToCheckinThisRun = new ArrayList<>();
                for (Journey journey : journeysWaitingToCheckin) {
                    paxThisRun += journey.getGroupSize();
                    journeysToCheckinThisRun.add(journey);
                    if (paxThisRun >= minPaxPerRun) {
                        break;
                    }
                }

                journeysToCheckinThisRun.forEach(journey -> {

                    journey.setStatus(Journey.Status.WaitingForBoarding);
                    session.save(EventLog.make(journey, "Check-in is done", transportFlight));

                });

                session.save(EventLog.make(transportFlight, String.format("Check-in is in progress, processed %d PAX", paxThisRun)));
                log.info("{} - Check-in is in progress, processed {} PAX", transportFlight, paxThisRun);

            });
        } finally {
            BM.stop();
        }

        return Result.resume(NextMinute);
    }

    @Override
    public Result onExpiry() {
        return Result.nothing();
    }
}
