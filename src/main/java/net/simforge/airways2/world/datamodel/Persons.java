package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Persons of the world who have their own life: pilots and cabin crew (passengers are not persons, they are journeys).
 * It is one entity for a pilot and for a person, see docs/persons-pilots.md.
 */
public class Persons {
    private final Storage<Person> storage = Storage.<Person>builder()
            .name("persons")
            .withInstantiator(Person::new)
            .withIdOf(DataType.Unsigned24bit)
            // -------------------------------------------------- record deletion status    1 byte
            .withDataField(DataField.of(DataType.Unsigned8bit)) // type                     1 byte
            .withDataField(DataField.of(DataType.Unsigned8bit)) // state                    1 byte
            .withDataField(DataField.of(DataType.Signed32bit)) // heartbeatTime             4 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // userId, 0 is NPC        2 bytes
            .withDataField(DataField.of(DataType.Unsigned8bit)) // locationType             1 byte
            .withDataField(DataField.of(DataType.Unsigned16bit)) // locationId              2 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // originCityId            2 bytes
            .withDataField(DataField.of(DataType.PlainString).length(2)) // initials        3 bytes (length byte + 2 chars)
            .withDataField(DataField.of(DataType.Unsigned16bit)) // avatarId                2 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // bankAccountId, 0 is none  3 bytes
            // ----------------------------------------------------- sum                   22 bytes
            // -------------------------------------------- reserved 10 bytes to align total size to 32 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // reserve                 2 bytes
            .withHeartbeatIndex(2) // heartbeatField index: position among withDataField calls (type=0, state=1, heartbeatTime=2)
            .build();

    private final DataField typeField = storage.getDataField(0);
    private final DataField stateField = storage.getDataField(1);
    private final DataField heartbeatTimeField = storage.getDataField(2);
    private final DataField userIdField = storage.getDataField(3);
    private final DataField locationTypeField = storage.getDataField(4);
    private final DataField locationIdField = storage.getDataField(5);
    private final DataField originCityIdField = storage.getDataField(6);
    private final DataField initialsField = storage.getDataField(7);
    private final DataField avatarIdField = storage.getDataField(8);
    private final DataField bankAccountIdField = storage.getDataField(9);

    private static final int MAX_INITIALS_LENGTH = 2;

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Stream<Person> all() {
        return storage.all();
    }

    public Stream<Person> filter(final Storage.Condition<Person> condition) {
        return storage.filter(condition);
    }

    public Optional<Person> findFirst(final Storage.Condition<Person> condition) {
        return storage.findFirst(condition);
    }

    public Optional<Person> byId(final int id) {
        return storage.byId(id);
    }

    /**
     * The player character of the user, a scan: there are few persons which belong to users.
     */
    public Optional<Person> byUserId(final int userId) {
        checkArgument(userId > 0, "userId is mandatory");
        return storage.findFirst(recordId -> storage.getAsInt(recordId, userIdField) == userId);
    }

    public Optional<Person> nextForHeartbeat(final int worldTime) {
        return storage.nextForHeartbeat(worldTime);
    }

    /**
     * Differences between the heartbeat index and the data, an empty list means they are consistent (a scan, for tests and diagnostics).
     */
    public List<String> checkHeartbeatIndex() {
        return storage.checkHeartbeatIndex();
    }

    /**
     * @param userId zero for NPC, the user of the player character otherwise
     */
    public Person create(final Type type,
                         final State state,
                         final int userId,
                         final LocationType locationType,
                         final int locationId,
                         final int originCityId,
                         final String initials,
                         final int avatarId) {
        checkNotNull(type);
        checkNotNull(state);
        checkNotNull(locationType);

        final int id = storage.addRecord();
        final Person person = new Person(id);
        person.setType(type);
        person.setState(state);
        person.setHeartbeatTime(0);
        person.setUserId(userId);
        person.setLocation(locationType, locationId);
        person.setOriginCityId(originCityId);
        person.setInitials(initials);
        person.setAvatarId(avatarId);
        return person;
    }

    public void deleteById(final int id) {
        storage.deleteRecord(id);
    }

    @SuppressWarnings("LombokGetterMayBeUsed")
    public class Person {
        private final int id;

        private Person(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public Type getType() {
            return Type.byCode(storage.getAsInt(id, typeField));
        }

        public void setType(final Type type) {
            checkNotNull(type);
            storage.set(id, typeField, type.code());
        }

        public State getState() {
            return State.byCode(storage.getAsInt(id, stateField));
        }

        public void setState(final State state) {
            checkNotNull(state);
            storage.set(id, stateField, state.code());
        }

        public int getHeartbeatTime() {
            return storage.getAsInt(id, heartbeatTimeField);
        }

        public void setHeartbeatTime(final int heartbeatTime) {
            storage.set(id, heartbeatTimeField, heartbeatTime);
        }

        /**
         * Zero for NPC.
         */
        public int getUserId() {
            return storage.getAsInt(id, userIdField);
        }

        public void setUserId(final int userId) {
            checkArgument(DataType.Unsigned16bit.contains(userId), "userId is out of 16 bits");
            storage.set(id, userIdField, userId);
        }

        public boolean isPlayerCharacter() {
            return getUserId() != 0;
        }

        public LocationType getLocationType() {
            return LocationType.byCode(storage.getAsInt(id, locationTypeField));
        }

        public int getLocationId() {
            return storage.getAsInt(id, locationIdField);
        }

        public void setLocation(final LocationType locationType, final int locationId) {
            checkNotNull(locationType);
            checkArgument(DataType.Unsigned16bit.contains(locationId), "locationId is out of 16 bits");
            checkArgument(locationType != LocationType.None || locationId == 0, "a person who is nowhere has no location id");
            storage.set(id, locationTypeField, locationType.code());
            storage.set(id, locationIdField, locationId);
        }

        public int getOriginCityId() {
            return storage.getAsInt(id, originCityIdField);
        }

        public void setOriginCityId(final int originCityId) {
            checkArgument(DataType.Unsigned16bit.contains(originCityId), "originCityId is out of 16 bits");
            storage.set(id, originCityIdField, originCityId);
        }

        /**
         * Up to two letters, empty if not set.
         */
        public String getInitials() {
            final String initials = storage.getAsString(id, initialsField);
            return initials != null ? initials : "";
        }

        public void setInitials(final String initials) {
            final String value = initials != null ? initials : "";
            checkArgument(value.length() <= MAX_INITIALS_LENGTH, "initials are two letters at most");
            storage.set(id, initialsField, value);
        }

        public int getAvatarId() {
            return storage.getAsInt(id, avatarIdField);
        }

        public void setAvatarId(final int avatarId) {
            checkArgument(DataType.Unsigned16bit.contains(avatarId), "avatarId is out of 16 bits");
            storage.set(id, avatarIdField, avatarId);
        }

        /**
         * The bank account of the person, zero if it has none yet. The account refers to its owner (BankAccounts, kind Person) and the person refers to its
         * account, so each of them is found directly, without scanning the other storage.
         */
        public int getBankAccountId() {
            return storage.getAsInt(id, bankAccountIdField);
        }

        public void setBankAccountId(final int bankAccountId) {
            checkArgument(DataType.Unsigned24bit.contains(bankAccountId), "bankAccountId is out of 24 bits");
            storage.set(id, bankAccountIdField, bankAccountId);
        }

        @Override
        public String toString() {
            return String.format("{ id: %s, type: %s, state: %s }", id, getType(), getState());
        }
    }

    public enum Type {
        Pilot(1),
        CabinCrew(2);

        private final int code;

        Type(final int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static Type byCode(final int code) {
            return Arrays.stream(Type.values())
                    .filter(type -> type.code == code)
                    .findFirst()
                    .orElse(null);
        }
    }

    public enum State {
        Idle(1),
        OnDuty(2),
        Travelling(3),
        Resting(4);

        private final int code;

        State(final int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static State byCode(final int code) {
            return Arrays.stream(State.values())
                    .filter(state -> state.code == code)
                    .findFirst()
                    .orElse(null);
        }
    }

    public enum LocationType {
        // nowhere or on the way: in the flight, on the way to a hotel
        None(0),
        City(1),
        Airport(2),
        Hotel(3);

        private final int code;

        LocationType(final int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static LocationType byCode(final int code) {
            return Arrays.stream(LocationType.values())
                    .filter(locationType -> locationType.code == code)
                    .findFirst()
                    .orElse(null);
        }
    }
}
