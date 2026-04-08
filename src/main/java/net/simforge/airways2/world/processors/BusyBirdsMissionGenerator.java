package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// constantly running process which finds some, few, not too many W or J journeys in looking for tickets status
// and pick them up - mark them as 'special processing'
// save info into dedicated storage and sets expiration date - lets start from 24 hours
// that means 'mission', or 'job', or 'order'
// when mission expires without being picked up by any pilot, the journey is released back to 'normal processing'

// the screen with available missions shows list of missions, from-to airport,
// offered aircraft and its proposed full itinerary, including total flight time,
// plus mission cost, and pilot's paycheck

// the pilot can take the mission, this means pilot commits to make all the flights for the mission within 48 hours window
// all the flight missions are created, pilot can specify and change departure time of any mission
// departure time of passenger flight influences to journey's checkin time
// checkin time and boarding time can be shortened due to small airplane size
// comparison of planned and actual departure & arrival times can be used as a measure for kind of bonus or something else

// "back to base" flight can be omitted *** this will be reviewed later...... there is an obligation to fly back to base
// however if someone flies two missions in a row, this "back to base" flight and then positioning flight is weird
// on the other hand, if someone drops, leaves plane unattended for days or weeks, this should be avoided and punished

// so, the mission has execution time window (48 hours), the airplane should be returned back to the base by end
// of the window, if it is not returned - cancel the mission, pay for flown and charge the fine for non-flown leg
// on the other hand, if the "passenger" flight is already flown, this aircraft can be assigned to other missions,
// including by other pilots
// if such aircraft is assigned to another mission, the previous mission is considered finished and payments can be done

// mission cancellation.....
public class BusyBirdsMissionGenerator {
    private static final Logger log = LoggerFactory.getLogger(BusyBirdsMissionGenerator.class);
    private static long lastExecution;

    public static void process(final World world) {
        if (System.currentTimeMillis() - lastExecution < 60000) {
            return;
        }
        lastExecution = System.currentTimeMillis();

        log.info("processing journeys");

        // todo ak2 select only those which in looking for tickets
        world.journeys().filter(world.journeys().bySpecialProcessing()).forEach(j -> processJourney(world, j));
    }

    private static void processJourney(final World world, final Journeys.Journey journey) {
        log.info("journey {} -> {}, pax {}", journey.getFromCityId(), journey.getToCityId(), journey.getGroupSize());

        final AircraftOperators.AircraftOperator busyBirdsOperator = world.aircraftOperators().byIata(World25.BusyBirdsIata).orElseThrow();

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
            log.info("FLIGHT - reposition - {}, {} -> {}", aircraft.get().getRegNo(), world.airports().getIcao(aircraft.get().getLocationAirportId()), fromAirport.get().getIcao());
        }

        log.info("FLIGHT - REVENUE    - {}, {} -> {}", aircraft.get().getRegNo(), fromAirport.get().getIcao(), toAirport.get().getIcao());

        final Optional<Airports.Airport> baseAirport = findNearestBaseAirport(world, busyBirdsOperator, toAirport.get());
        if (baseAirport.isEmpty()) {
            log.warn("unable to find suitable 'base' airport");
            return;
        }

        final boolean needFerryFlightToBaseAirport = toAirport.get().getId() != baseAirport.get().getId();
        if (needFerryFlightToBaseAirport) {
            log.info("FLIGHT - reposition - {}, {} -> {}", aircraft.get().getRegNo(), toAirport.get().getIcao(), baseAirport.get().getIcao());
        }
    }

    // todo ak2 take into account aircraft max range

    private static Optional<Aircrafts.Aircraft> findNearestSuitableAircraft(final World world, final AircraftOperators.AircraftOperator aircraftOperator, final Airports.Airport airport) {
        return world.aircrafts()
                .byAircraftOperatorId(aircraftOperator.getId())
                .filter(Aircrafts::isIdleAndParkedAtAirport)
                // todo ak2 check aircraft seats vs journey size
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
            return Optional.of(preferredBusinessTerminals.get((int) (Math.random()*preferredBusinessTerminals.size())));
        }

        final List<Airports.Airport> anyBusinessTerminals = airports.stream()
                .filter(a -> world.airportFacilities().hasFacility(a, AirportFacilities.Type.BusinessAviationTerminal))
                .toList();
        if (!anyBusinessTerminals.isEmpty()) {
            return Optional.of(anyBusinessTerminals.get((int) (Math.random()*anyBusinessTerminals.size())));
        }

        return Optional.of(airports.get((int) (Math.random()*airports.size())));
    }

    private static Optional<Airports.Airport> findNearestBaseAirport(final World world, final AircraftOperators.AircraftOperator aircraftOperator, final Airports.Airport airport) {
        return world.airportFacilities().by(aircraftOperator, AirportFacilities.Type.BaseAirport)
                .map(f -> world.airports().byId(f.getAirportId()).orElseThrow())
                .min(Comparator.comparingDouble(a -> Geo.distance(a.getCoords(), airport.getCoords())));
    }
}
