package net.simforge.airways.processes.transfer.pilot;

import net.simforge.airways.EventLog;
import net.simforge.airways.model.Person;
import net.simforge.airways.model.Pilot;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.model.journey.Transfer;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.ops.PilotOps;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

@Subscribe(PilotTransferFinished.class)
public class PilotTransferFinished implements Event, Handler {
    private static final Logger log = LoggerFactory.getLogger(PilotTransferFinished.class);

    @Inject
    private Transfer transfer;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        BM.start("PilotTransferFinished.process");
        try (Session session = sessionFactory.openSession()) {

            transfer = session.load(Transfer.class, transfer.getId());

            HibernateUtils.transaction(session, () -> {

                Journey journey = transfer.getJourney();

                String to = transfer.getToCity() != null ? transfer.getToCity().getName() : transfer.getToAirport().getIcao();

                List<Person> persons = JourneyOps.getPersons(session, journey);
                persons.forEach(person -> {
                    person.setStatus(Person.Status.Idle);
                    person.setLocationCity(transfer.getToCity());
                    person.setLocationAirport(transfer.getToAirport());
                    session.update(person);

                    Pilot pilot = PilotOps.loadPilotByPersonId(session, person.getId());
                    pilot.setStatus(Pilot.Status.Idle);
                    session.update(pilot);

                    EventLog.info(session, log, person, "At " + to, journey);
                });

                EventLog.info(session, log, journey, "At " + to);

                journey.setStatus(Journey.Status.Finished);
                journey.setTransfer(null);
                session.update(journey);

            });

        } finally {
            BM.stop();
        }
    }
}
