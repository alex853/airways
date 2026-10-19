package net.simforge.airways2.app.user;

import net.simforge.airways2.app.user.FlightDashboardController.UserLocationDto;
import net.simforge.airways2.app.user.FlightDashboardController.UserLocationState;
import net.simforge.airways2.pilottracker.SimTracker;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The verdict of the dashboard about the location of the pilot: the person is expected to be Idle at an airport and the pilot has to be
 * loaded at the same airport. A flight in progress is always valid.
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
    public void test__no_person_means_no_restrictions() {
        assertEquals(UserLocationState.OK, verdict(null, null, false, false, atAirport("KSFO")));
        assertEquals(UserLocationState.OK, verdict(null, null, false, false, SimTracker.UserStatus.none()));
    }

    @Test
    public void test__person_which_is_not_at_an_airport_means_no_restrictions() {
        assertEquals(UserLocationState.OK, verdict(null, Persons.State.Idle, true, false, atAirport("KSFO")));
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
}
