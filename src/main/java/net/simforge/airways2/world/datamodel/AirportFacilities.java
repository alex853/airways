package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
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
        checkArgument(!hasFacility(airport, type));
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
        checkArgument(!hasFacility(airport, aircraftOperator, type));
        final int recordId = storage.addRecord();
        storage.set(recordId, airportIdField, airport.getId());
        storage.set(recordId, aircraftOperatorIdField, aircraftOperator.getId());
        storage.set(recordId, typeField, type.code());
        return new Facility(recordId);
    }

    public boolean hasFacility(final Airports.Airport airport, final Type type) {
        checkNotNull(airport);
        checkNotNull(type);
        return storage.filter1(recordId -> readAirportId(recordId) == airport.getId()
                        && readType(recordId) == type)
                .findAny()
                .isPresent();
    }

    public boolean hasFacility(final Airports.Airport airport, final AircraftOperators.AircraftOperator aircraftOperator, final Type type) {
        checkNotNull(airport);
        checkNotNull(aircraftOperator);
        checkNotNull(type);
        return storage.filter1(recordId -> readAirportId(recordId) == airport.getId()
                        && readAircraftOperatorId(recordId) == aircraftOperator.getId()
                        && readType(recordId) == type)
                .findAny()
                .isPresent();
    }

    public Stream<Facility> by(final AircraftOperators.AircraftOperator aircraftOperator, final Type type) {
        checkNotNull(aircraftOperator);
        checkNotNull(type);
        return storage.filter1(recordId -> readAircraftOperatorId(recordId) == aircraftOperator.getId()
                && readType(recordId) == type);
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
            return readAirportId(id);
        }

        public int getAircraftOperatorId() {
            return readAircraftOperatorId(id);
        }

        public Type getType() {
            return readType(id);
        }
    }

    private int readAirportId(int recordId) {
        return storage.getAsInt(recordId, airportIdField);
    }

    private int readAircraftOperatorId(final int recordId) {
        return storage.getAsInt(recordId, aircraftOperatorIdField);
    }

    private Type readType(final int recordId) {
        return Type.byCode(storage.getAsInt(recordId, typeField));
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
