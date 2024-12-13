package net.simforge.airways.processes.journey.event;

import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.model.journey.Itinerary;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.processes.DurationConsts;
import net.simforge.airways.processes.transfer.journey.TransferLauncher;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import javax.inject.Inject;
import java.time.LocalDateTime;

/**
 * It calculates moment when passengers have to start their way to airport.
 * DepartAtAirport event will be fired at that calculated moment.
 * It will change location of persons and update status of journey.
 * Also it will schedule event TransferCityToAirportArrived.
 * TransferCityToAirportArrived updates persons' location to airport location and prepares journey for check-in.
 */
@Subscribe(TicketsBought.class)
public class TicketsBought implements Event, Handler {
    @Inject
    private Journey journey;
    @Inject
    private ProcessEngineScheduling scheduling;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        BM.start("TicketsBought.process");
        try (Session session = sessionFactory.openSession()) {

            Itinerary itinerary = journey.getItinerary();
            TransportFlight transportFlight = itinerary.getFlight();
            LocalDateTime checkinStartsAt = transportFlight.getDepartureDt().minusMinutes(DurationConsts.START_OF_CHECKIN_TO_DEPARTURE_MINS);

            HibernateUtils.transaction(session, () -> TransferLauncher.scheduleTransferToAirport(scheduling, session, journey, transportFlight.getFromAirport(), checkinStartsAt));

        } finally {
            BM.stop();
        }
    }
}
