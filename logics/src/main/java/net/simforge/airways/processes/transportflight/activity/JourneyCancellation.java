/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.activity;

import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.persistence.model.journey.Journey;
import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.Result;
import net.simforge.airways.processengine.activity.Activity;
import net.simforge.airways.processes.journey.TransferLauncher;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

public class JourneyCancellation implements Activity { // todo p1 checkin can start even if flight is cancelled
    private static Logger logger = LoggerFactory.getLogger(JourneyCancellation.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private ProcessEngine engine;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public Result act() { // todo p1 cancel all remaining tasks!
        BM.start("JourneyCancellation.act");
        try (Session session = sessionFactory.openSession()) {

            Collection<Journey> journeys = JourneyOps.loadJourneysForFlight(session, transportFlight);

            List<Journey> journeysToCancel = journeys.stream().filter(
                    journey ->
                            journey.getStatus() == Journey.Status.LookingForPersons
                                    || journey.getStatus() == Journey.Status.LookingForTickets
                                    || journey.getStatus() == Journey.Status.WaitingForFlight
                                    || journey.getStatus() == Journey.Status.TransferToAirport
                                    || journey.getStatus() == Journey.Status.WaitingForCheckin
                                    || journey.getStatus() == Journey.Status.WaitingForBoarding
                                    || journey.getStatus() == Journey.Status.TransferToCity
            ).collect(Collectors.toList());

            if (journeysToCancel.isEmpty()) {
                HibernateUtils.saveAndCommit(session, EventLog.make(transportFlight, "Journey cancellation COMPLETED"));
                logger.info("{} - Journey cancellation COMPLETED", transportFlight);
                return Result.done();
            }

            Map<Integer, Integer> statusToCount = new TreeMap<>();
            journeysToCancel.forEach(journey -> {
                HibernateUtils.transaction(session, () -> {

                    statusToCount.put(journey.getStatus(), statusToCount.getOrDefault(journey.getStatus(), 0) + 1);

                    switch (journey.getStatus()) {
                        case Journey.Status.LookingForPersons:
                        case Journey.Status.LookingForTickets:
                        case Journey.Status.WaitingForFlight:
                            JourneyOps.terminateJourney(session, journey);
                            logger.info("{} - Terminating journey {}", transportFlight, journey);

                            break;

                        case Journey.Status.TransferToAirport:
                            // no op - the journey will initiate cancellation by itself or we will do it once it reachs airport
                            break;

                        case Journey.Status.WaitingForCheckin:
                        case Journey.Status.WaitingForBoarding:
                            journey.setStatus(journey.getStatus()); // this is to update journey and to prevent interferring updates
                            session.update(journey);

                            TransferLauncher.startTransferToBiggestCityThenCancel(engine, session, journey);
                            logger.info("{} - Initiating transfer to city for journey {}", transportFlight, journey);

                            break;

                        case Journey.Status.TransferToCity:
                            // no op - this journey should cancel by itself
                            break;
                    }

                });
            });

            logger.info("{} - Journey cancellation is in progress, stats data {}", transportFlight, statusToCount);

            return Result.resume(Result.When.FewTimesPerHour);
        } finally {
            BM.stop();
        }
    }

    @Override
    public Result onExpiry() {
        return Result.nothing();
    }
}
