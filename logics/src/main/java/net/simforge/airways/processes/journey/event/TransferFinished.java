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
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;
import java.util.List;

@Subscribe(TransferFinished.class)
public class TransferFinished implements Event, Handler {
    @Inject
    private Transfer transfer;
    @Inject
    private ProcessEngine engine;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        BM.start("TransferFinished.process");
        try (Session session = sessionFactory.openSession()) {

            transfer = session.load(Transfer.class, transfer.getId());

            HibernateUtils.transaction(session, () -> {

                Journey journey = transfer.getJourney();

                String to = transfer.getToCity() != null ? transfer.getToCity().getName() : transfer.getToAirport().getIcao();

                List<Person> persons = JourneyOps.getPersons(session, journey);
                persons.forEach(person -> {
                    person.setLocationCity(transfer.getToCity());
                    person.setLocationAirport(transfer.getToAirport());
                    session.update(person);

                    session.save(EventLog.make(person, "At " + to, journey));
                });

                session.save(EventLog.make(journey, "At " + to));

                if (transfer.getOnFinishedStatus() != null) {
                    journey.setStatus(transfer.getOnFinishedStatus());
                } if (transfer.getOnFinishedEvent() != null) {
                    Class eventClass;
                    try {
                        eventClass = Class.forName(transfer.getOnFinishedEvent());
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e); // todo p5
                    }
                    //noinspection unchecked
                    engine.fireEvent(session, eventClass, journey);
                }

                journey.setTransfer(null);
                session.update(journey);

            });

        } finally {
            BM.stop();
        }
    }
}
