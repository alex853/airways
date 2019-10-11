/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.activity;

import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.Result;
import net.simforge.airways.processengine.activity.Activity;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.journey.Journey;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processes.journey.event.ArrivedOnFlight;
import net.simforge.airways.processes.transportflight.event.DeboardingCompleted;
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

public class Deboarding implements Activity {
    private static Logger logger = LoggerFactory.getLogger(Boarding.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private ProcessEngine engine;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public Result act() {
        BM.start("Deboarding.act");
        try (Session session = sessionFactory.openSession()) {

            Collection<Journey> journeys = JourneyOps.loadJourneysForFlight(session, transportFlight);

            List<Journey> journeysOnBoard = journeys.stream().filter(journey -> journey.getStatus() == Journey.Status.OnBoard).collect(Collectors.toList());

            if (journeysOnBoard.isEmpty()) {
                engine.fireEvent(DeboardingCompleted.class, transportFlight);
                return Result.done();
            }

            int minPaxPerRun = 20;
            int paxThisRun = 0;
            List<Journey> journeysToDeboardThisRun = new ArrayList<>();
            for (Journey journey : journeysOnBoard) {
                paxThisRun += journey.getGroupSize();
                journeysToDeboardThisRun.add(journey);
                if (paxThisRun >= minPaxPerRun) {
                    break;
                }
            }

            final int _paxThisRun = paxThisRun;
            HibernateUtils.transaction(session, () -> {

                journeysToDeboardThisRun.forEach(journey -> {

                    journey.setStatus(Journey.Status.JustArrived);
                    session.save(EventLog.make(journey, "Deboarded at " + transportFlight.getToAirport().getIcao(), transportFlight));

                    List<Person> persons = JourneyOps.getPersons(session, journey);
                    persons.forEach(person -> {

                        person.setLocationAirport(transportFlight.getToAirport());
                        session.update(person);

                        session.save(EventLog.make(person, "Deboarded at " + transportFlight.getToAirport().getIcao(), transportFlight, journey));

                    });

                    engine.fireEvent(session, ArrivedOnFlight.class, journey);

                });

                session.save(EventLog.make(transportFlight, "Deboarding is in progress, processed " + _paxThisRun + " PAX"));
                logger.info(transportFlight + " - Deboarding is in progress, processed " + _paxThisRun + " PAX");

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
