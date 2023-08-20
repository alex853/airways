package net.simforge.airways.processes.transportflight.activity;

import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.EventLog;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.model.journey.Journey;
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
    private static final Logger log = LoggerFactory.getLogger(JourneyCancellation.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private ProcessEngine engine;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public Result act() { // todo p1 cancel all remaining tasks!  // todo AK cancellation needs to cancel and stop all related events and activities
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
                HibernateUtils.transaction(session, () -> EventLog.info(session, log, transportFlight,
                        "Journey cancellation COMPLETED"));
                return Result.done();
            }

            Map<Integer, Integer> statusToCount = new TreeMap<>();
            journeysToCancel.forEach(journey -> HibernateUtils.transaction(session, () -> {

                statusToCount.put(journey.getStatus().code(), statusToCount.getOrDefault(journey.getStatus().code(), 0) + 1);

                switch (journey.getStatus()) {
                    case LookingForPersons:
                    case LookingForTickets:
                    case WaitingForFlight:
                        EventLog.info(session, log, transportFlight, "Terminating journey", journey);
                        JourneyOps.terminateJourney(session, journey);

                        break;

                    case TransferToAirport:
                        // no op - the journey will initiate cancellation by itself or we will do it once it reachs airport
                        break;

                    case WaitingForCheckin:
                    case WaitingForBoarding:
                        journey.setStatus(journey.getStatus()); // this is to update journey and to prevent interferring updates
                        session.update(journey);

                        EventLog.info(session, log, transportFlight, "Initiating transfer to city for journey due to cancellation", journey);
                        TransferLauncher.startTransferToBiggestCityThenCancel(engine, session, journey);

                        break;

                    case TransferToCity:
                        // no op - this journey should cancel by itself
                        break;
                }

            }));

            EventLog.info(session, log, transportFlight, String.format("Journey cancellation is in progress, stats data %s", statusToCount));

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
