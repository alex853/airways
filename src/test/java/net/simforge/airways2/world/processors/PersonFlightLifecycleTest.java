package net.simforge.airways2.world.processors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.datamodel.PilotLogbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The person (pilot) of the user lives together with the flight: Idle at the airport, OnDuty after the start, nowhere in the flight,
 * at the landing airport after the landing, Idle again when the flight is finished. Every change is logged as 'p/s #id'.
 */
class PersonFlightLifecycleTest {
    private World world;
    private FlightMissionControl control;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private FlightMissions.Mission mission;
    private Persons.Person person;

    private Logger personLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        control = world.flightMissionControl();
        airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        airportB = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", airportA);
        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        mission = world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, departure, departure + 2 * Time.ONE_HOUR);
        mission.setUserId(7);
        person = person(7);

        personLogger = (Logger) LoggerFactory.getLogger(PersonControl.class);
        appender = new ListAppender<>();
        appender.start();
        personLogger.addAppender(appender);
        personLogger.setLevel(Level.INFO);
    }

    @AfterEach
    public void afterEach() {
        personLogger.detachAppender(appender);
    }

    private Persons.Person person(final int userId) {
        return world.persons().create(Persons.Type.Pilot, Persons.State.Idle, userId,
                Persons.LocationType.Airport, airportA.getId(), 0, "AK", 0);
    }

    private List<String> logged() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private void flyUntilLanding() {
        assertTrue(control.start(mission).started());
        control.blocksOff(mission);
        control.takeoff(mission);
        control.landing(mission, airportB);
    }

    @Test
    public void test__whole_flight__idle_onduty_nowhere_airport_idle() {
        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(airportA.getId(), person.getLocationId());

        assertTrue(control.start(mission).started());
        assertEquals(Persons.State.OnDuty, person.getState());
        assertEquals(Persons.LocationType.Airport, person.getLocationType());
        assertEquals(airportA.getId(), person.getLocationId());

        control.blocksOff(mission);
        assertEquals(Persons.State.OnDuty, person.getState());
        assertEquals(airportA.getId(), person.getLocationId());

        control.takeoff(mission);
        assertEquals(Persons.State.OnDuty, person.getState());
        assertEquals(Persons.LocationType.None, person.getLocationType());
        assertEquals(0, person.getLocationId());

        control.landing(mission, airportB);
        assertEquals(Persons.State.OnDuty, person.getState());
        assertEquals(Persons.LocationType.Airport, person.getLocationType());
        assertEquals(airportB.getId(), person.getLocationId());

        control.blocksOn(mission);
        assertEquals(Persons.State.OnDuty, person.getState());

        control.finish(mission);
        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(Persons.LocationType.Airport, person.getLocationType());
        assertEquals(airportB.getId(), person.getLocationId());
    }

    @Test
    public void test__landing_goes_to_the_actual_landing_airport() {
        final Airports.Airport alternate = world.airports().create(0, 11, "CCC", "CCCC", "Charlie");
        assertTrue(control.start(mission).started());
        control.blocksOff(mission);
        control.takeoff(mission);

        control.landing(mission, alternate);

        assertEquals(alternate.getId(), person.getLocationId());
    }

    @Test
    public void test__other_persons_are_not_touched() {
        final Persons.Person another = person(8);
        final Persons.Person npc = person(0);

        flyUntilLanding();
        control.blocksOn(mission);
        control.finish(mission);

        for (final Persons.Person other : List.of(another, npc)) {
            assertEquals(Persons.State.Idle, other.getState());
            assertEquals(airportA.getId(), other.getLocationId());
        }
    }

    @Test
    public void test__mission_without_user_changes_no_person() {
        mission.setUserId(0);

        flyUntilLanding();
        control.blocksOn(mission);
        control.finish(mission);

        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(airportA.getId(), person.getLocationId());
        assertEquals(List.of(), logged());
    }

    @Test
    public void test__user_without_person_does_not_break_the_flight() {
        mission.setUserId(9);

        flyUntilLanding();
        control.blocksOn(mission);
        control.finish(mission);

        assertEquals(FlightMissions.Status.Finished, mission.getStatus());
        assertEquals(Persons.State.Idle, person.getState());
        assertTrue(logged().stream().noneMatch(m -> m.startsWith("p/s")));
    }

    @Test
    public void test__person_who_is_not_idle_goes_on_duty_anyway_the_start_is_not_blocked() {
        person.setState(Persons.State.Resting);

        assertTrue(control.start(mission).started());

        assertEquals(Persons.State.OnDuty, person.getState());
    }

    @Test
    public void test__cancel_of_dispatched_mission_does_not_touch_the_person() {
        control.cancelDispatched(mission, "test");

        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(airportA.getId(), person.getLocationId());
        assertEquals(List.of(), logged());
    }

    @Test
    public void test__cancel_in_flight_returns_the_person_to_the_departure_airport_and_frees_it() {
        assertTrue(control.start(mission).started());
        control.blocksOff(mission);
        control.takeoff(mission);
        assertEquals(Persons.LocationType.None, person.getLocationType());

        control.cancelFlightAndReturnAircraftToDepartureAirport(mission);

        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(Persons.LocationType.Airport, person.getLocationType());
        assertEquals(airportA.getId(), person.getLocationId());
    }

    @Test
    public void test__cancel_in_preflight_frees_the_person() {
        assertTrue(control.start(mission).started());

        control.cancelFlightAndReturnAircraftToDepartureAirport(mission);

        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(airportA.getId(), person.getLocationId());
    }

    @Test
    public void test__every_change_is_logged_as_p_s() {
        flyUntilLanding();
        control.blocksOn(mission);
        control.finish(mission);

        final int id = person.getId();
        final int fm = mission.getId();
        assertEquals(List.of(
                "p/s #" + id + " - state Idle -> OnDuty (f/m #" + fm + " started)",
                "p/s #" + id + " - location Airport AAAA -> None (f/m #" + fm + " took off)",
                "p/s #" + id + " - location None -> Airport BBBB (f/m #" + fm + " landed)",
                "p/s #" + id + " - state OnDuty -> Idle (f/m #" + fm + " finished)",
                "p/s #" + id + " - logbook entry #1: AAAA -> BBBB, 0 min, f/m #" + fm
        ), logged());
    }

    // ---- the logbook of the pilot

    @Test
    public void test__finished_flight_is_written_to_the_logbook_of_the_pilot() {
        assertTrue(control.start(mission).started());
        control.blocksOff(mission);
        control.takeoff(mission);
        control.landing(mission, airportB);
        world.setWorldTime(world.getWorldTime() + 95 * Time.ONE_MINUTE + 30);
        control.blocksOn(mission);
        control.finish(mission);

        final List<PilotLogbook.Entry> entries = world.pilotLogbook().all().toList();
        assertEquals(1, entries.size());
        final PilotLogbook.Entry entry = entries.get(0);
        assertEquals(person.getId(), entry.getPersonId());
        assertEquals(mission.getAircraftId(), entry.getAircraftId());
        assertEquals(airportA.getId(), entry.getFromAirportId());
        assertEquals(airportB.getId(), entry.getToAirportId());
        assertEquals(Time.START_TIME_EPOCH_SECONDS, entry.getBlocksOffTime());
        assertEquals(Time.START_TIME_EPOCH_SECONDS + 95 * Time.ONE_MINUTE, entry.getBlocksOnTime());
        assertEquals(95, entry.getDurationMinutes());
    }

    @Test
    public void test__logbook_gets_the_actual_landing_airport() {
        final Airports.Airport alternate = world.airports().create(0, 11, "CCC", "CCCC", "Charlie");
        assertTrue(control.start(mission).started());
        control.blocksOff(mission);
        control.takeoff(mission);
        control.landing(mission, alternate);
        control.blocksOn(mission);
        control.finish(mission);

        assertEquals(alternate.getId(), world.pilotLogbook().all().findFirst().orElseThrow().getToAirportId());
    }

    @Test
    public void test__flights_without_the_user_or_without_the_person_or_cancelled_are_not_written() {
        mission.setUserId(0);
        flyUntilLanding();
        control.blocksOn(mission);
        control.finish(mission);
        assertEquals(0, world.pilotLogbook().all().count());

        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        final FlightMissions.Mission noPerson = world.flightMissions().createDispatchedMission(
                world.aircrafts().byId(mission.getAircraftId()).orElseThrow(), airportB, airportA, departure, departure + Time.ONE_HOUR);
        noPerson.setUserId(9);
        noPerson.setActualLandingAirportId(airportA.getId());
        noPerson.setStatus(FlightMissions.Status.Postflight);
        control.finish(noPerson);
        assertEquals(0, world.pilotLogbook().all().count());
    }

    @Test
    public void test__cancelled_flight_is_not_written() {
        assertTrue(control.start(mission).started());
        control.blocksOff(mission);
        control.takeoff(mission);

        control.cancelFlightAndReturnAircraftToDepartureAirport(mission);

        assertEquals(0, world.pilotLogbook().all().count());
    }

    @Test
    public void test__flight_with_invalid_times_is_not_written_and_does_not_break_the_finish() {
        mission.setActualLandingAirportId(airportB.getId());
        mission.setStatus(FlightMissions.Status.Postflight); // blocks off and blocks on were not set

        control.finish(mission);

        assertEquals(FlightMissions.Status.Finished, mission.getStatus());
        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(0, world.pilotLogbook().all().count());
        assertTrue(logged().stream().anyMatch(m -> m.contains("is not written to the logbook")), logged().toString());
    }

    @Test
    public void test__nothing_is_logged_when_nothing_changes() {
        world.personControl().setState(person, Persons.State.Idle, "same");
        world.personControl().setLocation(person, Persons.LocationType.Airport, airportA.getId(), "same");

        assertEquals(List.of(), logged());
    }
}
