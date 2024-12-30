package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.world.World;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

public class Events {
    private final Storage<Event> storage = Storage.<Event>builder()
            .name("events")
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

    private Events(final World world) throws IOException {
        this.storage.setRootPath(world.getRootPath());
        this.storage.loadIfExists();
    }

    public static Events loadOrCreate(final World world) throws IOException {
        return new Events(world);
    }

    public void save() throws IOException {
        storage.save();
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
        PilotOnDuty(100);

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
