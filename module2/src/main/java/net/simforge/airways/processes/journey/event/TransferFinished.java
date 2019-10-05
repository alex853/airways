/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.journey.event;

import net.simforge.airways.engine.event.Event;
import net.simforge.airways.engine.event.Handler;
import net.simforge.airways.engine.event.Subscribe;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.journey.Journey;
import net.simforge.airways.persistence.model.journey.Transfer;
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
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        BM.start("TransferFinished.process");
        try (Session session = sessionFactory.openSession()) {

            transfer = session.load(Transfer.class, transfer.getId());

            HibernateUtils.transaction(session, () -> {

                Journey journey = transfer.getJourney();

                List<Person> persons = JourneyOps.getPersons(session, journey);
                persons.forEach(person -> {
                    person.setLocationCity(transfer.getToCity());
                    person.setLocationAirport(transfer.getToAirport());
                    session.update(person);
                });

                if (transfer.getOnFinishedStatus() != null) {
                    journey.setStatus(transfer.getOnFinishedStatus());
                }

                journey.setTransfer(null);
                session.update(journey);

            });

        } finally {
            BM.stop();
        }
    }
}
