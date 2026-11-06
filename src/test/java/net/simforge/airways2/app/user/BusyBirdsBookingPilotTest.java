package net.simforge.airways2.app.user;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A BusyBirds mission is booked only for a user who has a pilot (person); the state and the place of the pilot do not matter. The airports of the pilot (its airport, or the airports
 * of its city) are told by the page of the pilot.
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
        assertTrue(BusyBirdsController.noPilotProblem(world, 12345).contains("no pilot"));
    }

    @Test
    void anExcludedAirportIsNotOfferedToAPilotInACity() {
        world.airport2city().create(oakland.getId(), cityId);
        world.airport2city().create(sanFrancisco.getId(), cityId);
        sanFrancisco.setExcluded(true);
        final Persons.Person p = person(Persons.State.Idle, Persons.LocationType.City, cityId);

        assertEquals(List.of("KOAK"), world.personControl().bookingAirports(p).stream().map(Airports.Airport::getIcao).toList());
    }

    @Test
    void aPilotInACityHasTheAirportsLinkedToTheCity() {
        world.airport2city().create(oakland.getId(), cityId);
        world.airport2city().create(sanFrancisco.getId(), cityId);
        final Persons.Person p = person(Persons.State.Idle, Persons.LocationType.City, cityId);

        assertEquals(List.of("KOAK", "KSFO"), world.personControl().bookingAirports(p).stream().map(Airports.Airport::getIcao).toList());
    }
}
