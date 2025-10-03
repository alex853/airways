package net.simforge.airways2.world.processors;

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

    public Journeys.Journey create(final City2CityFlows.Flow c2cFlow, final boolean directOrBackDirection) {
        checkNotNull(c2cFlow);
        final Journeys.Journey journey = world.journeys().create(
                Journeys.Status.LookingForTickets,
                directOrBackDirection ? c2cFlow.getFromCityId() : c2cFlow.getToCityId(),
                directOrBackDirection ? c2cFlow.getToCityId() : c2cFlow.getFromCityId(),
                c2cFlow.getNextGroupSize());
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

    public void scheduleDeboardingForAllOnBoardJourneys(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Deboarding);

        world.journeys().filter(j -> j.getStatus() == Journeys.Status.OnBoard
                        && j.getTransportFlight1Id() == transportFlight.getId())
                .forEach(this::scheduleDeboardingAtRandomTime);
    }

    private void scheduleDeboardingAtRandomTime(final Journeys.Journey journey) {
        journey.setStatus(Journeys.Status.WaitingForDeboarding);
        journey.setHeartbeatTime(world.getWorldTime() + (int) (Math.random() * TransportFlightHelper.DEBOARDING_DURATION));
    }
}
