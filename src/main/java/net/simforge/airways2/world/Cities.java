package net.simforge.airways2.world;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

public class Cities {
    private final Storage<City> storage = Storage.<City>builder()
            .name("cities")
            .withInstantiator(City::new)
            .withIdOf(DataType.Unsigned16bit)
            .withDataField(DataField.of(DataType.Unsigned8bit)) // countryId
            .withDataField(DataField.of(DataType.LatLong24bit)) // latitude
            .withDataField(DataField.of(DataType.LatLong24bit)) // longitude
            .withDataField(DataField.of(DataType.Signed32bit)) // population
            .withDataField(DataField.of(DataType.PlainString).length(20)) // name
            .build();

    private final DataField countryIdField = storage.getDataField(0);
    private final DataField latitudeField = storage.getDataField(1);
    private final DataField longitudeField = storage.getDataField(2);
    private final DataField populationField = storage.getDataField(3);
    private final DataField nameField = storage.getDataField(4);

    private Cities() {}

    public static Cities loadOrCreate() throws IOException {
        final Cities cities = new Cities();
        cities.storage.loadIfExists();
        return cities;
    }

    public void save() throws IOException {
        storage.save();
    }

    public Optional<City> byId(final int cityId) {
        return storage.byId(cityId);
    }

    public Optional<City> byCountryIdAndName(final int countryId, final String cityName) {
        checkNotNull(cityName, "cityName should not be null");
        return storage.findFirst(c -> c.getCountryId() == countryId && cityName.equals(c.getName()));
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
        storage.set(recordId, nameField, name);
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
            return storage.getAsInt(id, countryIdField);
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

        public String getName() {
            return storage.getAsString(id, nameField);
        }
    }
}
