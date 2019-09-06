/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.service;

import net.simforge.airways.persistence.model.Journey;
import net.simforge.airways.persistence.model.flight.TransportFlight;

import java.util.ArrayList;
import java.util.Collection;

@Deprecated
public class TransportFlightService {
    public Collection<Journey> getJourneys(TransportFlight transportFlight) {
        return new ArrayList<>();
    }
}
