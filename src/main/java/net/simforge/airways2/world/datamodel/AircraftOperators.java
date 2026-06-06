package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class AircraftOperators {
    private final Storage<AircraftOperator> storage = Storage.<AircraftOperator>builder()
            .name("aircraft-operators")
            .withInstantiator(AircraftOperator::new)
            .withIdOf(DataType.Unsigned16bit)
            .withDataField(DataField.of(DataType.PlainString).length(2)) // iata
            .withDataField(DataField.of(DataType.PlainString).length(3)) // icao
            .withDataField(DataField.of(DataType.PlainString).length(30)) // name
            .build();

    private final DataField iataField = storage.getDataField(0);
    private final DataField icaoField = storage.getDataField(1);
    private final DataField nameField = storage.getDataField(2);

    public AircraftOperators() {
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Stream<AircraftOperator> all() {
        return storage.all();
    }

    public Optional<AircraftOperator> byId(final int id) {
        return storage.byId(id);
    }

    public Optional<AircraftOperator> byIata(final String iata) {
        checkNotNull(iata, "iata is mandatory");
        return storage.findFirst(recordId -> iata.equals(readIata(recordId)));
    }

    public Optional<AircraftOperator> byIcao(final String icao) {
        checkNotNull(icao, "icao is mandatory");
        return storage.findFirst(recordId -> icao.equals(readIcao(recordId)));
    }

    public AircraftOperator create(final String iata,
                                   final String icao,
                                   final String name) {
        checkNotNull(iata, "iata should be specified");
        checkNotNull(icao, "icao should be specified");
        checkArgument(byIata(iata).isEmpty(), "iata should be unique");
        checkArgument(byIcao(icao).isEmpty(), "icao should be unique");
        checkNotNull(name, "name should be specified");

        final int recordId = storage.addRecord();
        storage.set(recordId, iataField, iata);
        storage.set(recordId, icaoField, icao);
        storage.set(recordId, nameField, name);
        return new AircraftOperator(recordId);
    }

    public class AircraftOperator {
        private final int id;

        private AircraftOperator(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        @SuppressWarnings("unused")
        public String getIata() {
            return readIata(id);
        }

        public String getIcao() {
            return readIcao(id);
        }

        public String getName() {
            return readName(id);
        }
    }

    private String readIata(int recordId) {
        return storage.getAsString(recordId, iataField);
    }

    private String readIcao(int recordId) {
        return storage.getAsString(recordId, icaoField);
    }

    private String readName(int recordId) {
        return storage.getAsString(recordId, nameField);
    }
}
