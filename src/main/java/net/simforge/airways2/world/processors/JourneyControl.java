package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;

import java.util.EnumSet;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class JourneyControl {
    private final World world;

    private JourneyControl(World world) {
        this.world = world;
    }

    public static JourneyControl instance(final World world) {
        return new JourneyControl(world);
    }

    public Journeys.Journey create(final City2CityFlows.Flow c2cFlow, final CabinLayout.Service service, final boolean directOrBackDirection) {
        checkNotNull(c2cFlow);
        checkNotNull(service);

        final Journeys.Journey journey = world.journeys().create(
                Journeys.Status.LookingForTickets,
                directOrBackDirection ? c2cFlow.getFromCityId() : c2cFlow.getToCityId(), // todo ak1 'roundtrip support' - remove this switching
                directOrBackDirection ? c2cFlow.getToCityId() : c2cFlow.getFromCityId(),
                c2cFlow.getNextGroupSize(),
                service);
        journey.setHeartbeatTime(world.getWorldTime());
        return journey;
    }

    public void waitForCheckin(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(EnumSet.of(
                        Journeys.Status.LookingForTickets,
                        Journeys.Status.JustArrived)
                .contains(journey.getStatus()));

        journey.setStatus(Journeys.Status.WaitingForCheckIn);
        journey.setHeartbeatTime(world.getWorldTime());
    }

    public void board(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.WaitingForBoarding);

        journey.setStatus(Journeys.Status.OnBoard);
    }

    public void tooLateToBoard(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(EnumSet.of(
                        Journeys.Status.WaitingForCheckIn,
                        Journeys.Status.WaitingForBoarding)
                .contains(journey.getStatus()));

        // todo ak1 'cancel journey safely' with removal all following tickets etc
        // todo ak1 'update stats'
        journey.setStatus(Journeys.Status.TooLateToBoard);
    }

    public void scheduleDeboardingForAllOnBoardJourneys(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Deboarding);

        world.journeys()
                .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(), Journeys.Status.OnBoard))
                .forEach(this::scheduleDeboardingAtRandomTime);
    }

    private void scheduleDeboardingAtRandomTime(final Journeys.Journey journey) {
        journey.setStatus(Journeys.Status.WaitingForDeboarding);
        journey.setHeartbeatTime(world.getWorldTime() + (int) (Math.random() * TransportFlightHelper.DEBOARDING_DURATION));
    }
}
