package net.simforge.airways.engine;

import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.processes.transportflight.event.CheckinClosed;
import net.simforge.commons.hibernate.BaseEntity;

import java.time.LocalDateTime;

public class Events {
    public static void schedule(Class clazz, BaseEntity entity) {
    }

    public static void fire(Class clazz, BaseEntity entity) {

    }

    public static void schedule(Class<CheckinClosed> checkinClosedClass, TransportFlight transportFlight, LocalDateTime plusMinutes) {

    }
}
