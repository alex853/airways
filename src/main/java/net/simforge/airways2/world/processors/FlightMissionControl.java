package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;

public class FlightMissionControl {
    private static final Logger log = LoggerFactory.getLogger(FlightMissionControl.class);

    public static final int RESCHEDULE_SHIFT_STEP_MINUTES = 5;
    public static final int RESCHEDULE_MAX_SHIFT_MINUTES = 12 * 60;
    // boarding takes 10 mins and ends 10 mins before departure
    public static final int RESCHEDULE_MIN_DEPARTURE_RESERVE = 20 * Time.ONE_MINUTE;

    private final World world;
    private final Set<Integer> cancelledIdsScheduledForQuickRemoval = new TreeSet<>();

    public FlightMissionControl(final World world) {
        this.world = world;
    }

    private TransportFlightControl transportFlightControl() {
        return world.transportFlightControl();
    }

    public void switchToExternalCoordinatesMode(FlightMissions.Mission mission) {
        mission.setCoordinatesSource(FlightMissions.CoordinatesSource.TrackedViaTracker);
        log.info("f/m #{} - flight switched to external coordinates mode", mission.getId());
    }

    /**
     * The result of {@link #start}: the mission is started, or it is not and the reason says why. A failed start changes nothing,
     * the caller decides what to do: cancel the mission ({@link #cancelDispatched}), report an error to the user, etc.
     */
    public record StartResult(boolean started, String reason) {
        public static StartResult ok() {
            return new StartResult(true, null);
        }

        public static StartResult failed(final String reason) {
            return new StartResult(false, reason);
        }
    }

    /**
     * Starts the mission (Dispatched -> Preflight) if the mission and the aircraft are fine for that: the mission is
     * Dispatched, the aircraft is idle and parked at the departure airport. If not, nothing is changed and the result tells why.
     */
    public StartResult start(final FlightMissions.Mission mission) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

        final FlightMissions.Status actualStatus = mission.getStatus();
        if (actualStatus != FlightMissions.Status.Dispatched) {
            final String reason = String.format("flight actual state %s while expected %s", actualStatus, FlightMissions.Status.Dispatched);
            log.warn("f/m #{} - flight can not be started - {}", mission.getId(), reason);
            return StartResult.failed(reason);
        }

        if (aircraft.getOperationalStatus() != Aircrafts.OperationalStatus.Idle
                || aircraft.getLocationStatus() != Aircrafts.LocationStatus.ParkedAtAirport
                || aircraft.getLocationAirportId() != mission.getDepartureAirportId()) {
            final String reason = String.format("aircraft %s actual operational status %s, location status %s, location airport %s",
                    aircraft.getRegNo(), aircraft.getOperationalStatus(), aircraft.getLocationStatus(), aircraft.getLocationAirportId());
            log.warn("f/m #{} - flight can not be started - {}", mission.getId(), reason);
            return StartResult.failed(reason);
        }

        int worldTime = world.getWorldTime();
        mission.setStatus(FlightMissions.Status.Preflight);
        mission.setHeartbeatTime(worldTime + Time.TICK);
        aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Active);
        aircraft.setFlightMissionId(mission.getId());
        aircraft.setLastUpdated(worldTime);
        // todo ak3 pilot/pilots/cabin crew - set status

        world.log(EventLog.EventType.FlightStarted, EventLog.userId(mission.getUserId()), mission, aircraft, EventLog.airportId(mission.getDepartureAirportId()));
        log.info("f/m #{} - flight started and in Preflight status, aircraft {} is activated", mission.getId(), aircraft.getRegNo());
        return StartResult.ok();
    }

    /**
     * Cancels the mission which has not been started: the aircraft was not touched by it. The transport flight of the mission, if there is
     * one, is cancelled too and its journeys are returned to the cities where they were before.
     */
    public void cancelDispatched(final FlightMissions.Mission mission, final String reason) {
        checkArgument(mission.getStatus() == FlightMissions.Status.Dispatched, "only a dispatched mission can be cancelled this way");
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

        mission.setStatus(FlightMissions.Status.Cancelled);

        world.log(EventLog.EventType.FlightCancelled, EventLog.userId(mission.getUserId()), mission, aircraft);
        log.info("f/m #{} - flight cancelled - {}", mission.getId(), reason);

        transportFlightControl().cancelTransportFlightIfExists(mission);
    }

    public void cancelFlightAndReturnAircraftToDepartureAirport(final FlightMissions.Mission mission) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

        final FlightMissions.Status actualStatus = mission.getStatus();
        checkArgument(actualStatus == FlightMissions.Status.Preflight
                || actualStatus == FlightMissions.Status.Departure
                || actualStatus == FlightMissions.Status.Flying);

        mission.setStatus(FlightMissions.Status.Cancelled);

        final Airports.Airport departureAirport = world.airports().byId(mission.getDepartureAirportId()).orElseThrow();

        aircraft.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);
        aircraft.setLocationAirportId(mission.getDepartureAirportId());
        aircraft.setLocationLatitude(departureAirport.getLatitude());
        aircraft.setLocationLongitude(departureAirport.getLongitude());
        aircraft.setLocationAltitude(0);

        aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Idle);
        aircraft.setFlightMissionId(0);

        aircraft.setLastUpdated(world.getWorldTime());

        world.log(EventLog.EventType.FlightCancelled, EventLog.userId(mission.getUserId()), mission, aircraft);
        log.info("f/m #{} - flight cancelled from {}", mission.getId(), actualStatus);

        // The t/f of the mission is not touched here: the callers (PilotContext, cancel before takeoff and cancel from flying)
        // cancel it right after this call by ShadowJetLogic.cancelTransportFlightIfExists, which unloads the journeys
        // back to the departure city. It is covered by FlightWorkflowTest (cancellation and disconnect tests).
    }

    // Checks are shared between sim tracker 'reschedule' action and the reschedule itself, order matters for UI
    public Map<String, Boolean> rescheduleChecks(final FlightMissions.Mission mission) {
        final Optional<TransportFlights.Flight> transportFlight = world.transportFlights().byFlightMissionId(mission.getId());

        final Map<String, Boolean> checks = new LinkedHashMap<>();
        checks.put("flight-status-check",
                mission.getStatus() == FlightMissions.Status.Dispatched
                        || mission.getStatus() == FlightMissions.Status.Preflight);
        checks.put("transport-flight-status-check", transportFlight
                .map(tf -> tf.getStatus() == TransportFlights.Status.Scheduled
                        || tf.getStatus() == TransportFlights.Status.CheckIn)
                .orElse(true));
        checks.put("not-scheduled-flight-check", transportFlight
                .map(tf -> tf.getScheduledFlightId() == 0)
                .orElse(true));
        checks.put("no-two-leg-journeys-check", transportFlight
                .map(tf -> world.journeys()
                        .filter(world.journeys().byAnyTransportFlightId(tf.getId()))
                        .noneMatch(j -> j.getTransportFlight2Id() != 0))
                .orElse(true));
        return checks;
    }

    public void reschedule(final FlightMissions.Mission mission, final int shiftMinutes) {
        rescheduleChecks(mission).forEach((name, result) -> checkArgument(result, "reschedule - " + name + " failed"));
        checkArgument(shiftMinutes != 0, "reschedule - shift should not be zero");
        checkArgument(shiftMinutes % RESCHEDULE_SHIFT_STEP_MINUTES == 0, "reschedule - shift should be a multiple of " + RESCHEDULE_SHIFT_STEP_MINUTES + " minutes");
        checkArgument(Math.abs(shiftMinutes) <= RESCHEDULE_MAX_SHIFT_MINUTES, "reschedule - shift should not exceed " + RESCHEDULE_MAX_SHIFT_MINUTES + " minutes");

        final int worldTime = world.getWorldTime();
        final int shift = shiftMinutes * Time.ONE_MINUTE;
        final int oldDeparture = mission.getPlannedDepartureWorldTime();
        final int oldArrival = mission.getPlannedArrivalWorldTime();
        final int newDeparture = oldDeparture + shift;
        final int newArrival = oldArrival + shift;
        checkArgument(newDeparture >= worldTime + RESCHEDULE_MIN_DEPARTURE_RESERVE, "reschedule - new departure time is too early");

        // departure first - it updates date of flight, and arrival is stored relatively to date of flight
        mission.setPlannedDepartureWorldTime(newDeparture);
        mission.setPlannedArrivalWorldTime(newArrival);

        log.info("f/m #{} - rescheduled by {} min, departure {} -> {}, arrival {} -> {}", mission.getId(), shiftMinutes,
                Time.toLdt(oldDeparture), Time.toLdt(newDeparture), Time.toLdt(oldArrival), Time.toLdt(newArrival));

        world.transportFlights().byFlightMissionId(mission.getId()).ifPresent(tf -> {
            final int checkinStartTime = TransportFlightHelper.calcCheckinStartTime(mission);
            final int checkinEndTime = TransportFlightHelper.calcCheckinEndTime(mission);

            // Scheduled t/f does not renew its heartbeat until check-in starts, so it has to be re-planned explicitly
            tf.setHeartbeatTime(tf.getStatus() == TransportFlights.Status.Scheduled
                    ? Math.max(worldTime, checkinStartTime)
                    : worldTime);

            final int pingFrom = Math.max(worldTime, checkinStartTime);
            final int pingTo = Math.max(pingFrom, checkinEndTime);
            final List<String> pingedJourneyIds = world.journeys()
                    .filter(world.journeys().byTransportFlight1IdAndStatus(tf.getId(), Journeys.Status.WaitingForCheckIn))
                    .peek(j -> j.setHeartbeatTime(Tools.random(pingFrom, pingTo)))
                    .map(j -> "j/y #" + j.getId())
                    .toList();

            log.info("f/m #{}, t/f #{} - rescheduled, t/f heartbeat {}, pinged j/y {}", mission.getId(), tf.getId(),
                    Time.toLdt(tf.getHeartbeatTime()), pingedJourneyIds);
        });
    }

    public void scheduleQuickRemoval(final FlightMissions.Mission mission) {
        cancelledIdsScheduledForQuickRemoval.add(mission.getId());
    }

    public boolean checkAndRemoveIfScheduledForQuickRemoval(final FlightMissions.Mission mission) {
        return cancelledIdsScheduledForQuickRemoval.remove(mission.getId());
    }

    // The status of the mission is the precondition of every step of the flight: a step is allowed from one status only.
    private static void checkStatus(final FlightMissions.Mission mission, final String step, final FlightMissions.Status expected) {
        checkArgument(mission.getStatus() == expected,
                "f/m #" + mission.getId() + " - " + step + " requires status " + expected + ", the actual status is " + mission.getStatus());
    }

    public void blocksOff(final FlightMissions.Mission mission) {
        checkStatus(mission, "blocksOff", FlightMissions.Status.Preflight);

        mission.setStatus(FlightMissions.Status.Departure);
        mission.setActualDepartureWorldTime(world.getWorldTime());

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        aircraft.setLocationStatus(Aircrafts.LocationStatus.TaxiingOut);
        aircraft.setLastUpdated(world.getWorldTime());

        world.transportFlights().byFlightMissionId(mission.getId()).ifPresent(transportFlightControl()::whenFlightDepartsFromGate);

        world.log(EventLog.EventType.AircraftDepartedFromGate, EventLog.userId(mission.getUserId()), mission, aircraft, EventLog.airportId(mission.getDepartureAirportId()));
        log.info("f/m #{} - aircraft {} departed from gate at {}", mission.getId(), aircraft.getRegNo(), world.airports().getIcao(mission.getDepartureAirportId()).orElseThrow());
    }

    public void takeoff(final FlightMissions.Mission mission) {
        checkStatus(mission, "takeoff", FlightMissions.Status.Departure);

        mission.setStatus(FlightMissions.Status.Flying);
        mission.setActualTakeoffWorldTime(world.getWorldTime());

        // todo ak3 pilot/pilots/cabin crew - set status, location

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        aircraft.setLocationStatus(Aircrafts.LocationStatus.Flying);

        final Airports.Airport locationAirport = world.airports().byId(aircraft.getLocationAirportId()).orElseThrow();
        aircraft.setLocationAirportId(0);
        aircraft.setLocationLatitude(locationAirport.getLatitude());
        aircraft.setLocationLongitude(locationAirport.getLongitude());
        aircraft.setLocationAltitude(0);
        aircraft.setLastUpdated(world.getWorldTime());

        world.transportFlights().byFlightMissionId(mission.getId()).ifPresent(transportFlightControl()::whenFlightTakeoffs);

        world.log(EventLog.EventType.AircraftTakeoff, EventLog.userId(mission.getUserId()), mission, aircraft, EventLog.airportId(mission.getDepartureAirportId()));
        log.info("f/m #{} - aircraft {} took off at {}", mission.getId(), aircraft.getRegNo(), world.airports().getIcao(mission.getDepartureAirportId()).orElseThrow());
    }

    public void landing(final FlightMissions.Mission mission, Airports.Airport landingAirport) {
        checkStatus(mission, "landing", FlightMissions.Status.Flying);

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

        mission.setStatus(FlightMissions.Status.Arrival);
        mission.setActualLandingWorldTime(world.getWorldTime());
        mission.setActualLandingAirportId(landingAirport.getId());

        if (mission.getUserId() > 0) {
            // the last location of the pilot is the location of its person
            world.persons().byUserId(mission.getUserId()).ifPresentOrElse(
                    person -> person.setLocation(Persons.LocationType.Airport, landingAirport.getId()),
                    () -> log.warn("f/m #{} - user #{} has no person, its location is not saved", mission.getId(), mission.getUserId()));
        }

        // todo ak3 pilot/pilots/cabin crew - set status, location

        aircraft.setLocationStatus(Aircrafts.LocationStatus.TaxiingIn);
        aircraft.setLocationAirportId(landingAirport.getId());
        aircraft.setLocationLatitude(landingAirport.getLatitude());
        aircraft.setLocationLongitude(landingAirport.getLongitude());
        aircraft.setLocationAltitude(0);
        aircraft.setLastUpdated(world.getWorldTime());

        world.transportFlights().byFlightMissionId(mission.getId()).ifPresent(transportFlightControl()::whenFlightLands);

        world.log(EventLog.EventType.AircraftLanding, EventLog.userId(mission.getUserId()), mission, aircraft, EventLog.airportId(mission.getActualLandingAirportId()));
        log.info("f/m #{} - aircraft {} landed at {}", mission.getId(), aircraft.getRegNo(), world.airports().getIcao(mission.getActualLandingAirportId()).orElseThrow());
    }

    public void blocksOn(final FlightMissions.Mission mission) {
        checkStatus(mission, "blocksOn", FlightMissions.Status.Arrival);

        mission.setStatus(FlightMissions.Status.Postflight);
        mission.setActualArrivalWorldTime(world.getWorldTime());

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

        final Airports.Airport locationAirport = world.airports().byId(mission.getDestinationAirportId()).orElseThrow();

        aircraft.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);
        aircraft.setLocationAirportId(locationAirport.getId());
        aircraft.setLocationLatitude(locationAirport.getLatitude());
        aircraft.setLocationLongitude(locationAirport.getLongitude());
        aircraft.setLocationAltitude(0);
        aircraft.setLastUpdated(world.getWorldTime());

        // todo ak3 pilot/pilots/cabin crew - set status, location

        world.transportFlights().byFlightMissionId(mission.getId()).ifPresent(transportFlightControl()::whenFlightArrivesToGate);

        world.log(EventLog.EventType.AircraftArrivedToGate, EventLog.userId(mission.getUserId()), mission, aircraft, EventLog.airportId(mission.getDestinationAirportId()));
        log.info("f/m #{} - aircraft {} arrived to gate at {}", mission.getId(), aircraft.getRegNo(), world.airports().getIcao(mission.getDestinationAirportId()).orElseThrow());
    }

    public void finish(final FlightMissions.Mission mission) {
        checkStatus(mission, "finish", FlightMissions.Status.Postflight);

        mission.setStatus(FlightMissions.Status.Finished);

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Idle);
        aircraft.setFlightMissionId(0);

        int flightDurationSeconds = mission.getActualArrivalWorldTime() - mission.getActualDepartureWorldTime();
        aircraft.setFlightTime(aircraft.getFlightTime() + flightDurationSeconds/60);
        aircraft.setFlownCycles(aircraft.getFlownCycles() + 1);

        aircraft.setLastUpdated(world.getWorldTime());

        // todo ak3 pilot/pilots/cabin crew - set status, location
        // todo ak3 pilot assignements / aircraft assignments?

        world.log(EventLog.EventType.FlightFinished, EventLog.userId(mission.getUserId()), mission, aircraft, EventLog.airportId(mission.getDestinationAirportId()));
        log.info("f/m #{} - flight finished", mission.getId());

        world.airport2airportDailyFlightStats().incrementTodayCount(mission.getDepartureAirportId(), mission.getActualLandingAirportId());
    }
}
