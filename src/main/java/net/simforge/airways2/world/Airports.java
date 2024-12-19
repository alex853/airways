package net.simforge.airways2.world;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.storage.Strings;

import java.io.IOException;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

public class Airports {
    private final World world;
    private final Strings strings;

    private final Storage<Airport> storage = Storage.<Airport>builder()
            .name("airports")
            .withInstantiator(Airport::new)
            .withIdOf(DataType.Unsigned16bit)
            .withDataField(DataField.of(DataType.LatLong24bit)) // latitude
            .withDataField(DataField.of(DataType.LatLong24bit)) // longitude
            .withDataField(DataField.of(DataType.PlainString).length(3)) // iata
            .withDataField(DataField.of(DataType.PlainString).length(4)) // icao
            .withDataField(DataField.of(DataType.Signed32bit)) // nameId
            .build();

    private final DataField latitudeField = storage.getDataField(0);
    private final DataField longitudeField = storage.getDataField(1);
    private final DataField iataField = storage.getDataField(2);
    private final DataField icaoField = storage.getDataField(3);
    private final DataField nameIdField = storage.getDataField(4);

    private Airports(final World world) throws IOException {
        this.world = world;
        this.strings = world.strings();
        this.storage.setRootPath(world.getRootPath());
        this.storage.loadIfExists();
    }

    public static Airports loadOrCreate(final World world) throws IOException {
        return new Airports(world);
    }

    void save() throws IOException {
        storage.save();
    }

    public Optional<Airport> byId(final int airportId) {
        return storage.byId(airportId);
    }

    public Optional<Airport> byIcao(final String icao) {
        checkNotNull(icao, "icao should not be null");
        return storage.findFirst(a -> icao.equals(a.getIcao()));
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
            return storage.getAsString(id, icaoField);
        }

        public String getName() {
            return strings.byId(storage.getAsInt(id, nameIdField));
        }
    }
}
