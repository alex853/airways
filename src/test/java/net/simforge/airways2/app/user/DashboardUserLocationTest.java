package net.simforge.airways2.app.user;

import net.simforge.airways2.app.user.FlightDashboardController.UserLocationDto;
import net.simforge.airways2.app.user.FlightDashboardController.UserLocationState;
import net.simforge.airways2.app.userstorage.User;
import net.simforge.airways2.pilottracker.SimTracker;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The verdict of the dashboard about the pilot: the user has to have a person, the person is expected to be Idle at an airport and the pilot has to be
 * loaded at the same airport. A flight in progress is valid. The states which block the dashboard are refused by the actions.
 */
class DashboardUserLocationTest {
    private static SimTracker.UserStatus atAirport(final String icao) {
        return new SimTracker.UserStatus("Connected", null, "At airport", icao, true, false, "0.0", null);
    }

    private static SimTracker.UserStatus inFlight() {
        return new SimTracker.UserStatus("Connected", null, "In flight", null, false, true, "250", null);
    }

    private static SimTracker.UserStatus onGroundOutOfAirport() {
        return new SimTracker.UserStatus("Connected", null, "On ground out of any airport", null, true, false, "0.0", null);
    }

    private static UserLocationDto dto(final String expected, final Persons.State personState, final boolean hasPerson,
                                       final boolean flightInProgress, final SimTracker.UserStatus sim) {
        return FlightDashboardController.buildUserLocationDto(expected, personState, hasPerson, flightInProgress, sim);
    }

    private static UserLocationState verdict(final String expected, final Persons.State personState, final boolean hasPerson,
                                             final boolean flightInProgress, final SimTracker.UserStatus sim) {
        return dto(expected, personState, hasPerson, flightInProgress, sim).getState();
    }

    // ---- the verdict

    @Test
    public void test__idle_person_at_the_same_airport_is_ok() {
        assertEquals(UserLocationState.OK, verdict("KOAK", Persons.State.Idle, true, false, atAirport("KOAK")));
    }

    @Test
    public void test__airports_are_compared_ignoring_the_case() {
        assertEquals(UserLocationState.OK, verdict("KOAK", Persons.State.Idle, true, false, atAirport("koak")));
    }

    @Test
    public void test__idle_person_and_pilot_at_another_airport_is_wrong_airport() {
        assertEquals(UserLocationState.WRONG_AIRPORT, verdict("KOAK", Persons.State.Idle, true, false, atAirport("KSFO")));
    }

    @Test
    public void test__idle_person_and_the_sim_tracker_is_not_connected_is_unknown() {
        assertEquals(UserLocationState.UNKNOWN, verdict("KOAK", Persons.State.Idle, true, false, SimTracker.UserStatus.none()));
    }

    @Test
    public void test__idle_person_and_the_pilot_in_the_air_or_out_of_airport_is_unknown() {
        assertEquals(UserLocationState.UNKNOWN, verdict("KOAK", Persons.State.Idle, true, false, inFlight()));
        assertEquals(UserLocationState.UNKNOWN, verdict("KOAK", Persons.State.Idle, true, false, onGroundOutOfAirport()));
    }

    @Test
    public void test__no_person_is_no_person_whatever_the_sim_tracker_says() {
        assertEquals(UserLocationState.NO_PERSON, verdict(null, null, false, false, atAirport("KSFO")));
        assertEquals(UserLocationState.NO_PERSON, verdict(null, null, false, false, SimTracker.UserStatus.none()));
        assertEquals(UserLocationState.NO_PERSON, verdict(null, null, false, false, inFlight()));
    }

    @Test
    public void test__no_person_is_no_person_even_when_a_flight_is_in_progress() {
        assertEquals(UserLocationState.NO_PERSON, verdict(null, null, false, true, atAirport("KSFO")));
    }

    @Test
    public void test__idle_person_which_is_not_at_an_airport_is_not_ok_the_pilot_cannot_start_from_nowhere() {
        // the pilot is loaded at an airport but the person is in the air, in a hotel or in a city
        assertEquals(UserLocationState.PERSON_NOT_AT_AIRPORT, verdict(null, Persons.State.Idle, true, false, atAirport("KSFO")));
        assertEquals(UserLocationState.PERSON_NOT_AT_AIRPORT, verdict(null, Persons.State.Idle, true, false, SimTracker.UserStatus.none()));
        assertEquals(UserLocationState.PERSON_NOT_AT_AIRPORT, verdict(null, Persons.State.Idle, true, false, inFlight()));
    }

    @Test
    public void test__flight_in_progress_is_valid_for_the_person_on_duty_wherever_the_pilot_is() {
        assertEquals(UserLocationState.OK, verdict("KOAK", Persons.State.OnDuty, true, true, atAirport("KSFO")));
        assertEquals(UserLocationState.OK, verdict(null, Persons.State.OnDuty, true, true, inFlight()));
        assertEquals(UserLocationState.OK, verdict("KOAK", Persons.State.OnDuty, true, true, SimTracker.UserStatus.none()));
    }

    @Test
    public void test__person_on_duty_without_a_flight_in_progress_is_not_idle() {
        assertEquals(UserLocationState.PERSON_NOT_IDLE, verdict("KOAK", Persons.State.OnDuty, true, false, atAirport("KOAK")));
    }

    @Test
    public void test__resting_or_travelling_person_is_not_idle_even_at_the_same_airport() {
        assertEquals(UserLocationState.PERSON_NOT_IDLE, verdict("KOAK", Persons.State.Resting, true, false, atAirport("KOAK")));
        assertEquals(UserLocationState.PERSON_NOT_IDLE, verdict(null, Persons.State.Travelling, true, false, atAirport("KOAK")));
    }

    @Test
    public void test__expected_and_current_airports_are_filled_whenever_they_are_known() {
        final UserLocationDto wrong = dto("KOAK", Persons.State.Idle, true, false, atAirport("KSFO"));
        assertEquals("KOAK", wrong.getExpectedAirport());
        assertEquals("KSFO", wrong.getCurrentAirport());

        final UserLocationDto notConnected = dto("KOAK", Persons.State.Idle, true, false, SimTracker.UserStatus.none());
        assertEquals("KOAK", notConnected.getExpectedAirport());
        assertNull(notConnected.getCurrentAirport());

        final UserLocationDto noPerson = dto(null, null, false, false, atAirport("KSFO"));
        assertNull(noPerson.getExpectedAirport());
        assertEquals("KSFO", noPerson.getCurrentAirport());
    }

    // ---- which states block the dashboard

    @Test
    public void test__states_which_block_the_dashboard() {
        final EnumSet<UserLocationState> blocking = EnumSet.of(UserLocationState.NO_PERSON, UserLocationState.PERSON_NOT_IDLE,
                UserLocationState.PERSON_NOT_AT_AIRPORT, UserLocationState.WRONG_AIRPORT);

        for (final UserLocationState state : UserLocationState.values()) {
            assertEquals(blocking.contains(state), state.blocksDashboard(), state.name());
        }
        // the dashboard works without the sim tracker, so UNKNOWN is not a reason to refuse
        assertEquals(false, UserLocationState.UNKNOWN.blocksDashboard());
        assertEquals(false, UserLocationState.OK.blocksDashboard());
    }

    @Test
    public void test__blocking_state_refuses_the_action_with_403_and_the_others_let_it_go() {
        for (final UserLocationState state : UserLocationState.values()) {
            final UserLocationDto location = new UserLocationDto(state, "KOAK", "KOAK");
            if (state.blocksDashboard()) {
                final ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> FlightDashboardController.checkDashboardIsAllowed(location), state.name());
                assertEquals(HttpStatus.FORBIDDEN, e.getStatus(), state.name());
                assertEquals(true, e.getReason().contains(state.name()), e.getReason());
            } else {
                assertDoesNotThrow(() -> FlightDashboardController.checkDashboardIsAllowed(location), state.name());
            }
        }
    }

    // ---- the verdict for a user of the world

    private World world() {
        return World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
    }

    private static Airports.Airport airport(final World world, final String icao, final double lon) {
        return world.airports().create(51.0, lon, icao.substring(0, 3), icao, icao);
    }

    @Test
    public void test__user_without_person_id_has_no_person() {
        final World world = world();
        final User user = new User(1, "token-of-user-1", 0, 0);

        final UserLocationDto location = FlightDashboardController.buildUserLocation(world, user, 1, atAirport("KOAK"));

        assertEquals(UserLocationState.NO_PERSON, location.getState());
    }

    @Test
    public void test__user_whose_person_does_not_exist_in_the_world_has_no_person() {
        final World world = world();
        final User user = new User(1, "token-of-user-1", 5, 0); // there is no person #5

        assertEquals(UserLocationState.NO_PERSON, FlightDashboardController.buildUserLocation(world, user, 1, atAirport("KOAK")).getState());
    }

    @Test
    public void test__unknown_user_has_no_person() {
        final World world = world();

        assertEquals(UserLocationState.NO_PERSON, FlightDashboardController.buildUserLocation(world, null, 1, atAirport("KOAK")).getState());
    }

    @Test
    public void test__person_at_the_airport_of_the_pilot_is_ok() {
        final World world = world();
        final Airports.Airport koak = airport(world, "KOAK", -122.0);
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1,
                Persons.LocationType.Airport, koak.getId(), 0, "AK", 0);
        final User user = new User(1, "token-of-user-1", person.getId(), 0);

        final UserLocationDto location = FlightDashboardController.buildUserLocation(world, user, 1, atAirport("KOAK"));

        assertEquals(UserLocationState.OK, location.getState());
        assertEquals("KOAK", location.getExpectedAirport());
    }

    @Test
    public void test__person_in_a_city_or_nowhere_is_not_at_an_airport() {
        final World world = world();
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1,
                Persons.LocationType.None, 0, 0, "AK", 0);
        final User user = new User(1, "token-of-user-1", person.getId(), 0);

        assertEquals(UserLocationState.PERSON_NOT_AT_AIRPORT, FlightDashboardController.buildUserLocation(world, user, 1, atAirport("KOAK")).getState());

        person.setLocation(Persons.LocationType.City, 5);
        assertEquals(UserLocationState.PERSON_NOT_AT_AIRPORT, FlightDashboardController.buildUserLocation(world, user, 1, atAirport("KOAK")).getState());
    }

    @Test
    public void test__active_flight_of_the_user_makes_the_person_on_duty_valid_without_the_sim_tracker() {
        final World world = world();
        final Airports.Airport koak = airport(world, "KOAK", -122.0);
        final Airports.Airport ksfo = airport(world, "KSFO", -122.4);
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", koak);
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.OnDuty, 1,
                Persons.LocationType.None, 0, 0, "AK", 0);
        final User user = new User(1, "token-of-user-1", person.getId(), 0);

        // OnDuty and nowhere, there is no flight: the dashboard refuses
        assertEquals(UserLocationState.PERSON_NOT_IDLE, FlightDashboardController.buildUserLocation(world, user, 1, SimTracker.UserStatus.none()).getState());

        // the same person while the flight of the user is in progress: it is fine
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(aircraft, koak, ksfo,
                Time.START_TIME_EPOCH_SECONDS + 3600, Time.START_TIME_EPOCH_SECONDS + 7200);
        mission.setUserId(1);
        mission.setStatus(FlightMissions.Status.Flying);

        assertEquals(UserLocationState.OK, FlightDashboardController.buildUserLocation(world, user, 1, SimTracker.UserStatus.none()).getState());

        // a flight which is only dispatched is not in progress yet
        mission.setStatus(FlightMissions.Status.Dispatched);
        assertEquals(UserLocationState.PERSON_NOT_IDLE, FlightDashboardController.buildUserLocation(world, user, 1, SimTracker.UserStatus.none()).getState());
    }
}
