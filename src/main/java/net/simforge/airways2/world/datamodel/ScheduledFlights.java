package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

public class ScheduledFlights {
    private final Storage<Flight> storage = Storage.<Flight>builder()
            .name("scheduled-flights")
            .withInstantiator(Flight::new)
            .withIdOf(DataType.Unsigned24bit)
            .withDataField(DataField.of(DataType.Unsigned8bit)) // reserved - status+mode (in binary form?)
            .withDataField(DataField.of(DataType.Unsigned16bit)) // scheduledId
            .withDataField(DataField.of(DataType.Unsigned24bit)) // flightMissionId
            .build();

    @SuppressWarnings("unused")
    private final DataField reservedByteField = storage.getDataField(0);
    private final DataField scheduleIdField = storage.getDataField(1);
    private final DataField flightMissionIdField = storage.getDataField(2);

    public ScheduledFlights() {
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Stream<Flight> all() {
        return storage.all();
    }

    public Optional<Flight> byId(final int id) {
        return storage.byId(id);
    }

    public Stream<Flight> byScheduleId(final int scheduleId) {
        checkArgument(scheduleId > 0);

        return storage.filter1(recordId -> readScheduleId(recordId) == scheduleId);
    }

    public Flight create(final int scheduleId, final int flightMissionId) {
        final int recordId = storage.addRecord();
        storage.set(recordId, scheduleIdField, scheduleId);
        storage.set(recordId, flightMissionIdField, flightMissionId);
        return new Flight(recordId);
    }

    public void deleteById(final int id) {
        storage.deleteRecord(id);
    }

    @SuppressWarnings("LombokGetterMayBeUsed")
    public class Flight {
        private final int id;

        private Flight(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public int getScheduleId() {
            return readScheduleId(id);
        }

        public int getFlightMissionId() {
            return storage.getAsInt(id, flightMissionIdField);
        }
    }

    private int readScheduleId(int recordId) {
        return storage.getAsInt(recordId, scheduleIdField);
    }
}
