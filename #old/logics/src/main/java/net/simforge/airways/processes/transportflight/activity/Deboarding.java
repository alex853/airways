package net.simforge.airways.processes.transportflight.activity;

import net.simforge.airways.EventLog;
import net.simforge.airways.model.Person;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.Result;
import net.simforge.airways.processengine.activity.Activity;
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
    private static final Logger log = LoggerFactory.getLogger(Boarding.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private ProcessEngineScheduling scheduling;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public Result act() {
        BM.start("Deboarding.act");
        try (Session session = sessionFactory.openSession()) {

            Collection<Journey> journeys = JourneyOps.loadJourneysForFlight(session, transportFlight);

            List<Journey> journeysOnBoard = journeys.stream().filter(journey -> journey.getStatus() == Journey.Status.OnBoard).collect(Collectors.toList());

            if (journeysOnBoard.isEmpty()) {
                scheduling.fireEvent(DeboardingCompleted.class, transportFlight);
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
                    EventLog.info(session, log, journey, "Deboarded at " + transportFlight.getToAirport().getIcao(), transportFlight);

                    List<Person> persons = JourneyOps.getPersons(session, journey);
                    persons.forEach(person -> {

                        person.setLocationAirport(transportFlight.getToAirport());
                        session.update(person);

                        EventLog.info(session, log, person, "Deboarded at " + transportFlight.getToAirport().getIcao(), transportFlight, journey);

                    });

                    scheduling.fireEvent(session, ArrivedOnFlight.class, journey);

                });

                EventLog.info(session, log, transportFlight, "Deboarding is in progress, processed " + _paxThisRun + " PAX");

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
