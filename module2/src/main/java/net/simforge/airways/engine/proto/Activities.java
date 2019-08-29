/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.engine.proto;

import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.processes.flight.activity.AllocateFlight;
import net.simforge.airways.processes.transportflight.activity.Checkin;

import java.time.LocalDateTime;

@Deprecated
public class Activities {
    public static void start(Class aClass, Object o) {

    }

    public static void stop(ActivityStatus status) {

    }

    public static void startWithExpiration(Class<AllocateFlight> allocateFlightClass, Flight flight, LocalDateTime minusMinutes) {

    }

    public static ActivityStatus findStatus(Class<Checkin> checkinClass, TransportFlight transportFlight) {
        return null;
    }
}
