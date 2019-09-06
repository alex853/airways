/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage2.flightallocation;

import net.simforge.airways.stage2.model.geo.Airport;

public class InAirportState implements State {
    private Airport airport;

    public InAirportState(Airport airport) {
        this.airport = airport;
    }

    @Override
    public boolean isCompatibleWith(State state) {
        return state instanceof InAirportState && ((InAirportState) state).airport.getIcao().equals(airport.getIcao());
    }

    @Override
    public String toString() {
        return "InAirportState{" +
                "airport.icao=" + airport.getIcao() +
                '}';
    }
}
