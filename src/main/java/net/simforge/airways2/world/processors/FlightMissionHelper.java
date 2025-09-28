package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.AircraftPerformanceData;
import net.simforge.airways2.world.computations.FlightTimeline;
import net.simforge.airways2.world.computations.SimpleFlight;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.commons.misc.Geo;

import java.util.Optional;

import static net.simforge.airways2.world.Time.fromLdt;
import static net.simforge.airways2.world.datamodel.EventsToProcess.Type.PilotOnDuty;

public class FlightMissionHelper {

    public static FlightMissions.Mission scheduleDispatchedMission(
            final World world,
            final Aircrafts.Aircraft aircraft,
            final Airports.Airport departureAirport,
            final Airports.Airport destinationAirport,
            final int departureTime) {
        final AircraftTypes.AircraftType aircraftType = world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow();
        final AircraftPerformanceData performanceData = AircraftPerformanceData.getData(aircraftType.getIcao());
        final SimpleFlight simpleFlight = SimpleFlight.forRoute(departureAirport.getCoords(), destinationAirport.getCoords(), performanceData);
        final FlightTimeline flightTimeline = FlightTimeline.byFlyingTime(simpleFlight.getTotalTime());
        flightTimeline.scheduleDepartureTime(Time.toLdt(departureTime));
        final int arrivalTime = Time.fromLdt(flightTimeline.getBlocksOn().getScheduledTime());

        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(
                aircraft,
                departureAirport,
                destinationAirport,
                departureTime,
                arrivalTime);

        world.eventsToProcess().sendEvent(
                PilotOnDuty,
                mission.getId(),
                fromLdt(flightTimeline.getStart().getScheduledTime()));
        return mission;
    }

    public static FlightMissions.Mission scheduleDispatchedMissionFromCurrentLocationAirport(
            final World world,
            final Aircrafts.Aircraft aircraft,
            final Airports.Airport destinationAirport,
            final int departureTime) {
        return scheduleDispatchedMission(
                world,
                aircraft,
                world.airports().byId(aircraft.getLocationAirportId()).orElseThrow(),
                destinationAirport,
                departureTime);
    }

    public static boolean isFinishedOrCancelledOrEmpty(final Optional<FlightMissions.Mission> mission) {
        return mission.isEmpty()
                || mission.get().getStatus() == FlightMissions.Status.Finished
                || mission.get().getStatus() == FlightMissions.Status.Cancelled;
    }

    public static String formatRoute(final World world, final int flightMissionId) {
        final FlightMissions.Mission mission = world.flightMissions().byId(flightMissionId).orElseThrow();
        final Airports.Airport from = world.airports().byId(mission.getDepartureAirportId()).orElseThrow();
        final Airports.Airport to = world.airports().byId(mission.getDestinationAirportId()).orElseThrow();
        return from.getIcao() + " - " + to.getIcao();
    }

    public static String formatRouteOrNull(final World world, final int flightMissionId) {
        final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(flightMissionId);
        if (mission.isEmpty()) {
            return null;
        }
        return formatRoute(world, flightMissionId);
    }

    public static float calculateHeading(final World world, final int flightMissionId) {
        final FlightMissions.Mission mission = world.flightMissions().byId(flightMissionId).orElseThrow();
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        final Airports.Airport to = world.airports().byId(mission.getDestinationAirportId()).orElseThrow();
        return (float) Geo.bearing(aircraft.getLocationCoords(), to.getCoords());
    }

    public static int calcPreflightStartTime(final FlightMissions.Mission flight) {
        return flight.getPlannedDepartureWorldTime() - FlightTimeline.START_TO_BLOCKS_OFF_MINUTES * 60;
    }
}
