package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.Formatting;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

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
        final int toAirportId = mission.getActualLandingAirportId() > 0 ? mission.getActualLandingAirportId() : mission.getDestinationAirportId();

        final Collection<Integer> fromCityIds = world.airport2city().allByAirportId(fromAirportId).map(Airport2City.Link::getCityId).toList();
        final Collection<Integer> toCityIds = world.airport2city().allByAirportId(toAirportId).map(Airport2City.Link::getCityId).toList();
        // todo ak2 check for intersection? what to do in case of intersection?

        fromCityIds.forEach(
                fromCityId -> toCityIds.forEach(
                        toCityId -> updateCity2CityFlowSuccessRateBothDirections(fromCityId, toCityId, deltaPercents)));
    }

    private void updateCity2CityFlowSuccessRateBothDirections(final int fromCityId, final int toCityId, final float deltaPercents) {
        final String forward = updateCity2CityFlowSuccessRateOneDirection(fromCityId, toCityId, deltaPercents);
        final String backward = updateCity2CityFlowSuccessRateOneDirection(toCityId, fromCityId, deltaPercents);

        // one line for both directions: "s/r d0.025%: #94/4 Istan-Paris n 0.6550698, #4/94 Paris-Istan n 0.6550698"
        final List<String> parts = Stream.of(forward, backward).filter(Objects::nonNull).toList();
        if (!parts.isEmpty()) {
            log.info("s/r d{}%: {}", deltaPercents, String.join(", ", parts));
        }
    }

    // returns the part of the log line, null if there is no flow
    private String updateCity2CityFlowSuccessRateOneDirection(final int fromCityId, final int toCityId, final float deltaPercents) {
        final Optional<City2CityFlows.Flow> flow = world.city2cityFlows().getFromCityIdToCityId(fromCityId, toCityId);

        final String fromCity = Formatting.shortCity(world.cities().byId(fromCityId).orElseThrow().getName());
        final String toCity = Formatting.shortCity(world.cities().byId(toCityId).orElseThrow().getName());

        if (flow.isEmpty()) {
            log.warn("c2c #{}/{} {}-{} - s/r - NO FLOW FOUND", fromCityId, toCityId, fromCity, toCity);
            return null;
        }

        final float originalSuccessRate = flow.get().getSuccessRate();
        final float successRateToItsLimit = deltaPercents > 0 ? 1.0f - originalSuccessRate : originalSuccessRate;
        final float successRateDelta = successRateToItsLimit * (deltaPercents/100);
        final float newSuccessRate = originalSuccessRate + successRateDelta;
        flow.get().setSuccessRate(newSuccessRate);

        return "#" + fromCityId + "/" + toCityId + " " + fromCity + "-" + toCity + " n " + Formatting.df7.format(flow.get().getSuccessRate());
    }
}
