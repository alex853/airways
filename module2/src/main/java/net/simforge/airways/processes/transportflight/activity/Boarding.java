/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.activity;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.Result;
import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.journey.Journey;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processes.transportflight.event.BoardingCompleted;
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

import static net.simforge.airways.engine.Result.When.NextMinute;

/**
 * It boards passengers to the aircraft. Boarding started by BoardingStarted event which initiated by pilot's command.
 */
public class Boarding implements Activity {
    private static Logger logger = LoggerFactory.getLogger(Boarding.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private Engine engine;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public Result act() {
        BM.start("Boarding.act");
        try (Session session = sessionFactory.openSession()) {

            Collection<Journey> journeys = JourneyOps.loadJourneysForFlight(session, transportFlight);

            List<Journey> journeysWaitingToBoard = journeys.stream().filter(journey -> journey.getStatus() == Journey.Status.WaitingForBoarding).collect(Collectors.toList());

            if (journeysWaitingToBoard.isEmpty()) {
                engine.fireEvent(session, BoardingCompleted.class, transportFlight);
                return Result.done();
            }

            int minPaxPerRun = 20;
            int paxThisRun = 0;
            List<Journey> journeysToBoardThisRun = new ArrayList<>();
            for (Journey journey : journeysWaitingToBoard) {
                paxThisRun += journey.getGroupSize();
                journeysToBoardThisRun.add(journey);
                if (paxThisRun > minPaxPerRun) {
                    break;
                }
            }

            final int _paxThisRun = paxThisRun;
            HibernateUtils.transaction(session, () -> {

                journeysToBoardThisRun.forEach(journey -> {

                    journey.setStatus(Journey.Status.OnBoard);
                    session.save(EventLog.make(journey, "On board", transportFlight));

                    List<Person> persons = JourneyOps.getPersons(session, journey);
                    persons.forEach(person -> {

                        person.setLocationAirport(null);
                        session.update(person);

                    });
                });

                session.save(EventLog.make(transportFlight, "Boarding is in progress, processed " + _paxThisRun + " PAX"));
                logger.info(transportFlight + " - Boarding is in progress, processed " + _paxThisRun + " PAX");

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
