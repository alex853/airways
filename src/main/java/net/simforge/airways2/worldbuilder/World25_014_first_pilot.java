package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.app.userstorage.User;
import net.simforge.airways2.app.userstorage.UserStorage;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import net.simforge.airways2.world.datamodel.Persons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

// One-time creation of the first person (id 1): the pilot of user 1. The airport is the last location airport of the user.
// It does nothing if the world already has any person, so a repeated start is harmless. The step is commented out in
// Application.main after the deploy.
public class World25_014_first_pilot {
    private static final Logger log = LoggerFactory.getLogger(World25_014_first_pilot.class);

    static final int USER_ID = 1;
    static final String ORIGIN_COUNTRY_CODE = "CY";
    static final String ORIGIN_CITY_NAME = "Larnaca";
    static final String INITIALS = "AK";
    static final int AVATAR_ID = 0;

    public static void main(String[] args) throws IOException {
        final Optional<User> user = UserStorage.getInstance().findById(USER_ID);
        if (user.isEmpty()) {
            log.error("First pilot - user #{} not found, the pilot is not created", USER_ID);
            return;
        }

        final Optional<String> lastLocationAirport = user.get().getLastLocationAirport();
        if (lastLocationAirport.isEmpty()) {
            log.error("First pilot - user #{} has no last location airport, the pilot is not created", USER_ID);
            return;
        }

        final World world = World25.load();

        if (addFirstPilot(world, USER_ID, lastLocationAirport.get()).isPresent()) {
            world.save();
        }
    }

    /**
     * @return the created person, empty (and nothing is changed) if the world already has persons or the airport or the city is not found
     */
    static Optional<Persons.Person> addFirstPilot(final World world, final int userId, final String airportIcao) {
        if (world.persons().all().findAny().isPresent()) {
            log.warn("First pilot - the world already has persons, skipped");
            return Optional.empty();
        }

        final Optional<Airports.Airport> airport = world.airports().byIcao(airportIcao);
        if (airport.isEmpty()) {
            log.error("First pilot - airport {} not found, the pilot is not created", airportIcao);
            return Optional.empty();
        }

        final Optional<Countries.Country> country = world.countries().byCode(ORIGIN_COUNTRY_CODE);
        final Optional<Cities.City> city = country.flatMap(c -> world.cities().byCountryIdAndName(c.getId(), ORIGIN_CITY_NAME));
        if (city.isEmpty()) {
            log.error("First pilot - origin city {} ({}) not found, the pilot is not created", ORIGIN_CITY_NAME, ORIGIN_COUNTRY_CODE);
            return Optional.empty();
        }

        final Persons.Person person = world.persons().create(
                Persons.Type.Pilot,
                Persons.State.Idle,
                userId,
                Persons.LocationType.Airport,
                airport.get().getId(),
                city.get().getId(),
                INITIALS,
                AVATAR_ID);

        log.warn("First pilot - person #{} created, user #{}, airport {}, origin city {}", person.getId(), userId, airportIcao, ORIGIN_CITY_NAME);
        return Optional.of(person);
    }
}
