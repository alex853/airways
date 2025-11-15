package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;

public class AirportFacilities {
    private final Storage<Facility> storage = Storage.<Facility>builder()
            .name("airport-facilities")
            .withInstantiator(Facility::new)
            .withIdOf(DataType.Unsigned24bit)
            .withDataField(DataField.of(DataType.Unsigned16bit)) // airportId
            .withDataField(DataField.of(DataType.Unsigned16bit)) // aircraftOperatorId
            .withDataField(DataField.of(DataType.Unsigned8bit)) // type
            // --- reserve 7 bytes --------------------------------------
            .withDataField(DataField.of(DataType.Unsigned24bit))
            .withDataField(DataField.of(DataType.Signed32bit))
            .build();

    private final DataField airportIdField = storage.getDataField(0);
    private final DataField aircraftOperatorIdField = storage.getDataField(1);
    private final DataField typeField = storage.getDataField(2);

    public AirportFacilities() {
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Facility create(final Airports.Airport airport,
                           final Type type) {
        checkNotNull(airport);
        checkNotNull(type);
        // todo ak check for presence
        final int recordId = storage.addRecord();
        storage.set(recordId, airportIdField, airport.getId());
        storage.set(recordId, aircraftOperatorIdField, 0);
        storage.set(recordId, typeField, type.code());
        return new Facility(recordId);
    }

    public Facility create(final Airports.Airport airport,
                           final AircraftOperators.AircraftOperator aircraftOperator,
                           final Type type) {
        checkNotNull(airport);
        checkNotNull(aircraftOperator);
        checkNotNull(type);
        // todo ak check for presence
        final int recordId = storage.addRecord();
        storage.set(recordId, airportIdField, airport.getId());
        storage.set(recordId, aircraftOperatorIdField, aircraftOperator.getId());
        storage.set(recordId, typeField, type.code());
        return new Facility(recordId);
    }

    public class Facility {
        private final int id;

        private Facility(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public int getAirportId() {
            return storage.getAsInt(id, airportIdField);
        }

        public int getAircraftOperatorId() {
            return storage.getAsInt(id, aircraftOperatorIdField);
        }

        public int getTypeRaw() {
            return storage.getAsInt(id, typeField);
        }

        public Type getType() {
            return Type.byCode(getTypeRaw());
        }
    }

    public enum Type {
        BusinessAviationTerminal(2),
        BaseAirport(4);

        private final int code;

        Type(final int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static Type byCode(final int code) {
            return Arrays.stream(Type.values())
                    .filter(status -> status.code == code)
                    .findFirst()
                    .orElse(null);
        }
    }
}
