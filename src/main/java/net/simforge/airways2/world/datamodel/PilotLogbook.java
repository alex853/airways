package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.BitAccessField;
import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.world.Time;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Flights of the pilots: one record is one flight of one person, from blocks off to blocks on. It is written when the flight is finished.
 * <p>
 * The times are minutes from the start of the world (2025-01-01), 3 bytes: it is about 31 years. The duration is not stored, it is the
 * difference of the two times. The type of the aircraft is not stored, the aircraft is not deleted, so the type is read from it.
 */
public class PilotLogbook {
    private static final int MAX_MINUTES = (1 << 24) - 1;
    private static final int MAX_16BIT = 65535;
    private static final int MAX_TENTHS = 255;

    private final Storage<Entry> storage = Storage.<Entry>builder()
            .name("pilot-logbook")
            .withInstantiator(Entry::new)
            .withIdOf(DataType.Unsigned24bit)
            // -------------------------------------------------- record deletion status    1 byte
            .withDataField(DataField.of(DataType.Unsigned24bit)) // personId                3 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // aircraftId              3 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // fromAirportId           2 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // toAirportId             2 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // blocksOff, minutes      3 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // blocksOn, minutes       3 bytes
            .withDataField(DataField.of(DataType.Unsigned8bit)) // flight kind bits: bit 0 IFR, bit 6 night takeoff, bit 7 night landing, bits 1-5 are free  1 byte
            .withDataField(DataField.of(DataType.Unsigned8bit)) // flightTime, tenths of hour 0-255     1 byte
            .withDataField(DataField.of(DataType.Unsigned8bit)) // nightTime, tenths of hour           1 byte
            .withDataField(DataField.of(DataType.Unsigned8bit)) // ifrTime, tenths of hour             1 byte
            // ----------------------------------------------------- sum                   21 bytes
            // -------------------------------------------- reserved 11 bytes to align total size to 32 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // reserve                 2 bytes
            .withDataField(DataField.of(DataType.Unsigned8bit)) // reserve                  1 byte
            .build();

    private final DataField personIdField = storage.getDataField(0);
    private final DataField aircraftIdField = storage.getDataField(1);
    private final DataField fromAirportIdField = storage.getDataField(2);
    private final DataField toAirportIdField = storage.getDataField(3);
    private final DataField blocksOffField = storage.getDataField(4);
    private final DataField blocksOnField = storage.getDataField(5);
    private final DataField kindRawField = storage.getDataField(6);
    private final BitAccessField kindBits = BitAccessField.instance(storage, kindRawField);
    // The bit 0 is the flight rules, the day and night bits are in the top of the byte on purpose: they are going to be reworked,
    // and the bits 1-5 between them are free for other flags. A record is one flight: one takeoff and one landing, each of them is
    // either at day (bit is 0) or at night (bit is 1). Nobody edits them.
    private final BitAccessField.Section ifrBit = kindBits.section(0, 1); // 1 is IFR, 0 is VFR
    private final BitAccessField.Section nightTakeoffBit = kindBits.section(6, 1);
    private final BitAccessField.Section nightLandingBit = kindBits.section(7, 1);
    private final DataField flightTimeField = storage.getDataField(7);
    private final DataField nightTimeField = storage.getDataField(8);
    private final DataField ifrTimeField = storage.getDataField(9);

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Stream<Entry> all() {
        return storage.all();
    }

    public Stream<Entry> filter(final Storage.Condition<Entry> condition) {
        return storage.filter(condition);
    }

    public Optional<Entry> byId(final int id) {
        return storage.byId(id);
    }

    public Storage.Condition<Entry> byPersonId(final int personId) {
        checkArgument(personId > 0);
        return recordId -> storage.getAsInt(recordId, personIdField) == personId;
    }

    /**
     * @param blocksOffTime world time (seconds) of blocks off, it is stored with an accuracy of a minute
     * @param blocksOnTime  world time (seconds) of blocks on
     */
    public Entry create(final int personId,
                        final int aircraftId,
                        final int fromAirportId,
                        final int toAirportId,
                        final int blocksOffTime,
                        final int blocksOnTime) {
        checkArgument(personId > 0, "personId is mandatory");
        checkArgument(aircraftId > 0, "aircraftId is mandatory");
        checkArgument(1 <= fromAirportId && fromAirportId <= MAX_16BIT, "fromAirportId is out of 16 bits");
        checkArgument(1 <= toAirportId && toAirportId <= MAX_16BIT, "toAirportId is out of 16 bits");
        final int blocksOffMinutes = toMinutes(blocksOffTime);
        final int blocksOnMinutes = toMinutes(blocksOnTime);
        checkArgument(blocksOnMinutes >= blocksOffMinutes, "blocks on is before blocks off");

        final int id = storage.addRecord();
        storage.set(id, personIdField, personId);
        storage.set(id, aircraftIdField, aircraftId);
        storage.set(id, fromAirportIdField, fromAirportId);
        storage.set(id, toAirportIdField, toAirportId);
        storage.set(id, blocksOffField, blocksOffMinutes);
        storage.set(id, blocksOnField, blocksOnMinutes);
        // day or night of the takeoff and of the landing is not calculated yet, day is the default
        nightTakeoffBit.setInt(id, 0);
        nightLandingBit.setInt(id, 0);
        // the flight rules are not known yet, VFR until the source is connected
        ifrBit.setInt(id, 0);
        // the times are in tenths of hour. Night and IFR time are not calculated yet, zeros mean not classified
        storage.set(id, flightTimeField, flightTimeTenths(blocksOnMinutes - blocksOffMinutes));
        storage.set(id, nightTimeField, 0);
        storage.set(id, ifrTimeField, 0);
        return new Entry(id);
    }

    /**
     * World time in seconds to minutes from the start of the world, the seconds are dropped.
     */
    public static int toMinutes(final int worldTime) {
        checkArgument(worldTime >= Time.START_TIME_EPOCH_SECONDS, "time is before the start of the world");
        final int minutes = (worldTime - Time.START_TIME_EPOCH_SECONDS) / Time.ONE_MINUTE;
        checkArgument(minutes <= MAX_MINUTES, "time is out of 3 bytes of minutes");
        return minutes;
    }

    public static int fromMinutes(final int minutes) {
        return Time.START_TIME_EPOCH_SECONDS + minutes * Time.ONE_MINUTE;
    }

    /**
     * Minutes to tenths of hour (6 minutes each), rounded half up: 7 h 33 min is 453 min, 75.5 tenths, 7.6 hours. A byte holds up to 25.5 hours.
     */
    public static int flightTimeTenths(final int minutes) {
        checkArgument(minutes >= 0);
        return Math.min(MAX_TENTHS, (minutes + 3) / 6);
    }

    private static void checkTenths(final int tenths) {
        checkArgument(0 <= tenths && tenths <= MAX_TENTHS, "time is a byte of tenths of hour");
    }

    @SuppressWarnings("LombokGetterMayBeUsed")
    public class Entry {
        private final int id;

        private Entry(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public int getPersonId() {
            return storage.getAsInt(id, personIdField);
        }

        public int getAircraftId() {
            return storage.getAsInt(id, aircraftIdField);
        }

        public int getFromAirportId() {
            return storage.getAsInt(id, fromAirportIdField);
        }

        public int getToAirportId() {
            return storage.getAsInt(id, toAirportIdField);
        }

        /**
         * World time in seconds, with an accuracy of a minute.
         */
        public int getBlocksOffTime() {
            return fromMinutes(storage.getAsInt(id, blocksOffField));
        }

        public int getBlocksOnTime() {
            return fromMinutes(storage.getAsInt(id, blocksOnField));
        }

        public int getDurationMinutes() {
            return storage.getAsInt(id, blocksOnField) - storage.getAsInt(id, blocksOffField);
        }

        /**
         * Flight time in tenths of hour: from blocks off to blocks on.
         */
        public int getFlightTimeTenths() {
            return storage.getAsInt(id, flightTimeField);
        }

        public int getNightTimeTenths() {
            return storage.getAsInt(id, nightTimeField);
        }

        public void setNightTimeTenths(final int tenths) {
            checkTenths(tenths);
            checkArgument(tenths <= getFlightTimeTenths(), "night time is longer than the flight time");
            storage.set(id, nightTimeField, tenths);
        }

        public int getIfrTimeTenths() {
            return storage.getAsInt(id, ifrTimeField);
        }

        /**
         * Flight rules of the flight: true is IFR, false is VFR. IFR time is the whole flight time when the flight is IFR.
         */
        public boolean isIfr() {
            return ifrBit.getBoolean(id);
        }

        public void setIfr(final boolean ifr) {
            ifrBit.setBoolean(id, ifr);
            // IFR time is the time of the flight under an IFR flight plan (SimBrief), the real time in the clouds (IMC) is another thing
            storage.set(id, ifrTimeField, ifr ? getFlightTimeTenths() : 0);
        }

        public int getDayTakeoffs() {
            return nightTakeoffBit.getBoolean(id) ? 0 : 1;
        }

        public int getNightTakeoffs() {
            return nightTakeoffBit.getBoolean(id) ? 1 : 0;
        }

        public void setNightTakeoff(final boolean night) {
            nightTakeoffBit.setBoolean(id, night);
        }

        public int getDayLandings() {
            return nightLandingBit.getBoolean(id) ? 0 : 1;
        }

        public int getNightLandings() {
            return nightLandingBit.getBoolean(id) ? 1 : 0;
        }

        public void setNightLanding(final boolean night) {
            nightLandingBit.setBoolean(id, night);
        }



        @Override
        public String toString() {
            return String.format("{ id: %s, person: %s, aircraft: %s }", id, getPersonId(), getAircraftId());
        }
    }
}
