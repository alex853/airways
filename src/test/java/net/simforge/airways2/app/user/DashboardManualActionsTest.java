package net.simforge.airways2.app.user;

import net.simforge.airways2.app.dto.FlightUltraDto;
import net.simforge.airways2.pilottracker.SimTracker;
import net.simforge.airways2.world.processors.PaxManager;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Without the sim tracker the dashboard is in the manual mode: the actions it offers follow the status of the flight and of its transport flight.
 */
class DashboardManualActionsTest {
    private World world;
    private FlightMissions.Mission mission;
    // the pilot stands at the departure airport of the flight
    private int departureId;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final Airports.Airport a = world.airports().create(51.28, -0.76, "FRM", "EGLF", "Farnborough");
        final Airports.Airport b = world.airports().create(52.36, 13.5, "TOO", "EDDB", "Berlin");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", a);
        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        mission = world.flightMissions().createDispatchedMission(aircraft, a, b, departure, departure + 2 * Time.ONE_HOUR);
        departureId = a.getId();
    }

    private List<String> names() {
        return FlightDashboardController.buildManualActions(world, mission, departureId).stream().map(SimTracker.UserAction::getName).toList();
    }

    @Test
    void ferryFlightGoesThroughAllTheSteps() {
        assertEquals(List.of("start-flight-manual"), names());

        mission.setStatus(FlightMissions.Status.Preflight);
        assertEquals(List.of("blocks-off"), names());

        mission.setStatus(FlightMissions.Status.Departure);
        assertEquals(List.of("takeoff"), names());

        mission.setStatus(FlightMissions.Status.Flying);
        assertEquals(List.of("landing"), names());

        mission.setStatus(FlightMissions.Status.Arrival);
        assertEquals(List.of("blocks-on"), names());

        mission.setStatus(FlightMissions.Status.Postflight);
        assertEquals(List.of("finish-flight"), names());

        mission.setStatus(FlightMissions.Status.Finished);
        assertEquals(List.of(), names());
    }

    @Test
    void passengerFlightWaitsForBoardingAndDeboarding() {
        final TransportFlights.Flight tf = world.transportFlightControl().createTransportFlight(mission);

        mission.setStatus(FlightMissions.Status.Preflight);
        tf.setStatus(TransportFlights.Status.WaitingForBoarding);
        assertEquals(List.of("start-boarding"), names());

        tf.setStatus(TransportFlights.Status.Boarding);
        assertEquals(List.of(), names());

        tf.setStatus(TransportFlights.Status.WaitingForDeparture);
        assertEquals(List.of("blocks-off"), names());

        // the check-in is going: the pilot can start the boarding at once, the transport flight goes from CheckIn to Boarding without WaitingForBoarding
        tf.setStatus(TransportFlights.Status.CheckIn);
        assertEquals(List.of("start-boarding"), names());

        // the check-in has not started yet: nothing to board, no button
        tf.setStatus(TransportFlights.Status.Scheduled);
        assertEquals(List.of(), names());
        tf.setStatus(TransportFlights.Status.WaitingForDeparture);

        mission.setStatus(FlightMissions.Status.Postflight);
        tf.setStatus(TransportFlights.Status.WaitingForDeboarding);
        assertEquals(List.of("start-deboarding"), names());

        tf.setStatus(TransportFlights.Status.Deboarding);
        assertEquals(List.of(), names());

        tf.setStatus(TransportFlights.Status.Finished);
        assertEquals(List.of("finish-flight"), names());
    }

    @Test
    void aFlightWithTheAutoFinishHasNoFinishButtonButKeepsTheDeboarding() {
        final TransportFlights.Flight tf = world.transportFlightControl().createTransportFlight(mission);
        mission.setAutoFinish(true);
        mission.setStatus(FlightMissions.Status.Postflight);

        tf.setStatus(TransportFlights.Status.WaitingForDeboarding);
        assertEquals(List.of("start-deboarding"), names());

        tf.setStatus(TransportFlights.Status.Finished);
        assertEquals(List.of(), names());
    }

    @Test
    void theFerryFlightWithTheAutoFinishHasNoFinishButton() {
        mission.setAutoFinish(true);
        mission.setStatus(FlightMissions.Status.Postflight);

        assertEquals(List.of(), names());
    }

    @Test
    void theFlightDtoTellsTheAutoFinishOnlyWhenItIsOn() {
        assertEquals(null, FlightUltraDto.from(world, mission, true).getAutoFinish());

        mission.setAutoFinish(true);
        assertEquals(Boolean.TRUE, FlightUltraDto.from(world, mission, true).getAutoFinish());
    }

    @Test
    void theFlightDtoTellsTheCarriedPaxOnlyAfterTheTakeoff() {
        final TransportFlights.Flight tf = world.transportFlightControl().createTransportFlight(mission);
        tf.setPaxOnBoard(7);
        assertEquals(null, FlightUltraDto.from(world, mission, true).getCrdPax()); // they are on board, but the flight has not taken off yet

        tf.setPaxCarried(7);
        tf.setPaxOnBoard(3); // the deboarding goes on, the carried ones stay the same
        assertEquals(7, FlightUltraDto.from(world, mission, true).getCrdPax());
    }

    @Test
    void theFlightDtoHasThePlannedTimesOfTheBoardingOfAFlightWithPassengers() {
        assertEquals(null, FlightUltraDto.from(world, mission, true).getBrdEnd()); // a ferry flight has no boarding
        assertEquals(null, FlightUltraDto.from(world, mission, true).getBrdStart());

        final TransportFlights.Flight tf = world.transportFlightControl().createTransportFlight(mission);
        final FlightUltraDto dto = FlightUltraDto.from(world, mission, true);

        // the end is 10 minutes before the planned departure, the start is the end minus the duration by the layout
        final int end = mission.getPlannedDepartureWorldTime() - 10 * 60;
        assertEquals(net.simforge.airways2.tools.TimeTools.hhmmPlusDaysOrNull(end), dto.getBrdEnd());
        assertEquals(net.simforge.airways2.tools.TimeTools.hhmmPlusDaysOrNull(end - PaxManager.Boarding.estimatePlannedDuration(tf.getTotalTickets())), dto.getBrdStart());
    }

    @Test
    void theFlightDtoHasTheFixedTimesOfTheCheckIn() {
        assertEquals(null, FlightUltraDto.from(world, mission, true).getCinStart()); // a ferry flight has no check-in
        assertEquals(null, FlightUltraDto.from(world, mission, true).getCinEnd());

        world.transportFlightControl().createTransportFlight(mission);
        final FlightUltraDto dto = FlightUltraDto.from(world, mission, true);

        // the check-in starts 120 minutes before the planned departure and ends 30 minutes before it, it does not depend on the aircraft
        final int departure = mission.getPlannedDepartureWorldTime();
        assertEquals(net.simforge.airways2.tools.TimeTools.hhmmPlusDaysOrNull(departure - 120 * 60), dto.getCinStart());
        assertEquals(net.simforge.airways2.tools.TimeTools.hhmmPlusDaysOrNull(departure - 30 * 60), dto.getCinEnd());
    }

    @Test
    void manualLandingIsNotAllowedBeforeThreeQuartersOfThePlannedFlyingTime() {
        mission.setStatus(FlightMissions.Status.Flying);
        final int takeoff = world.getWorldTime();
        mission.setActualTakeoffWorldTime(takeoff);
        // the planned time of the mission is 2 hours block to block, 20 minutes of it are the taxi, the flying time is 100 minutes,
        // so the earliest landing is 75 minutes after the takeoff

        world.setWorldTime(takeoff + 75 * 60 - 1);
        final SimTracker.UserAction early = FlightDashboardController.buildManualActions(world, mission, departureId).get(0);
        assertEquals("landing", early.getName());
        assertFalse(early.isAllowed());
        assertFalse(early.getChecks().get(0).isResult());

        world.setWorldTime(takeoff + 75 * 60);
        assertTrue(FlightDashboardController.buildManualActions(world, mission, departureId).get(0).isAllowed());
    }

    @Test
    void manualActionsAreAllowed() {
        assertTrue(FlightDashboardController.buildManualActions(world, mission, departureId).stream().allMatch(SimTracker.UserAction::isAllowed));
    }

    // ---- the mode of the flight and the actions which the dashboard offers

    private static SimTracker.UserStatus connected(final SimTracker.UserAction... actions) {
        return new SimTracker.UserStatus("Connected", null, "At airport", "EGLF", true, false, "0.0", List.of(actions));
    }

    private static List<String> names(final List<SimTracker.UserAction> actions) {
        return actions.stream().map(SimTracker.UserAction::getName).toList();
    }

    private static SimTracker.UserAction trackerAction(final String name) {
        return new SimTracker.UserAction(name, true, List.of());
    }

    @Test
    void modeIsNotDecidedBeforeTheStart() {
        assertEquals(null, FlightDashboardController.resolveMode(mission, false));
        assertEquals(null, FlightDashboardController.resolveMode(mission, true));
        assertEquals(null, FlightDashboardController.resolveMode(null, true));
    }

    @Test
    void modeIsTheOneTheFlightWasStartedIn() {
        mission.setStatus(FlightMissions.Status.Preflight);

        mission.setTrackingMode(FlightMissions.TrackingMode.Manual);
        assertEquals(FlightDashboardController.TrackingMode.MANUAL, FlightDashboardController.resolveMode(mission, true));

        mission.setTrackingMode(FlightMissions.TrackingMode.SimTracker);
        assertEquals(FlightDashboardController.TrackingMode.SIM_TRACKER, FlightDashboardController.resolveMode(mission, false));
    }

    @Test
    void aFlightStartedBeforeTheModeWasStoredFollowsTheConnection() {
        mission.setStatus(FlightMissions.Status.Flying);

        assertEquals(FlightDashboardController.TrackingMode.SIM_TRACKER, FlightDashboardController.resolveMode(mission, true));
        assertEquals(FlightDashboardController.TrackingMode.MANUAL, FlightDashboardController.resolveMode(mission, false));
    }

    @Test
    void beforeTheStartWithoutTheTrackerOnlyTheManualStartIsOffered() {
        assertEquals(List.of("start-flight-manual"),
                names(FlightDashboardController.buildActions(world, mission, SimTracker.UserStatus.none(), false, departureId)));
    }

    @Test
    void beforeTheStartWithTheTrackerBothStartsAreOffered() {
        final SimTracker.UserStatus sim = connected(trackerAction("start-flight"), trackerAction("reschedule"));

        assertEquals(List.of("start-flight", "reschedule", "start-flight-manual"),
                names(FlightDashboardController.buildActions(world, mission, sim, true, departureId)));
    }

    @Test
    void aManualFlightGetsTheManualActionsEvenIfTheTrackerIsConnected() {
        mission.setStatus(FlightMissions.Status.Preflight);
        mission.setTrackingMode(FlightMissions.TrackingMode.Manual);
        final SimTracker.UserStatus sim = connected(trackerAction("blocks-off"), trackerAction("reschedule"));

        assertEquals(List.of("blocks-off"), names(FlightDashboardController.buildActions(world, mission, sim, true, departureId)));

        mission.setStatus(FlightMissions.Status.Departure);
        assertEquals(List.of("takeoff"), names(FlightDashboardController.buildActions(world, mission, sim, true, departureId)));
    }

    @Test
    void aTrackedFlightGetsTheActionsOfTheTracker() {
        mission.setStatus(FlightMissions.Status.Preflight);
        mission.setTrackingMode(FlightMissions.TrackingMode.SimTracker);
        final SimTracker.UserStatus sim = connected(trackerAction("blocks-off"), trackerAction("reschedule"));

        assertEquals(List.of("blocks-off", "reschedule"), names(FlightDashboardController.buildActions(world, mission, sim, true, departureId)));
    }

    @Test
    void theTrackedStartIsNotAllowedWhenThePilotIsNotAtTheDepartureAirport() {
        final SimTracker.UserStatus sim = connected(trackerAction("start-flight"));

        final SimTracker.UserAction atDeparture = FlightDashboardController.buildActions(world, mission, sim, true, departureId).get(0);
        assertEquals("start-flight", atDeparture.getName());
        assertTrue(atDeparture.isAllowed());

        // a pilot in a city (no airport) or at another airport has to travel to the departure airport first
        for (final int pilotAirportId : new int[]{0, departureId + 1}) {
            final SimTracker.UserAction elsewhere = FlightDashboardController.buildActions(world, mission, sim, true, pilotAirportId).get(0);
            assertEquals("start-flight", elsewhere.getName());
            assertFalse(elsewhere.isAllowed());
            assertFalse(elsewhere.getChecks().get(elsewhere.getChecks().size() - 1).isResult());
        }
    }

    @Test
    void theManualStartIsNotAllowedWhenThePilotIsNotAtTheDepartureAirport() {
        final SimTracker.UserAction elsewhere = FlightDashboardController.buildManualActions(world, mission, departureId + 1).get(0);
        assertEquals("start-flight-manual", elsewhere.getName());
        assertFalse(elsewhere.isAllowed());
        assertFalse(elsewhere.getChecks().get(0).isResult());

        // a pilot who is nowhere (no pilot, or the pilot is not at an airport)
        assertFalse(FlightDashboardController.buildManualActions(world, mission, 0).get(0).isAllowed());

        assertTrue(FlightDashboardController.buildManualActions(world, mission, departureId).get(0).isAllowed());
    }

    @Test
    void theManualStartIsNotAllowedWhenTheAircraftIsNotIdleAtTheDepartureAirport() {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Active);

        final SimTracker.UserAction start = FlightDashboardController.buildManualActions(world, mission, departureId).get(0);

        assertFalse(start.isAllowed());
        assertTrue(start.getChecks().get(0).isResult());
        assertFalse(start.getChecks().get(1).isResult());
    }
}
