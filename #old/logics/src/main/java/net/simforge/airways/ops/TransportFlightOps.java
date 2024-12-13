package net.simforge.airways.ops;

import net.simforge.airways.model.flight.TransportFlight;

import static net.simforge.airways.model.flight.TransportFlight.Status.Cancelled;
import static net.simforge.airways.model.flight.TransportFlight.Status.Finished;

public class TransportFlightOps {
    public static void checkAndSetStatus(TransportFlight transportFlight, TransportFlight.Status newStatus) {
        final TransportFlight.Status currStatus = transportFlight.getStatus();
        if (currStatus == Finished || currStatus == Cancelled) {
            throw new IllegalStateException("Can't update terminal status " + currStatus + " to " + newStatus);
        }

        if (currStatus.code() > newStatus.code()) {
            throw new IllegalStateException("Can't change status from " + currStatus + " to " + newStatus);
        }

        transportFlight.setStatus(newStatus);
    }
}
