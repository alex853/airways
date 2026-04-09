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

public class Airports {
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
        return storage.findFirst(a -> icao.equals(a.getIcao()));
    }

    public Stream<Airport> all() {
        return storage.all();
    }

    public String getIcao(final int airportId) {
        return byId(airportId).orElseThrow().getIcao();
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

        public Geo.Coords getCoords() {
            return Geo.coords(getLatitude(), getLongitude());
        }

        public boolean isExcluded() { // todo ak3 extend airports storage and add some field to support 'exclusion';
            String icao = getIcao();
            return "LFPY".equals(icao)
                    || "EDDT".equals(icao);
        }

        @Override
        public String toString() {
            return "{ icao: " + getIcao() + " }";
        }
    }
}
