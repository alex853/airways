package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import net.simforge.airways2.world.datamodel.Journeys;

public class JourneyControl {
    private final World world;

    private JourneyControl(World world) {
        this.world = world;
    }

    public static JourneyControl instance(final World world) {
        return new JourneyControl(world);
    }

    public Journeys.Journey create(final City2CityFlows.Flow c2cFlow, final boolean directOrBackDirection) {
        return world.journeys().create(
                Journeys.Status.LookingForTickets,
                directOrBackDirection ? c2cFlow.getFromCityId() : c2cFlow.getToCityId(),
                directOrBackDirection ? c2cFlow.getToCityId() : c2cFlow.getFromCityId(),
                c2cFlow.getNextGroupSize());
    }
}
