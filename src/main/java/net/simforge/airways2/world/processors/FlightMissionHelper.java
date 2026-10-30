package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
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

import java.time.Duration;
import java.time.LocalDateTime;
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

        // the preflight starts a fixed time before the planned start of the boarding, which is by the cabin layout of the type (a flight without passengers is not known yet,
        // it is taken as a flight with them)
        world.eventsToProcess().sendEvent(
                PilotOnDuty,
                mission.getId(),
                calcPreflightStartTime(mission, world.transportFlightControl().cabinLayoutOfType(aircraftType.getIcao())));
        return mission;
    }

    /**
     * The planned duration of a flight on the route: from blocks off to blocks on, that is the flying time by the performance of the type and the taxi
     * before the takeoff and after the landing. The same calculation as the one which gives the planned arrival of a dispatched mission
     * ({@link #scheduleDispatchedMission}), so the arrival which is told before the booking is the arrival of the mission.
     *
     * @throws java.util.NoSuchElementException if there are no performance data of the type
     */
    public static Duration calculateFlightDuration(
            final String aircraftTypeIcao,
            final Airports.Airport departureAirport,
            final Airports.Airport destinationAirport) {
        final AircraftPerformanceData performanceData = AircraftPerformanceData.getData(aircraftTypeIcao);
        final SimpleFlight simpleFlight = SimpleFlight.forRoute(departureAirport.getCoords(), destinationAirport.getCoords(), performanceData);
        final FlightTimeline flightTimeline = FlightTimeline.byFlyingTime(simpleFlight.getTotalTime());
        // the duration does not depend on the time of the day, any time is good as the departure
        final LocalDateTime departureTime = Time.toLdt(Time.START_TIME_EPOCH_SECONDS);
        flightTimeline.scheduleDepartureTime(departureTime);
        // a mission keeps its times in whole minutes (the minutes are cut off), so the duration is in whole minutes too: with a departure on a whole minute it is
        // the duration of the mission
        return Duration.ofMinutes(Duration.between(departureTime, flightTimeline.getBlocksOn().getScheduledTime()).toMinutes());
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

    /**
     * How long before the planned start of the boarding the preflight starts (the pilot is on duty, the flight can be started): a fixed time (decided 2026-10-30). It was a
     * fixed time before the planned departure (FlightTimeline.START_TO_BLOCKS_OFF_MINUTES, 30 minutes), which did not fit the aircraft with a long boarding.
     */
    public static final int PREFLIGHT_TO_BOARDING_MINUTES = 30;

    /**
     * The start of the preflight by the cabin layout of the flight: the planned start of the boarding minus 30 minutes. For an A320 with 168 seats the boarding is 17 minutes
     * and ends 10 minutes before the departure, so the preflight starts 57 minutes before it.
     */
    public static int calcPreflightStartTime(final FlightMissions.Mission flight, final CabinLayout cabinLayout) {
        return TransportFlightHelper.calcBoardingStartTime(flight, cabinLayout) - PREFLIGHT_TO_BOARDING_MINUTES * 60;
    }

    /**
     * The start of the preflight of a flight in the world: by the cabin layout of its transport flight; a flight without passengers has no boarding, its preflight starts
     * 30 minutes before the planned departure as it did before.
     */
    public static int calcPreflightStartTime(final World world, final FlightMissions.Mission flight) {
        return world.transportFlights().byFlightMissionId(flight.getId())
                .map(tf -> calcPreflightStartTime(flight, tf.getTotalTickets()))
                .orElseGet(() -> flight.getPlannedDepartureWorldTime() - FlightTimeline.START_TO_BLOCKS_OFF_MINUTES * 60);
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
