package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.Formatting;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;

public class City2CityFlowControl {
    private static final Logger log = LoggerFactory.getLogger(City2CityFlowControl.class);

    private final World world;

    public City2CityFlowControl(final World world) {
        this.world = world;
    }

    public void updateSuccessRate(final Journeys.Journey journey, final float deltaPercents) {
        updateCity2CityFlowSuccessRateBothDirections(journey.getFromCityId(), journey.getToCityId(), deltaPercents);
    }

    public void updateSuccessRate(final TransportFlights.Flight transportFlight, final float deltaPercents) {
        final FlightMissions.Mission mission = world.flightMissions().byId(transportFlight.getFlightMissionId()).orElseThrow();

        final int fromAirportId = mission.getDepartureAirportId();
        final int toAirportId = mission.getDestinationAirportId(); // todo ak1 actual landing airport?

        final Collection<Integer> fromCityIds = world.airport2city().allByAirportId(fromAirportId).stream().map(Airport2City.Link::getCityId).toList();
        final Collection<Integer> toCityIds = world.airport2city().allByAirportId(toAirportId).stream().map(Airport2City.Link::getCityId).toList();
        // todo ak1 check for intersection? what to do in case of intersection?

        fromCityIds.forEach(fromCityId -> toCityIds.forEach(toCityId -> updateCity2CityFlowSuccessRateBothDirections(fromCityId, toCityId, deltaPercents)));
    }

    private void updateCity2CityFlowSuccessRateBothDirections(final int fromCityId, final int toCityId, final float deltaPercents) {
        updateCity2CityFlowSuccessRateOneDirection(fromCityId, toCityId, deltaPercents);
        updateCity2CityFlowSuccessRateOneDirection(toCityId, fromCityId, deltaPercents);
    }

    private void updateCity2CityFlowSuccessRateOneDirection(final int fromCityId, final int toCityId, final float deltaPercents) {
        final Optional<City2CityFlows.Flow> flow = world.city2cityFlows().getFromCityIdToCityId(fromCityId, toCityId);

        final String fromCity = world.cities().byId(fromCityId).orElseThrow().getName();
        final String toCity = world.cities().byId(toCityId).orElseThrow().getName();

        if (flow.isEmpty()) {
            log.warn("update c2c flow success rate [{} -> {}] no flow found", fromCity, toCity);
            return;
        }

        final float originalSuccessRate = flow.get().getSuccessRate();
        final float successRateToItsLimit = deltaPercents > 0 ? 1.0f - originalSuccessRate : originalSuccessRate;
        final float successRateDelta = successRateToItsLimit * (deltaPercents/100);
        final float newSuccessRate = originalSuccessRate + successRateDelta;
        flow.get().setSuccessRate(newSuccessRate);

        log.info("update c2c flow success rate [{} -> {}] success rate update {}% - src {}, delta {}, new {} (stored {})", fromCity, toCity,
                deltaPercents,
                Formatting.df7z.format(originalSuccessRate),
                Formatting.df7z.format(successRateDelta),
                Formatting.df7z.format(newSuccessRate),
                Formatting.df7z.format(flow.get().getSuccessRate()));
    }
}
