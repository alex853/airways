package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class TransportFlightProcessor {
    private static final Logger log = LoggerFactory.getLogger(TransportFlightProcessor.class);

    public static void process(final World world) {
        final int worldTime = world.getWorldTime();

        final TransportFlightControl tfControl = TransportFlightControl.instance(world);
        while (true) {
            final Optional<TransportFlights.Flight> transportFlightO = world.transportFlights().nextForHeartbeat(worldTime);
            if (transportFlightO.isEmpty()) {
                break;
            }

            final TransportFlights.Flight transportFlight = transportFlightO.get();
            try {
                processTransportFlight(world, tfControl, transportFlight);
            } catch (final RuntimeException e) {
                log.warn("t/f {} processing error", transportFlight.getId(), e);
                throw e;
            }
        }
    }

    private static void processTransportFlight(final World world, final TransportFlightControl tfControl, final TransportFlights.Flight transportFlight) {
        transportFlight.setHeartbeatTime(0);
        switch (transportFlight.getStatus()) {
            case Scheduled -> {
                if (tfControl.checkinTimeComes(transportFlight)) {
                    tfControl.startCheckin(transportFlight);
                }
            }
            case Checkin -> {
                if (tfControl.checkinTimeEnds(transportFlight) || tfControl.allPaxCheckedIn(transportFlight)) {
                    tfControl.waitForBoarding(transportFlight);
                } else {
                    tfControl.continueCheckin(transportFlight);
                }
            }
            case Boarding -> {
                if (tfControl.allPaxBoarded(transportFlight)) {
                    tfControl.waitForDeparture(transportFlight);
                }
            }
            case Deboarding -> {
                if (tfControl.allPaxDeboarded(transportFlight)) {
                    tfControl.finish(transportFlight);
                }
            }
        }
    }
}
