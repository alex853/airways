package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.BitAccessField;
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
            // two letters of 63 values each (a space and 62 letters and digits) are 12 bits, see INITIALS_ALPHABET; the highest bit is the feature flag of the warp drive.
            // The reserve byte is the third byte of the old PlainString (length byte + 2 chars): it is kept, so the fields after it stay where they were
            .withDataField(DataField.of(DataType.Unsigned16bit)) // initials (bits 0-11), warpDrive (bit 15)  2 bytes
            .withDataField(DataField.of(DataType.Unsigned8bit)) // reserve                  1 byte
            .withDataField(DataField.of(DataType.Unsigned16bit)) // avatarId                2 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // bankAccountId, 0 is none  3 bytes
            .withDataField(DataField.of(DataType.Signed16bit)) // awakeCreditMinutes, see PilotRest  2 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // creditTime, when the credit was fixed (falling asleep or waking up), 0 is not started  4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // dutyStartTime, when the flight in progress was started, 0 is no flight in progress  4 bytes
            // ----------------------------------------------------- sum                   32 bytes
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
    // storage.getDataField(8) is the reserve byte
    private final DataField avatarIdField = storage.getDataField(9);
    private final DataField bankAccountIdField = storage.getDataField(10);
    private final DataField awakeCreditMinutesField = storage.getDataField(11);
    private final DataField creditTimeField = storage.getDataField(12);
    private final DataField dutyStartTimeField = storage.getDataField(13);

    private final BitAccessField initialsBits = BitAccessField.instance(storage, initialsField);
    private final BitAccessField.Section initialsSection = initialsBits.section(0, 12);
    private final BitAccessField.Section warpDriveBit = initialsBits.section(15, 1);

    private static final int MAX_INITIALS_LENGTH = 2;

    // The letters of the initials: a space (an empty place, the first one, so no initials are zero) and the 62 letters and digits. Two letters are the number
    // first * 63 + second, it is less than 63 * 63 = 3969 and fits 12 bits. The order of the alphabet is part of the format: never change it.
    static final String INITIALS_ALPHABET = " ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int INITIALS_BASE = INITIALS_ALPHABET.length();

    /**
     * The initials as the number which is stored: up to two English letters or digits, a missing letter is a space. The empty initials are zero.
     */
    static int encodeInitials(final String initials) {
        final String value = initials != null ? initials : "";
        checkArgument(value.length() <= MAX_INITIALS_LENGTH, "initials are two letters at most");
        int number = 0;
        for (int i = 0; i < MAX_INITIALS_LENGTH; i++) {
            final boolean present = i < value.length();
            final int index = present ? INITIALS_ALPHABET.indexOf(value.charAt(i)) : 0;
            // a space is only the empty place, it is not a letter of the initials
            checkArgument(index >= 0 && !(present && value.charAt(i) == ' '), "initials are English letters and digits only");
            number = number * INITIALS_BASE + index;
        }
        return number;
    }

    /**
     * The initials of the stored number, empty if the number is not valid (the bytes of an old format, nothing was written yet).
     */
    static String decodeInitials(final int number) {
        if (number < 0 || number >= INITIALS_BASE * INITIALS_BASE) {
            return "";
        }
        final String initials = "" + INITIALS_ALPHABET.charAt(number / INITIALS_BASE) + INITIALS_ALPHABET.charAt(number % INITIALS_BASE);
        return initials.replace(" ", "");
    }

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

    /**
     * The persons who sleep (Resting) and have no heartbeat: they were put to sleep before the wake up was scheduled, nothing is going to wake them up.
     * A scan, it is for the start of the application only.
     */
    public Stream<Person> sleepingWithoutHeartbeat() {
        final Storage.Condition<Person> noHeartbeat = storage.byZeroHeartbeat(heartbeatTimeField);
        final Storage.Condition<Person> resting = recordId -> storage.getAsInt(recordId, stateField) == State.Resting.code();
        return storage.filter(Storage.Condition.and(noHeartbeat, resting));
    }

    /**
     * The persons who are free (Idle) and have no heartbeat: nothing is going to put them to sleep. A scan, it is for the start of the application only.
     */
    public Stream<Person> idleWithoutHeartbeat() {
        final Storage.Condition<Person> noHeartbeat = storage.byZeroHeartbeat(heartbeatTimeField);
        final Storage.Condition<Person> idle = recordId -> storage.getAsInt(recordId, stateField) == State.Idle.code();
        return storage.filter(Storage.Condition.and(noHeartbeat, idle));
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
            return decodeInitials(initialsSection.getInt(id));
        }

        public void setInitials(final String initials) {
            initialsSection.setInt(id, encodeInitials(initials));
        }

        /**
         * The feature flag of the warp drive (the pilot may move to any airport at once): it is switched by hand for the one who has it, see the admin
         * endpoint POST /admin/person/{id}/warp-drive.
         */
        public boolean isWarpDrive() {
            return warpDriveBit.getBoolean(id);
        }

        public void setWarpDrive(final boolean warpDrive) {
            warpDriveBit.setBoolean(id, warpDrive);
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
        /**
         * The awake credit in minutes at the moment of getCreditTime(): how long the person can stay awake and active. It is negative when the person is
         * overtired. What it is now (it falls while the person is awake) is told by PilotRest.
         */
        public int getAwakeCreditMinutes() {
            return storage.getAsInt(id, awakeCreditMinutesField);
        }

        /**
         * The moment the awake credit was fixed: the person fell asleep or woke up. Zero means that the credit has not been started yet.
         */
        public int getCreditTime() {
            return storage.getAsInt(id, creditTimeField);
        }

        public void setAwakeCredit(final int awakeCreditMinutes, final int creditTime) {
            checkArgument(DataType.Signed16bit.contains(awakeCreditMinutes), "awake credit is out of 16 bits: %s", awakeCreditMinutes);
            storage.set(id, awakeCreditMinutesField, awakeCreditMinutes);
            storage.set(id, creditTimeField, creditTime);
        }

        /**
         * The moment (world time) the person went on duty: the flight which is in progress was started. Zero when there is no such flight.
         */
        public int getDutyStartTime() {
            return storage.getAsInt(id, dutyStartTimeField);
        }

        public void setDutyStartTime(final int dutyStartTime) {
            storage.set(id, dutyStartTimeField, dutyStartTime);
        }

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
