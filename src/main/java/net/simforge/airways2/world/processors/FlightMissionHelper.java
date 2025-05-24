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

import static net.simforge.airways2.world.Time.fromLdt;
import static net.simforge.airways2.world.datamodel.EventsToProcess.Type.PilotOnDuty;

public class FlightMissionHelper {

    public static FlightMissions.Mission scheduleFlightMission(
            final World world,
            final Aircrafts.Aircraft aircraft,
            final Airports.Airport destinationAirport,
            final int departureTime) {
        final Airports.Airport locationAirport = world.airports().byId(aircraft.getLocationAirportId()).orElseThrow();

        final AircraftPerformanceData performanceData = AircraftPerformanceDataHelper.getData();
        final SimpleFlight simpleFlight = SimpleFlight.forRoute(locationAirport.getCoords(), destinationAirport.getCoords(), performanceData);
        final FlightTimeline flightTimeline = FlightTimeline.byFlyingTime(simpleFlight.getTotalTime());
        flightTimeline.scheduleDepartureTime(Time.toLdt(departureTime));
        final int arrivalTime = Time.fromLdt(flightTimeline.getBlocksOn().getScheduledTime());

        final FlightMissions.Mission mission = world.flightMissions().createPlannedMission(
                aircraft,
                locationAirport,
                destinationAirport,
                departureTime,
                arrivalTime);

        world.eventsToProcess().sendEvent(
                PilotOnDuty,
                mission.getId(),
                fromLdt(flightTimeline.getStart().getScheduledTime()));
        return mission;
    }

}
