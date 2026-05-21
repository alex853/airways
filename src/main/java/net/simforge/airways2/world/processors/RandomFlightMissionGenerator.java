package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.EventLog;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

public class RandomFlightMissionGenerator {
    private static final Logger log = LoggerFactory.getLogger(RandomFlightMissionGenerator.class);
    private static long lastExecution;

    public static void process(final World world) {
        if (System.currentTimeMillis() - lastExecution < 3600000) {
            return;
        }
        lastExecution = System.currentTimeMillis();

        final Collection<Aircrafts.Aircraft> idleAircraft = world.aircrafts()
                .byAircraftOperatorIdAndIdleAndParkedAtAirport(Aircrafts.NO_AIRCRAFT_OPERATOR_ID)
                .toList();
        final List<Aircrafts.Aircraft> aircraftWithoutMission = idleAircraft.stream()
                .filter(aircraft -> FlightMissionHelper.isFinishedOrCancelledOrEmpty(world.flightMissions().theLatestMissionByAircraftId(aircraft)))
                .toList();

        if (aircraftWithoutMission.isEmpty()) {
            log.info("there is no aircraft for random mission generation");
            return;
        }

        final Aircrafts.Aircraft aircraft = aircraftWithoutMission.get(0);
        final Airports.Airport destinationAirport = selectRandomDestination(world, aircraft);
        final int departureTime = world.getWorldTime() + 4 * Time.ONE_HOUR;

        final FlightMissions.Mission mission = FlightMissionHelper.scheduleDispatchedMissionFromCurrentLocationAirport(world, aircraft, destinationAirport, departureTime);
        world.transportFlightControl().createTransportFlight(mission); // todo ak1 apply fixes from shadowjet code

        world.log(EventLog.EventType.FlightDispatchedRandomly, EventLog.pilotId(0), mission, aircraft);
        log.info("f/m #{} - flight dispatched randomly, aircraft {}", mission.getId(), aircraft.getRegNo());
    }

    private static Airports.Airport selectRandomDestination(World world, Aircrafts.Aircraft aircraft) {
        final int locationAirportId = aircraft.getLocationAirportId();
        final Airports.Airport locationAirport = world.airports().byId(locationAirportId).orElseThrow();
        final List<Airports.Airport> possibleDestinations = world.airports().all()
                .filter(airport -> !airport.isExcluded())
                .filter(airport -> airport.getId() != locationAirportId
                        && Geo.distance(locationAirport.getCoords(), airport.getCoords()) >= 100
                        && Geo.distance(locationAirport.getCoords(), airport.getCoords()) <= 2000) // A320 practical range
                .toList();
        return possibleDestinations.get((int) (possibleDestinations.size() * Math.random()));
    }
}
