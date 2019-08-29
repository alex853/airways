/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.activity;

import net.simforge.airways.engine.Result;
import net.simforge.airways.engine.activity.Activity;
import net.simforge.airways.persistence.model.Journey;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.service.TransportFlightService;

import java.util.Collection;

/**
 * 'Checkin' activity starts in 'CheckinStarted' event.
 */
public class Checkin implements Activity {
    TransportFlight transportFlight;

    @Override
    public Result act() {
        TransportFlightService service = null;
        Collection<Journey> journeys = service.getJourneys(transportFlight);

        // todo some stuff

        return Result.ok();//.repeatShortly();
    }

    @Override
    public Result afterExpired() {
        return null;
    }
}
