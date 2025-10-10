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
        final TransportFlightControl tfControl = world.transportFlightControl();

        EventProcessing.process(world, StartAutomaticDeboarding, event -> world.transportFlights()
                .byId(event.getObjectId())
                .ifPresent(tfControl::startDeboarding));

        while (true) {
            final Optional<TransportFlights.Flight> transportFlight = world.transportFlights().nextForHeartbeat(worldTime);
            if (transportFlight.isEmpty()) {
                break;
            }

            try {
                processTransportFlight(world, transportFlight.get());
            } catch (final RuntimeException e) {
                log.warn("t/f #{} processing error", transportFlight.get().getId(), e);
                throw e;
            }
        }
    }

    private static void processTransportFlight(final World world, final TransportFlights.Flight transportFlight) {
        final TransportFlightControl tfControl = world.transportFlightControl();

        transportFlight.setHeartbeatTime(0);
        switch (transportFlight.getStatus()) {
            case Scheduled -> {
                if (tfControl.ifCheckInTimeComes(transportFlight)) {
                    tfControl.startCheckIn(transportFlight);
                }
            }
            case CheckIn -> {
                if (tfControl.isCheckInFinishTimePassed(transportFlight) || tfControl.areAllPaxCheckedIn(transportFlight)) {
                    tfControl.waitForBoarding(transportFlight);
                } else {
                    tfControl.continueCheckIn(transportFlight);
                }
            }
            case Boarding -> {
                if (tfControl.isBoardingFinishTimePassed(transportFlight) || tfControl.areAllPaxBoarded(transportFlight)) {
                    tfControl.waitForDeparture(transportFlight);
                } else {
                    tfControl.continueBoarding(transportFlight);
                }
            }
            case Deboarding -> {
                if (tfControl.areAllPaxDeboarded(transportFlight)) {
                    tfControl.finish(transportFlight);
                } else {
                    tfControl.continueDeboarding(transportFlight);
                }
            }
        }
    }
}
