package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class BusyBirdsMissionGenerator {
    private static final Logger log = LoggerFactory.getLogger(BusyBirdsMissionGenerator.class);
    private static long lastExecution;

    public static void process(final World world) {
        if (System.currentTimeMillis() - lastExecution < 600000) {
            return;
        }
        lastExecution = System.currentTimeMillis();

        // todo ak select only those which in looking for tickets
        final Optional<Journeys.Journey> first = world.journeys().filter(world.journeys().bySpecialProcessing()).findFirst();
        if (first.isEmpty()) {
            return;
        }

        final AircraftOperators.AircraftOperator busyBirdsOperator = world.aircraftOperators().byIata(World25.BusyBirdsIata).orElseThrow();

        final Journeys.Journey journey = first.get();

        final Optional<Airports.Airport> fromAirport = chooseAirport(world,
                busyBirdsOperator,
                world.airport2city().linksByCityId(journey.getFromCityId())
                        .map(l -> world.airports().byId(l.getAirportId()).orElseThrow())
                        .toList());
        final Optional<Airports.Airport> toAirport = chooseAirport(world,
                busyBirdsOperator,
                world.airport2city().linksByCityId(journey.getToCityId())
                        .map(l -> world.airports().byId(l.getAirportId()).orElseThrow())
                        .toList());

        if (fromAirport.isEmpty()) {
            log.warn("unable to find suitable 'from' airport");
            return;
        }

        if (toAirport.isEmpty()) {
            log.warn("unable to find suitable 'to' airport");
            return;
        }

        final Optional<Aircrafts.Aircraft> aircraft = findNearestSuitableAircraft(world, busyBirdsOperator, fromAirport.get());

        if (aircraft.isEmpty()) {
            log.warn("unable to find suitable aircraft");
            return;
        }

        final boolean needFerryFlightToDepartureAirport = aircraft.get().getLocationAirportId() != fromAirport.get().getId();

        if (needFerryFlightToDepartureAirport) {
            log.info("FLIGHT - FERRY   - {}, {} -> {}", aircraft.get().getRegNo(), world.airports().getIcao(aircraft.get().getLocationAirportId()), toAirport.get().getIcao());
        }

        log.info("FLIGHT - REVENUE - {}, {} -> {}", aircraft.get().getRegNo(), fromAirport.get().getIcao(), toAirport.get().getIcao());

        final Optional<Airports.Airport> baseAirport = findNearestBaseAirport(world, busyBirdsOperator, toAirport.get());
        if (baseAirport.isEmpty()) {
            log.warn("unable to find suitable 'base' airport");
            return;
        }

        final boolean needFerryFlightToBaseAirport = toAirport.get().getId() != baseAirport.get().getId();
        if (needFerryFlightToBaseAirport) {
            log.info("FLIGHT - FERRY   - {}, {} -> {}", aircraft.get().getRegNo(), toAirport.get().getIcao(), baseAirport.get().getIcao());
        }
    }

    // todo ak take into account aircraft max range

    private static Optional<Aircrafts.Aircraft> findNearestSuitableAircraft(final World world, final AircraftOperators.AircraftOperator aircraftOperator, final Airports.Airport airport) {
        return world.aircrafts()
                .byAircraftOperatorId(aircraftOperator.getId())
                .filter(Aircrafts::isIdleAndParkedAtAirport)
                // todo ak check aircraft seats vs journey size
                .min(Comparator.comparingDouble(a -> Geo.distance(a.getLocationCoords(), airport.getCoords())));
    }

    private static Optional<Airports.Airport> chooseAirport(final World world, final AircraftOperators.AircraftOperator aircraftOperator, final List<Airports.Airport> airports) {
        if (airports.isEmpty()) {
            return Optional.empty();
        }

        final Optional<Airports.Airport> baseAirport = airports.stream()
                .filter(a -> world.airportFacilities().hasFacility(a, aircraftOperator, AirportFacilities.Type.BaseAirport))
                .findFirst();
        if (baseAirport.isPresent()) {
            return baseAirport;
        }

        final List<Airports.Airport> preferredBusinessTerminals = airports.stream()
                .filter(a -> world.airportFacilities().hasFacility(a, aircraftOperator, AirportFacilities.Type.BusinessAviationTerminal))
                .toList();
        if (!preferredBusinessTerminals.isEmpty()) {
            return Optional.of(preferredBusinessTerminals.get(Tools.random(0, preferredBusinessTerminals.size())));
        }

        final List<Airports.Airport> anyBusinessTerminals = airports.stream()
                .filter(a -> world.airportFacilities().hasFacility(a, AirportFacilities.Type.BusinessAviationTerminal))
                .toList();
        if (!anyBusinessTerminals.isEmpty()) {
            return Optional.of(anyBusinessTerminals.get(Tools.random(0, anyBusinessTerminals.size())));
        }

        return Optional.of(airports.get(Tools.random(0, airports.size())));
    }

    private static Optional<Airports.Airport> findNearestBaseAirport(final World world, final AircraftOperators.AircraftOperator aircraftOperator, final Airports.Airport airport) {
        return world.airportFacilities().by(aircraftOperator, AirportFacilities.Type.BaseAirport)
                .map(f -> world.airports().byId(f.getAirportId()).orElseThrow())
                .min(Comparator.comparingDouble(a -> Geo.distance(a.getCoords(), airport.getCoords())));
    }
}
