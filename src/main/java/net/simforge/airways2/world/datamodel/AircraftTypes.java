package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

public class AircraftTypes {
    private final Storage<AircraftType> storage = Storage.<AircraftType>builder()
            .name("aircraft-types")
            .withInstantiator(AircraftType::new)
            .withIdOf(DataType.Unsigned16bit)
            .withDataField(DataField.of(DataType.PlainString).length(4)) // icao
            .withDataField(DataField.of(DataType.PlainString).length(3)) // iata
            .build();

    private final DataField icaoField = storage.getDataField(0);
    private final DataField iataField = storage.getDataField(1);

    public AircraftTypes() {
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public AircraftType create(final String icao,
                               final String iata) {
        final int recordId = storage.addRecord();
        storage.set(recordId, icaoField, icao);
        storage.set(recordId, iataField, iata);
        return new AircraftType(recordId);
    }

    public Stream<AircraftType> all() {
        return storage.all();
    }

    public Optional<AircraftType> byId(final int aircraftTypeId) {
        return storage.byId(aircraftTypeId);
    }

    public Optional<AircraftType> byIcao(final String icao) {
        checkNotNull(icao, "icao is mandatory");
        return storage.findFirst(recordId -> icao.equals(readIcao(recordId)));
    }

    public class AircraftType {
        private final int id;

        private AircraftType(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public String getIcao() {
            return readIcao(id);
        }

        @SuppressWarnings("unused")
        public String getIata() {
            return storage.getAsString(id, iataField);
        }
    }

    private String readIcao(int recordId) {
        return storage.getAsString(recordId, icaoField);
    }
}
