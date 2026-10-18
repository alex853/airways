package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The last location of a pilot is the location of its person: the landing of a flight of the user moves the person to the airport.
 */
class PersonLocationOnLandingTest {
    private World world;
    private FlightMissionControl control;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private FlightMissions.Mission mission;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        control = world.flightMissionControl();
        airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        airportB = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", airportA);
        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        mission = world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, departure, departure + 2 * Time.ONE_HOUR);
        mission.setStatus(FlightMissions.Status.Flying);
    }

    private Persons.Person person(final int userId) {
        return world.persons().create(Persons.Type.Pilot, Persons.State.Idle, userId,
                Persons.LocationType.Airport, airportA.getId(), 0, "AK", 0);
    }

    @Test
    public void test__landing_of_the_flight_of_the_user_moves_the_person_of_the_user_to_the_landing_airport() {
        final Persons.Person person = person(7);
        final Persons.Person another = person(8);
        mission.setUserId(7);

        control.landing(mission, airportB);

        assertEquals(Persons.LocationType.Airport, person.getLocationType());
        assertEquals(airportB.getId(), person.getLocationId());
        assertEquals(airportA.getId(), another.getLocationId());
    }

    @Test
    public void test__landing_of_the_flight_without_user_changes_no_person() {
        final Persons.Person person = person(7);

        control.landing(mission, airportB);

        assertEquals(airportA.getId(), person.getLocationId());
    }

    @Test
    public void test__landing_of_the_flight_of_the_user_without_person_does_not_fail() {
        mission.setUserId(9);

        control.landing(mission, airportB);

        assertEquals(FlightMissions.Status.Arrival, mission.getStatus());
    }

    @Test
    public void test__by_user_id_finds_the_person_of_the_user() {
        final Persons.Person person = person(7);
        person(0);

        assertEquals(person.getId(), world.persons().byUserId(7).orElseThrow().getId());
        assertTrue(world.persons().byUserId(5).isEmpty());
    }
}
