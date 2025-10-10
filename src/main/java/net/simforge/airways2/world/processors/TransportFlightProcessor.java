package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.simforge.airways2.world.datamodel.EventsToProcess.Type.StartAutomaticDeboarding;

public class TransportFlightProcessor {
    private static final Logger log = LoggerFactory.getLogger(TransportFlightProcessor.class);

    public static void process(final World world) {
        final int worldTime = world.getWorldTime();
        final TransportFlightControl tfControl = world.transportFlightControl();

        Processing.event(world, StartAutomaticDeboarding, event -> world.transportFlights()
                .byId(event.getObjectId())
                .ifPresent(tfControl::startDeboarding));

        Processing.heartbeat(() -> world.transportFlights().nextForHeartbeat(worldTime),
                transportFlight -> processTransportFlight(world, transportFlight));
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
