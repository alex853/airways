package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.Formatting;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import net.simforge.airways2.world.datamodel.Journeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class City2CityFlowControl {
    private static final Logger log = LoggerFactory.getLogger(City2CityFlowControl.class);

    private final World world;

    public City2CityFlowControl(final World world) {
        this.world = world;
    }

    public void updateSuccessRate(final Journeys.Journey journey, final float deltaPercents) {
        updateCity2CityFlowSuccessRateOneDirection(journey.getFromCityId(), journey.getToCityId(), deltaPercents);
        updateCity2CityFlowSuccessRateOneDirection(journey.getToCityId(), journey.getFromCityId(), deltaPercents);
    }

    private void updateCity2CityFlowSuccessRateOneDirection(final int fromCityId, final int toCityId, final float deltaPercents) {
        final Optional<City2CityFlows.Flow> flow = world.city2cityFlows().getFromCityIdToCityId(fromCityId, toCityId);
        if (flow.isEmpty()) {
            log.warn("update c2c flow success rate - {}->{} - no flow found", fromCityId, toCityId);
            return;
        }

        final float originalSuccessRate = flow.get().getSuccessRate();
        final float successRateToItsLimit = deltaPercents > 0 ? 1.0f - originalSuccessRate : originalSuccessRate;
        final float successRateDelta = successRateToItsLimit * (deltaPercents/100);
        final float newSuccessRate = originalSuccessRate + successRateDelta;
        flow.get().setSuccessRate(newSuccessRate);

        log.info("update c2c flow success rate - {}->{} - success rate update {}% - src {}, delta {}, new {} (stored {})", fromCityId, toCityId,
                deltaPercents,
                Formatting.df7z.format(originalSuccessRate),
                Formatting.df7z.format(successRateDelta),
                Formatting.df7z.format(newSuccessRate),
                Formatting.df7z.format(flow.get().getSuccessRate()));
    }
}
