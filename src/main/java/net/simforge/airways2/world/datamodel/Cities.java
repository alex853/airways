package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.storage.Strings;
import net.simforge.commons.misc.Geo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

public class Cities {
    private final Strings strings;

    private final Storage<City> storage = Storage.<City>builder()
            .name("cities")
            .withInstantiator(City::new)
            .withIdOf(DataType.Unsigned16bit)
            .withDataField(DataField.of(DataType.Unsigned8bit)) // countryId
            .withDataField(DataField.of(DataType.LatLong24bit)) // latitude
            .withDataField(DataField.of(DataType.LatLong24bit)) // longitude
            .withDataField(DataField.of(DataType.Signed32bit)) // population
            .withDataField(DataField.of(DataType.Signed32bit)) // nameId
            .build();

    private final DataField countryIdField = storage.getDataField(0);
    private final DataField latitudeField = storage.getDataField(1);
    private final DataField longitudeField = storage.getDataField(2);
    private final DataField populationField = storage.getDataField(3);
    private final DataField nameIdField = storage.getDataField(4);

    public Cities(final Strings strings) {
        this.strings = strings;
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Stream<City> all() {
        return storage.all();
    }

    public Optional<City> byId(final int cityId) {
        return storage.byId(cityId);
    }

    public Optional<City> byCountryIdAndName(final int countryId, final String cityName) {
        checkNotNull(cityName, "cityName should not be null");
        return storage.findFirst(recordId-> readCountryId(recordId) == countryId && cityName.equalsIgnoreCase(readName(recordId)));
    }

    public City create(final int countryId,
                       final String name,
                       final double latitude,
                       final double longitude,
                       final int population) {
        final int recordId = storage.addRecord();
        storage.set(recordId, countryIdField, countryId);
        storage.set(recordId, latitudeField, (float) latitude);
        storage.set(recordId, longitudeField, (float) longitude);
        storage.set(recordId, populationField, population);
        storage.set(recordId, nameIdField, strings.findOrAdd(name));
        return new City(recordId);
    }

    public class City {
        private final int id;

        private City(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public int getCountryId() {
            return readCountryId(id);
        }

        public float getLatitude() {
            return storage.getAsFloat(id, latitudeField);
        }

        public float getLongitude() {
            return storage.getAsFloat(id, longitudeField);
        }

        public int getPopulation() {
            return storage.getAsInt(id, populationField);
        }

        public void setPopulation(final int population) {
            storage.set(id, populationField, population);
        }

        public String getName() {
            return readName(id);
        }

        public void setName(final String name) {
            storage.set(id, nameIdField, strings.findOrAdd(name));
        }

        public Geo.Coords getCoords() {
            return Geo.coords(getLatitude(), getLongitude());
        }
    }

    private String readName(int recordId) {
        return strings.byId(storage.getAsInt(recordId, nameIdField));
    }

    private int readCountryId(int recordId) {
        return storage.getAsInt(recordId, countryIdField);
    }
}
