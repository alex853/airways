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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class Airports {
    private static final Logger log = LoggerFactory.getLogger(Airports.class);

    private final Strings strings;

    // The record is 20 bytes as it was: the fixed length strings (no length bytes) gave two bytes for countryId and the flags. The place of the bytes is the same as of the
    // former length bytes of iata and icao, see migrateFormerLengthBytes().
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

    public Airports(final Strings strings) {
        this.strings = strings;
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
        migrateFormerLengthBytes();
    }

    /**
     * todo remove after the first deploy and the first save of the world (the file is migrated by then).
     * The file used to hold iata and icao as length-prefixed strings. The new fields countryId and flags are at the places of the length bytes, so an old file shows the lengths
     * in them. It is the old file when every airport has the length of its iata in countryId and the length of its icao in flags (no length for a null), and at least one
     * airport has an icao (a migrated file has zeros). Then the two bytes are zeroed. A migrated file never looks like that.
     */
    void migrateFormerLengthBytes() {
        boolean anyIcao = false;
        for (final Airport airport : (Iterable<Airport>) storage.all()::iterator) {
            final int id = airport.getId();
            if (!isFormerLengthByte(storage.getAsInt(id, countryIdField), airport.getIata())
                    || !isFormerLengthByte(storage.getAsInt(id, flagsField), airport.getIcao())) {
                return;
            }
            anyIcao |= airport.getIcao() != null;
        }
        if (!anyIcao) {
            return;
        }
        storage.all().forEach(airport -> {
            storage.set(airport.getId(), countryIdField, 0);
            storage.set(airport.getId(), flagsField, 0);
        });
        log.info("a/p - the former length bytes of iata and icao are zeroed in {} airports (countryId and flags)", storage.getCount());
    }

    private static boolean isFormerLengthByte(final int value, final String string) {
        return string == null ? value == 0 || value == 255 : value == string.length();
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

        public int getFlags() {
            return storage.getAsInt(id, flagsField);
        }

        public void setFlags(final int flags) {
            storage.set(id, flagsField, flags);
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

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean isExcluded() { // todo ak0 rework it into 'exclusion' field, or status, or something like that
            String icao = getIcao();
            return "LFPY".equals(icao)
                    || "EDDT".equals(icao)
                    || "LGAT".equals(icao)
                    || "CGAS".equals(icao);
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
