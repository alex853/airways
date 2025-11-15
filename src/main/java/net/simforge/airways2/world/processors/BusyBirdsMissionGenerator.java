package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class BusyBirdsMissionGenerator {
    private static final Logger log = LoggerFactory.getLogger(BusyBirdsMissionGenerator.class);
    private static long lastExecution;

    public static void process(final World world) {
        if (LocalDateTime.now().getMinute() != 0) {
            return;
        }
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

        final List<Airports.Airport> fromCityAirports = world.airport2city().linksByCityId(journey.getFromCityId())
                .map(l -> world.airports().byId(l.getAirportId()).orElseThrow())
                .toList();
        final List<Airports.Airport> toCityAirports = world.airport2city().linksByCityId(journey.getToCityId())
                .map(l -> world.airports().byId(l.getAirportId()).orElseThrow())
                .toList();

        // todo ak is there base airport?
        // todo ak is there BB preferred business terminal?
        // todo ak is there any business terminal? choose any between them if there are many
        // todo ak is there any airport at all? choose any between them if there are many


        // todo ak business terminal flags!
        // todo ak check that airport list is not empty

        final List<Aircrafts.Aircraft> aircrafts = world.aircrafts()
                .filter(world.aircrafts().byAircraftOperatorId(busyBirdsOperator.getId()))
                .filter(Aircrafts::isIdleAndParkedAtAirport)
                // todo ak check aircraft seats vs journey size
                .toList();
        // todo ak check that aircraft list is not empty

        Aircrafts.Aircraft closestAircraft = null;
        double closestAircraftDistance = 1_000_000;
        for (final Aircrafts.Aircraft aircraft : aircrafts) {
            for (Airports.Airport airport : fromCityAirports) {
                final double distance = Geo.distance(aircraft.getLocationCoords(), airport.getCoords());
                if (distance >= closestAircraftDistance) {
                    continue;
                }
                closestAircraft = aircraft;
                closestAircraftDistance = distance;
            }
        }

        final Aircrafts.Aircraft aircraft = closestAircraft;
        if (!fromCityAirports.stream().map(Airports.Airport::getId).toList().contains(aircraft.getLocationAirportId())) {
            log.info("FLIGHT - FERRY   - {}, {} -> {}", aircraft.getRegNo(), world.airports().getIcao(aircraft.getLocationAirportId()), );
        }

        log.info("FLIGHT - REVENUE - {}, {} -> {}");
    }
}
