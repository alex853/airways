package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.AircraftPerformanceData;
import net.simforge.airways2.world.computations.AircraftPerformanceDataHelper;
import net.simforge.airways2.world.computations.FlightTimeline;
import net.simforge.airways2.world.computations.SimpleFlight;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.commons.misc.Geo;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

import static net.simforge.airways2.world.datamodel.EventsToProcess.Type.PilotOnDuty;

public class RandomFlightMissionGenerator {
    public static void process(final World world, final int worldTime) {
        final Collection<Aircrafts.Aircraft> idleAircraft = world.aircrafts().allIdleAndParkedAtAirport();

        final List<Aircrafts.Aircraft> aircraftWithoutMission = idleAircraft.stream()
                .filter(aircraft -> FlightMissions.isFinishedOrCancelledOrEmpty(world.flightMissions().theLatestMissionByAircraftId(aircraft)))
                .toList();

        aircraftWithoutMission.forEach(aircraft -> {
            final int locationAirportId = aircraft.getLocationAirportId();
            final Airports.Airport locationAirport = world.airports().byId(locationAirportId).orElseThrow();
            final List<Airports.Airport> possibleDestinations = world.airports().all().stream()
                    .filter(airport -> airport.getId() != locationAirportId
                            && Geo.distance(locationAirport.getCoords(), airport.getCoords()) >= 100)
                    .toList();
            final Airports.Airport destinationAirport = possibleDestinations.get((int) (possibleDestinations.size() * Math.random()));

            final int departureTime = worldTime + Time.ONE_HOUR;

            final AircraftPerformanceData performanceData = AircraftPerformanceDataHelper.getData();
            final SimpleFlight simpleFlight = SimpleFlight.forRoute(locationAirport.getCoords(), destinationAirport.getCoords(), performanceData);
            final FlightTimeline flightTimeline = FlightTimeline.byFlyingTime(simpleFlight.getTotalTime());
            flightTimeline.scheduleDepartureTime(LocalDateTime.ofEpochSecond(departureTime, 0, ZoneOffset.UTC)); // todo ak0 Time.fromLtd?
            final int arrivalTime = (int) flightTimeline.getBlocksOn().getScheduledTime().toEpochSecond(ZoneOffset.UTC); // todo ak0 Time.fromLtd?

            final FlightMissions.Mission mission = world.flightMissions().createPlannedMission(
                    aircraft,
                    locationAirport,
                    destinationAirport,
                    departureTime,
                    arrivalTime);

            world.eventsToProcess().sendEvent(
                    PilotOnDuty,
                    mission.getId(),
                    Time.fromLdt(flightTimeline.getStart().getScheduledTime()));
        });
    }

}
