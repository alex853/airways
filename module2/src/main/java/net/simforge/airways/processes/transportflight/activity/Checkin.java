package net.simforge.airways.processes.transportflight.activity;

import net.simforge.airways.engine.proto.Activity;
import net.simforge.airways.engine.proto.ActivityResult;
import net.simforge.airways.model.Journey;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.service.TransportFlightService;

import java.util.Collection;

/**
 * 'Checkin' activity starts in 'CheckinStarted' event.
 */
public class Checkin implements Activity<TransportFlight> {
    @Override
    public ActivityResult act(TransportFlight transportFlight) {
        TransportFlightService service = null;
        Collection<Journey> journeys = service.getJourneys(transportFlight);

        // todo some stuff

        return ActivityResult.repeatShortly();
    }
}
