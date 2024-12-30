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
import java.util.ArrayList;
import java.util.List;

import static net.simforge.airways2.world.datamodel.Events.Type.PilotOnDuty;

public class RandomFlightMissionGenerator {
    public static void process(final World world, final int worldTime) {
        final List<Aircrafts.Aircraft> idleAircraft = world.aircrafts().all().stream()
                .filter(aircraft -> aircraft.getOperationalStatus() == Aircrafts.OperationalStatus.Idle
                        && aircraft.getLocationStatus() == Aircrafts.LocationStatus.ParkedAtAirport)
                .toList();

        final List<Aircrafts.Aircraft> aircraftWithoutMission = idleAircraft.stream()
                .filter(aircraft -> {
                    final List<FlightMissions.Mission> allMissions = new ArrayList<>(world.flightMissions().allForAircraft(aircraft));
                    allMissions.sort(FlightMissions.sortByDepartureTimeFromFutureToPast);
                    if (allMissions.isEmpty()) {
                        return true;
                    }
                    final FlightMissions.Mission lastMission = allMissions.get(0);
                    return lastMission.getStatus() == FlightMissions.Status.Finished
                        || lastMission.getStatus() == FlightMissions.Status.Cancelled;
                })
                .toList();

        aircraftWithoutMission.forEach(aircraft -> {
            final int locationAirportId = aircraft.getLocationAirportId();
            final Airports.Airport locationAirport = world.airports().byId(locationAirportId).orElseThrow();
            final List<Airports.Airport> possibleDestinations = world.airports().all().stream()
                    .filter(airport -> airport.getId() != locationAirportId
                            && Geo.distance(locationAirport.getCoords(), airport.getCoords()) >= 100)
                    .toList();
            final Airports.Airport destination = possibleDestinations.get((int) (possibleDestinations.size() * Math.random()));

            final int departureTime = worldTime + Time.ONE_HOUR;

            final AircraftPerformanceData performanceData = AircraftPerformanceDataHelper.getData();
            final SimpleFlight simpleFlight = SimpleFlight.forRoute(locationAirport.getCoords(), destination.getCoords(), performanceData);
            final FlightTimeline flightTimeline = FlightTimeline.byFlyingTime(simpleFlight.getTotalTime());
            flightTimeline.scheduleDepartureTime(LocalDateTime.ofEpochSecond(departureTime, 0, ZoneOffset.UTC)); // todo ak0 Time.fromLtd?
            final int arrivalTime = (int) flightTimeline.getBlocksOn().getScheduledTime().toEpochSecond(ZoneOffset.UTC); // todo ak0 Time.fromLtd?

            final FlightMissions.Mission mission = world.flightMissions().createPlannedMission(
                    aircraft,
                    locationAirport,
                    destination,
                    departureTime,
                    arrivalTime);

            world.events().sendEvent(
                    PilotOnDuty,
                    mission.getId(),
                    (int) flightTimeline.getStart().getScheduledTime().toEpochSecond(ZoneOffset.UTC)); // todo ak0 Time.fromLtd?
        });
    }
}
