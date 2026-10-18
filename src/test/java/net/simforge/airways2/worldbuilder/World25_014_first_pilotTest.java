package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class World25_014_first_pilotTest {
    private World world;
    private Airports.Airport airport;
    private Cities.City larnaca;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final int countryId = world.countries().create("CY", "Cyprus").getId();
        larnaca = world.cities().create(countryId, "Larnaca", 34.92, 33.63, 160000);
        airport = world.airports().create(34.8788, 33.6303, "LCA", "LCLK", "Larnaca Intl");
    }

    @Test
    public void test__first_pilot_is_person_1_of_user_1_at_the_airport() {
        final Persons.Person person = World25_014_first_pilot.addFirstPilot(world, 1, "LCLK").orElseThrow();

        assertEquals(1, person.getId());
        assertEquals(Persons.Type.Pilot, person.getType());
        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(0, person.getHeartbeatTime());
        assertEquals(1, person.getUserId());
        assertEquals(Persons.LocationType.Airport, person.getLocationType());
        assertEquals(airport.getId(), person.getLocationId());
        assertEquals(larnaca.getId(), person.getOriginCityId());
        assertEquals("AK", person.getInitials());
        assertEquals(0, person.getAvatarId());
    }

    @Test
    public void test__it_is_one_time__the_second_call_changes_nothing() {
        World25_014_first_pilot.addFirstPilot(world, 1, "LCLK").orElseThrow();

        assertTrue(World25_014_first_pilot.addFirstPilot(world, 1, "LCLK").isEmpty());
        assertEquals(1, world.persons().all().count());
    }

    @Test
    public void test__unknown_airport_or_city_creates_nothing() {
        assertTrue(World25_014_first_pilot.addFirstPilot(world, 1, "XXXX").isEmpty());
        assertEquals(0, world.persons().all().count());

        final World noCity = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        noCity.airports().create(34.8788, 33.6303, "LCA", "LCLK", "Larnaca Intl");
        assertTrue(World25_014_first_pilot.addFirstPilot(noCity, 1, "LCLK").isEmpty());
        assertEquals(Optional.empty(), noCity.persons().byId(1));
    }
}
