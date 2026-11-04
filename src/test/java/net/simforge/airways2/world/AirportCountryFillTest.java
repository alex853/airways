package net.simforge.airways2.world;

import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AirportCountryFillTest {
    @TempDir
    Path root;

    private WorldStorageStrategy folderStrategy() {
        return new WorldStorageStrategy() {
            @Override
            public void load(final WorldIOOperation loadingOps) throws IOException {
                loadingOps.perform(root);
            }

            @Override
            public void save(final WorldIOOperation savingOps) throws IOException {
                savingOps.perform(root);
            }
        };
    }

    @Test
    public void test__country_of_airport_is_the_country_of_the_biggest_linked_city_and_is_filled_once() throws IOException {
        final WorldStorageStrategy strategy = folderStrategy();
        final World world = World.create(strategy, Time.START_TIME_EPOCH_SECONDS);
        final Countries.Country countryA = world.countries().create("AA", "Aland");
        final Countries.Country countryB = world.countries().create("BB", "Bland");
        final Cities.City small = world.cities().create(countryA.getId(), "Small", 1, 1, 1_000);
        final Cities.City big = world.cities().create(countryB.getId(), "Big", 2, 2, 1_000_000);
        final Airports.Airport shared = world.airports().create(1, 1, "AAA", "AAAA", "Shared");
        final Airports.Airport single = world.airports().create(2, 2, "BBB", "BBBB", "Single");
        final Airports.Airport alone = world.airports().create(3, 3, "CCC", "CCCC", "Alone");
        world.airport2city().create(shared.getId(), small.getId());
        world.airport2city().create(shared.getId(), big.getId());
        world.airport2city().create(single.getId(), small.getId());
        world.save();

        final World loaded = World.load(strategy);

        assertEquals(countryB.getId(), loaded.airports().byId(shared.getId()).orElseThrow().getCountryId());
        assertEquals(countryA.getId(), loaded.airports().byId(single.getId()).orElseThrow().getCountryId());
        assertEquals(0, loaded.airports().byId(alone.getId()).orElseThrow().getCountryId());

        // a value which was set by hand is not overwritten by the next load
        loaded.airports().byId(shared.getId()).orElseThrow().setCountryId(countryA.getId());
        loaded.save();
        final World reloaded = World.load(strategy);
        assertEquals(countryA.getId(), reloaded.airports().byId(shared.getId()).orElseThrow().getCountryId());
    }
}
