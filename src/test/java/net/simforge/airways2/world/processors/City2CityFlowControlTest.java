package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import net.simforge.airways2.world.datamodel.Journeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The update of the success rate of the flows of two cities: the flow that does not exist is created inactive to keep the success rate, a city has no flow
 * to itself.
 */
class City2CityFlowControlTest {
    private World world;
    private Cities.City alpha;
    private Cities.City beta;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final int countryId = world.countries().create("TT", "Testland").getId();
        alpha = world.cities().create(countryId, "Alpha", 10, 20, 5_000_000);
        beta = world.cities().create(countryId, "Beta", 12.5, 20, 5_000_000);
    }

    private Journeys.Journey journey(final Cities.City from, final Cities.City to) {
        return world.journeys().create(Journeys.Status.LookingForTickets, from.getId(), to.getId(), 1, CabinLayout.Service.Y);
    }

    @Test
    void aMissingFlowIsCreatedInactiveInBothDirectionsAndItsSuccessRateGrows() {
        assertTrue(world.city2cityFlows().getFromCityIdToCityId(alpha.getId(), beta.getId()).isEmpty());

        world.c2cFlowControl().updateSuccessRate(journey(alpha, beta), 0.1f);

        for (final int[] pair : new int[][] {{alpha.getId(), beta.getId()}, {beta.getId(), alpha.getId()}}) {
            final City2CityFlows.Flow flow = world.city2cityFlows().getFromCityIdToCityId(pair[0], pair[1]).orElseThrow();
            assertFalse(flow.isActive(), "the created flow is inactive");
            assertEquals(0, flow.getHeartbeatTime(), "an inactive flow is not in the heartbeat index");
            // +0.1% of the way to 1.0 from the starting rate
            final float expected = CityFlowHelper.STARTING_SUCCESS_RATE + (1 - CityFlowHelper.STARTING_SUCCESS_RATE) * 0.001f;
            assertEquals(expected, flow.getSuccessRate(), 0.0001f);
        }
    }

    @Test
    void anExistingFlowIsUpdatedAndNothingIsCreated() {
        final City2CityFlows.Flow forward = world.city2cityFlows().createInactive(alpha.getId(), beta.getId());
        forward.setSuccessRate(0.9f);
        final long flows = world.city2cityFlows().all().count();

        world.c2cFlowControl().updateSuccessRate(journey(alpha, beta), 0.1f);

        assertEquals(0.9001f, forward.getSuccessRate(), 0.00002f);
        // the other direction did not exist, it is created
        assertEquals(flows + 1, world.city2cityFlows().all().count());
        assertTrue(world.city2cityFlows().getFromCityIdToCityId(beta.getId(), alpha.getId()).isPresent());
    }

    @Test
    void aCityHasNoFlowToItself() {
        world.c2cFlowControl().updateSuccessRate(journey(alpha, alpha), 0.1f);

        assertEquals(0, world.city2cityFlows().all().count());
    }

    @Test
    void anInactiveFlowWithTheGrownSuccessRateIsKeptByTheRedistribution() {
        // the pair is thick enough to pass the redistribution, the success rate the flow collected stays, the starting one is not put again
        final City2CityFlows.Flow flow = world.city2cityFlows().createInactive(alpha.getId(), beta.getId());
        flow.setSuccessRate(0.8f);
        world.cityFlows().createMissingCityFlows();

        CityFlowsProcessor.process(world);
        CityFlowsProcessor.process(world);

        assertTrue(flow.isActive());
        assertEquals(0.8f, flow.getSuccessRate(), 0.0001f);
    }

    @Test
    void aGrownSuccessRateDoesNotMakeAThinPairActive() {
        // the success rate does not open the flow: the pair has to pass the flow units first (two small cities far from each other do not)
        final int countryId = alpha.getCountryId();
        final Cities.City smallA = world.cities().create(countryId, "SmallA", 10, 20, 50_000);
        final Cities.City smallB = world.cities().create(countryId, "SmallB", 40, 60, 50_000);
        final City2CityFlows.Flow flow = world.city2cityFlows().createInactive(smallA.getId(), smallB.getId());
        flow.setSuccessRate(0.99f);
        world.cityFlows().createMissingCityFlows();

        CityFlowsProcessor.process(world);
        CityFlowsProcessor.process(world);
        CityFlowsProcessor.process(world);
        CityFlowsProcessor.process(world);

        assertFalse(flow.isActive());
    }
}
