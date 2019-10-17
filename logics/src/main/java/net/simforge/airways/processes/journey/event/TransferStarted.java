/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.journey.event;

import net.simforge.airways.EventLog;
import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.model.Person;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.model.journey.Transfer;
import net.simforge.airways.processengine.TimeMachine;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.List;

@Subscribe(TransferStarted.class)
public class TransferStarted implements Event, Handler {
    @Inject
    private Transfer transfer;
    @Inject
    private ProcessEngine engine;
    @Inject
    private SessionFactory sessionFactory;
    @Inject
    private TimeMachine timeMachine;

    @Override
    public void process() {
        BM.start("TransferStarted.process");
        try (Session session = sessionFactory.openSession()) {

            transfer = session.load(Transfer.class, transfer.getId());

            HibernateUtils.transaction(session, () -> {

                Journey journey = transfer.getJourney();

                String to = transfer.getToCity() != null ? transfer.getToCity().getName() : transfer.getToAirport().getIcao();

                List<Person> persons = JourneyOps.getPersons(session, journey);
                persons.forEach(person -> {
                    String from = person.getLocationCity() != null
                            ? person.getLocationCity().getName()
                            : person.getLocationAirport() != null
                            ? person.getLocationAirport().getIcao()
                            : "UNKNOWN";
                    session.save(EventLog.make(person, "Transferring from " + from + " to " + to, journey));

                    person.setLocationCity(null);
                    person.setLocationAirport(null);
                    session.update(person);
                });

                session.save(EventLog.make(journey, "Transferring to " + to));

                LocalDateTime transferWillFinishAt = timeMachine.now().plusMinutes(transfer.getDuration());
                engine.scheduleEvent(session, TransferFinished.class, transfer, transferWillFinishAt);

                if (transfer.getOnStartedStatus() != null) {
                    journey.setStatus(transfer.getOnStartedStatus());
                    session.update(journey);
                }

            });

        } finally {
            BM.stop();
        }
    }
}
