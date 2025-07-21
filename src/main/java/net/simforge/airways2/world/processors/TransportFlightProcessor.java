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
            switch (transportFlight.getStatus()) {
                case Scheduled -> {
                    if (tfControl.checkinTimeComes(transportFlight)) {
                        tfControl.startCheckin(transportFlight);
                    }
                }
                case Checkin -> {
                    if (tfControl.allPaxCheckedIn(transportFlight)) {
                        tfControl.waitForBoarding(transportFlight);
                    }
                }
            }
        }
    }
}