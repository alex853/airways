package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

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

    public Event sendEvent(final Type type,
                           final int objectId,
                           final int time) {
        checkNotNull(type, "type is mandatory");

        final int recordId = storage.addRecord();
        storage.set(recordId, statusField, Status.Active.ordinal());
        storage.set(recordId, typeField, type.code());
        storage.set(recordId, timeField, time);
        storage.set(recordId, objectIdField, objectId);
        return new Event(recordId);
    }

    public Optional<Event> findFirstActiveEvent(final Type type, int worldTime) {
        checkNotNull(type, "type is mandatory");

        return storage.findFirst(event -> event.getStatus() == Status.Active
                && event.getType() == type
                && event.getTime() <= worldTime);
    }

    public Collection<Event> all() {
        return storage.all();
    }

    public Collection<Event> filter(final Predicate<EventsToProcess.Event> condition) {
        return storage.filter(condition);
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
            return storage.getAsInt(id, statusField);
        }

        public Type getType() {
            return Type.byCode(getTypeRaw());
        }

        public int getTypeRaw() {
            return storage.getAsInt(id, typeField);
        }

        public int getTime() {
            return storage.getAsInt(id, timeField);
        }

        public int getObjectId() {
            return storage.getAsInt(id, objectIdField);
        }
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
