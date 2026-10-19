package net.simforge.airways2.app.user;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A BusyBirds flight is booked only for a user who has a pilot (person) who is Idle at the airport where the aircraft stands.
 */
class BusyBirdsBookingPilotTest {
    private World world;
    private Airports.Airport oakland;
    private Airports.Airport sanFrancisco;
    private int cityId;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final int countryId = world.countries().create("US", "United States").getId();
        cityId = world.cities().create(countryId, "Oakland", 37.72, -122.22, 400000).getId();
        oakland = world.airports().create(37.72, -122.22, "OAK", "KOAK", "Oakland");
        sanFrancisco = world.airports().create(37.62, -122.38, "SFO", "KSFO", "San Francisco");
    }

    private Persons.Person person(final Persons.State state, final Persons.LocationType locationType, final int locationId) {
        return world.persons().create(Persons.Type.Pilot, state, 1, locationType, locationId, cityId, "AK", 0);
    }

    @Test
    void noPersonNoBooking() {
        assertTrue(BusyBirdsController.pilotBookingProblem(world, 0, oakland.getId()).contains("no pilot"));
        assertTrue(BusyBirdsController.pilotBookingProblem(world, 12345, oakland.getId()).contains("no pilot"));
    }

    @Test
    void idlePilotAtTheAirportOfTheAircraftMayBook() {
        final Persons.Person p = person(Persons.State.Idle, Persons.LocationType.Airport, oakland.getId());

        assertNull(BusyBirdsController.pilotBookingProblem(world, p.getId(), oakland.getId()));
    }

    @Test
    void busyPilotMayNotBook() {
        final Persons.Person p = person(Persons.State.OnDuty, Persons.LocationType.Airport, oakland.getId());

        assertTrue(BusyBirdsController.pilotBookingProblem(world, p.getId(), oakland.getId()).contains("busy"));
    }

    @Test
    void pilotOutOfAirportMayNotBook() {
        final Persons.Person p = person(Persons.State.Idle, Persons.LocationType.City, cityId);

        assertTrue(BusyBirdsController.pilotBookingProblem(world, p.getId(), oakland.getId()).contains("not at an airport"));
    }

    @Test
    void pilotAtAnotherAirportThanTheAircraftMayNotBook() {
        final Persons.Person p = person(Persons.State.Idle, Persons.LocationType.Airport, oakland.getId());

        assertEquals("Your pilot is at KOAK but the aircraft is at KSFO, the flight cannot be booked.",
                BusyBirdsController.pilotBookingProblem(world, p.getId(), sanFrancisco.getId()));
    }
}
