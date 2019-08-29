/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

package net.simforge.airways.service;

import net.simforge.airways.persistence.model.Journey;
import net.simforge.airways.persistence.model.flight.TransportFlight;

import java.util.Collection;

public interface TransportFlightService {
    Collection<Journey> getJourneys(TransportFlight transportFlight);
}
