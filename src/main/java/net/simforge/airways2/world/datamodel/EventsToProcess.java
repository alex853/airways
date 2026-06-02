package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

public class EventsToProcess {
    private final Storage<Event> storage = Storage.<Event>builder()
            .name("events-to-process")
            .withInstantiator(Event::new)
            .withIdOf(DataType.Signed32bit)
            .withDataField(DataField.of(DataType.Unsigned8bit)) // status
            .withDataField(DataField.of(DataType.Unsigned16bit)) // type
            .withDataField(DataField.of(DataType.Signed32bit)) // time
            .withDataField(DataField.of(DataType.Signed32bit)) // objectId
            .build();

    private final DataField statusField = storage.getDataField(0);
    private final DataField typeField = storage.getDataField(1);
    private final DataField timeField = storage.getDataField(2);
    private final DataField objectIdField = storage.getDataField(3);

    public EventsToProcess() {
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public void sendEvent(final Type type,
                           final int objectId,
                           final int time) {
        checkNotNull(type, "type is mandatory");

        final int recordId = storage.addRecord();
        storage.set(recordId, statusField, Status.Active.ordinal());
        storage.set(recordId, typeField, type.code());
        storage.set(recordId, timeField, time);
        storage.set(recordId, objectIdField, objectId);
    }

    public Optional<Event> findFirstActiveEvent(Type type, int time) {
        checkNotNull(type, "type is mandatory");

        return storage.findFirst1(recordId -> readStatusRaw(recordId) == Status.Active.ordinal()
                && readTypeRaw(recordId) == type.code()
                && readTime(recordId) <= time);
    }

    public Stream<Event> all() {
        return storage.all();
    }

    public Stream<Event> processedOlderThan(int time) {
        return storage.filter1(recordId -> readTime(recordId) <= time
                && readStatusRaw(recordId) == Status.Processed.ordinal());
    }

    public void deleteById(final int id) {
        storage.deleteRecord(id);
    }

    public void printDeletedRecordInfo() {
        storage.printDeletedRecordInfo();
    }

    public class Event {
        private final int id;

        private Event(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public Status getStatus() {
            return Status.values()[getStatusRaw()];
        }

        public void setProcessedStatus() {
            if (getStatus() == Status.Active) {
                storage.set(id, statusField, Status.Processed.ordinal());
            } else {
                throw new IllegalStateException("do not know what to do here");
            }
        }

        public int getStatusRaw() {
            return readStatusRaw(id);
        }

        public Type getType() {
            return Type.byCode(getTypeRaw());
        }

        public int getTypeRaw() {
            return readTypeRaw(id);
        }

        public int getTime() {
            return readTime(id);
        }

        public int getObjectId() {
            return storage.getAsInt(id, objectIdField);
        }
    }

    private int readStatusRaw(int recordId) {
        return storage.getAsInt(recordId, statusField);
    }

    private int readTypeRaw(int recordId) {
        return storage.getAsInt(recordId, typeField);
    }

    private int readTime(int recordId) {
        return storage.getAsInt(recordId, timeField);
    }

    public enum Status {
        Active,
        Processed
    }

    public enum Type {
        PilotOnDuty(100),
        StartAutomaticDeboarding(180),
        ;

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
