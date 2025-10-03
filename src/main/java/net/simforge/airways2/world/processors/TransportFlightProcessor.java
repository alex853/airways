package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static net.simforge.airways2.world.datamodel.EventsToProcess.Type.StartAutomaticDeboarding;

public class TransportFlightProcessor {
    private static final Logger log = LoggerFactory.getLogger(TransportFlightProcessor.class);

    public static void process(final World world) {
        final int worldTime = world.getWorldTime();
        final TransportFlightControl tfControl = TransportFlightControl.instance(world);

        EventProcessing.process(world, StartAutomaticDeboarding, event -> world.transportFlights()
                .byId(event.getObjectId())
                .ifPresent(tfControl::startDeboarding));

        while (true) {
            final Optional<TransportFlights.Flight> transportFlight = world.transportFlights().nextForHeartbeat(worldTime);
            if (transportFlight.isEmpty()) {
                break;
            }

            try {
                processTransportFlight(world, tfControl, transportFlight.get());
            } catch (final RuntimeException e) {
                log.warn("t/f #{} processing error", transportFlight.get().getId(), e);
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
            case CheckIn -> {
                if (tfControl.checkinTimeEnds(transportFlight) || tfControl.allPaxCheckedIn(transportFlight)) {
                    tfControl.waitForBoarding(transportFlight);
                } else {
                    tfControl.continueCheckin(transportFlight);
                }
            }
            case Boarding -> {
                if (tfControl.boardingTimeEnds(transportFlight) || tfControl.allPaxBoarded(transportFlight)) {
                    tfControl.waitForDeparture(transportFlight);
                } else {
                    tfControl.continueBoarding(transportFlight);
                }
            }
            case Deboarding -> {
                if (tfControl.allPaxDeboarded(transportFlight)) {
                    tfControl.finish(transportFlight);
                } else {
                    tfControl.continueDeboarding(transportFlight);
                }
            }
        }
    }
}
