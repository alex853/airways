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

    public static boolean isFinishedOrCancelled(FlightMissions.Mission mission) {
        return mission.getStatus() == FlightMissions.Status.Finished
                || mission.getStatus() == FlightMissions.Status.Cancelled;
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

    /**
     * The earliest time of a manual landing: three quarters of the planned flying time after the takeoff. The planned time of the mission is from blocks off to
     * blocks on, it has the taxi before the takeoff and after the landing, so they are taken off: the flying time is what is left. (Three quarters of the whole
     * block time would be later than the planned landing for a flight shorter than an hour.)
     */
    public static int calcEarliestAllowedLandingTime(final FlightMissions.Mission flight) {
        final int plannedBlockTime = flight.getPlannedArrivalWorldTime() - flight.getPlannedDepartureWorldTime();
        final int plannedTaxiTime = (FlightTimeline.BLOCKS_OFF_TO_TAKEOFF_MINUTES + FlightTimeline.LANDING_TO_BLOCKS_ON_MINUTES) * 60;
        final int plannedFlyingTime = Math.max(plannedBlockTime - plannedTaxiTime, 0);
        final int minDuration = (int) Math.round(plannedFlyingTime * 0.75);
        return flight.getActualTakeoffWorldTime() + minDuration;
    }
}
