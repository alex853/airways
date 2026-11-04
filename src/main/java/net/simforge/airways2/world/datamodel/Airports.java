package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.BitAccessField;
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

public class Airports {
    private final Strings strings;

    // The record is 20 bytes as it was: the fixed length strings (no length bytes) gave two bytes for countryId and the flags.
    private final Storage<Airport> storage = Storage.<Airport>builder()
            .name("airports")
            .withInstantiator(Airport::new)
            .withIdOf(DataType.Unsigned16bit)
            .withDataField(DataField.of(DataType.LatLong24bit)) // latitude
            .withDataField(DataField.of(DataType.LatLong24bit)) // longitude
            .withDataField(DataField.of(DataType.Unsigned8bit)) // countryId, zero means 'unknown'
            .withDataField(DataField.of(DataType.FixedLengthString).length(3)) // iata
            .withDataField(DataField.of(DataType.Unsigned8bit)) // flags, the bits are to be defined
            .withDataField(DataField.of(DataType.FixedLengthString).length(4)) // icao
            .withDataField(DataField.of(DataType.Signed32bit)) // nameId
            .build();

    private final DataField latitudeField = storage.getDataField(0);
    private final DataField longitudeField = storage.getDataField(1);
    private final DataField countryIdField = storage.getDataField(2);
    private final DataField iataField = storage.getDataField(3);
    private final DataField flagsField = storage.getDataField(4);
    private final DataField icaoField = storage.getDataField(5);
    private final DataField nameIdField = storage.getDataField(6);

    private final BitAccessField flagsBits = BitAccessField.instance(storage, flagsField);
    private final BitAccessField.Section excludedBit = flagsBits.section(0, 1); // the airport does not take part in the flights
    private final BitAccessField.Section hubBit = flagsBits.section(1, 1); // the airport is a hub, just a mark

    public Airports(final Strings strings) {
        this.strings = strings;
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Airport create(final double latitude,
                          final double longitude,
                          final String iata,
                          final String icao,
                          final String name) {
        final int recordId = storage.addRecord();
        storage.set(recordId, latitudeField, (float) latitude);
        storage.set(recordId, longitudeField, (float) longitude);
        storage.set(recordId, iataField, iata);
        storage.set(recordId, icaoField, icao);
        storage.set(recordId, nameIdField, strings.findOrAdd(name));
        return new Airport(recordId);
    }

    public Optional<Airport> byId(final int airportId) {
        return storage.byId(airportId);
    }

    public Optional<Airport> byIcao(final String icao) {
        checkNotNull(icao, "icao should not be null");
        return storage.findFirst(recordId -> icao.equals(readIcao(recordId)));
    }

    /**
     * The moment of the last change of the storage, see {@link Storage#getLastChangedAtMillis()}.
     */
    public long getLastChangedAtMillis() {
        return storage.getLastChangedAtMillis();
    }

    public Stream<Airport> all() {
        return storage.all();
    }

    public Optional<String> getIcao(final int airportId) {
        return byId(airportId).map(Airport::getIcao);
    }

    public class Airport {
        private final int id;

        private Airport(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public float getLatitude() {
            return storage.getAsFloat(id, latitudeField);
        }

        public float getLongitude() {
            return storage.getAsFloat(id, longitudeField);
        }

        public String getIata() {
            return storage.getAsString(id, iataField);
        }

        public String getIcao() {
            return readIcao(id);
        }

        public int getCountryId() {
            return storage.getAsInt(id, countryIdField);
        }

        public void setCountryId(final int countryId) {
            storage.set(id, countryIdField, countryId);
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean isExcluded() {
            return excludedBit.getBoolean(id);
        }

        public void setExcluded(final boolean excluded) {
            excludedBit.setBoolean(id, excluded);
        }

        public boolean isHub() {
            return hubBit.getBoolean(id);
        }

        public void setHub(final boolean hub) {
            hubBit.setBoolean(id, hub);
        }

        public String getName() {
            return strings.byId(storage.getAsInt(id, nameIdField));
        }

        public void setName(final String name) {
            storage.set(id, nameIdField, strings.findOrAdd(name));
        }

        public Geo.Coords getCoords() {
            return Geo.coords(getLatitude(), getLongitude());
        }

        @Override
        public String toString() {
            return "{ icao: " + getIcao() + " }";
        }
    }

    private String readIcao(int recordId) {
        return storage.getAsString(recordId, icaoField);
    }
}
