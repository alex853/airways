package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.storage.Strings;
import net.simforge.commons.misc.Geo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class Aircrafts {
    private final Strings strings;

    private final Storage<Aircraft> storage = Storage.<Aircraft>builder()
            .name("aircrafts")
            .withInstantiator(Aircraft::new)
            .withIdOf(DataType.Unsigned24bit)
            .withDataField(DataField.of(DataType.Unsigned16bit)) // aircraftTypeId
            .withDataField(DataField.of(DataType.Signed32bit)) // regNoId
            .withDataField(DataField.of(DataType.Unsigned16bit)) // aircraftOperatorId
            .withDataField(DataField.of(DataType.Unsigned24bit)) // flightMissionId
            .withDataField(DataField.of(DataType.Unsigned8bit)) // operationalStatus
            .withDataField(DataField.of(DataType.Unsigned8bit)) // locationStatus
            .withDataField(DataField.of(DataType.Unsigned16bit)) // locationAirportId
            .withDataField(DataField.of(DataType.Float)) // locationLatitude
            .withDataField(DataField.of(DataType.Float)) // locationLongitude
            // locationHeading 0-359, 9bits
            // locationAltitude 0-1023, 10bits
            // flightTime, minutes, 24bits
            // flownCycles, count, 16bits
            // lastMoved, seconds since epoch, 32bits, optional....
            // total = 12 bytes
            // to add 16 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserved1
            .withDataField(DataField.of(DataType.Signed32bit)) // reserved2
            .withDataField(DataField.of(DataType.Signed32bit)) // reserved3
            .withDataField(DataField.of(DataType.Signed32bit)) // reserved4
            .build(); // todo ak0 add flight time, cycles, last moved at, heading, reserve space

/*    private final Storage<Aircraft> storage0 = Storage.<Aircraft>builder()
            .name("aircrafts0")
            .withInstantiator(Aircraft::new)
            .withIdOf(DataType.Unsigned24bit)
            .withDataField(DataField.of(DataType.Unsigned16bit)) // aircraftTypeId
            .withDataField(DataField.of(DataType.Signed32bit)) // regNoId
            .withDataField(DataField.of(DataType.Unsigned16bit)) // aircraftOperatorId
            .withDataField(DataField.of(DataType.Unsigned24bit)) // flightMissionId
            .withDataField(DataField.of(DataType.Unsigned8bit)) // operationalStatus
            .withDataField(DataField.of(DataType.Unsigned8bit)) // locationStatus
            .withDataField(DataField.of(DataType.Unsigned16bit)) // locationAirportId
            .withDataField(DataField.of(DataType.Float)) // locationLatitude
            .withDataField(DataField.of(DataType.Float)) // locationLongitude
            .withDataField(DataField.of(DataType.Signed32bit)) // reserved1
            .withDataField(DataField.of(DataType.Signed32bit)) // reserved2
            .withDataField(DataField.of(DataType.Signed32bit)) // reserved3
            .withDataField(DataField.of(DataType.Signed32bit)) // reserved4
            .build();*/

    private final DataField aircraftTypeIdField = storage.getDataField(0);
    private final DataField regNoIdField = storage.getDataField(1);
    private final DataField aircraftOperatorIdField = storage.getDataField(2);
    private final DataField flightMissionIdField = storage.getDataField(3);
    private final DataField operationalStatusField = storage.getDataField(4);
    private final DataField locationStatusField = storage.getDataField(5);
    private final DataField locationAirportIdField = storage.getDataField(6);
    private final DataField locationLatitudeField = storage.getDataField(7);
    private final DataField locationLongitudeField = storage.getDataField(8);

    public Aircrafts(final Strings strings) {
        this.strings = strings;
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);

//        buildStorage0andSave(rootPath);
    }

    public Stream<Aircraft> all() {
        return storage.all();
    }

    public Stream<Aircraft> filter(final Storage.Condition<Aircraft> condition) {
        return storage.filter1(condition);
    }

    public Collection<Aircraft> allIdleAndParkedAtAirport() {
        return storage.filter(Aircrafts::isIdleAndParkedAtAirport); // todo ak0 migrate to filter1
    }

    public Collection<Aircraft> allIdleAndParkedAtAirportAndNoOperatorAssigned() {
        return storage.filter(Aircrafts::isIdleAndParkedAtAirportAndNoOperatorAssigned); // todo ak0 migrate to filter1
    }

    public Optional<Aircraft> byId(final int id) {
        return storage.byId(id);
    }

    public Aircraft create(final AircraftTypes.AircraftType aircraftType,
                           final String regNo,
                           final Airports.Airport locationAirport) {
        checkNotNull(aircraftType);
        checkNotNull(regNo);
        checkArgument(byRegNo(regNo).isEmpty());
        checkNotNull(locationAirport);

        final int recordId = storage.addRecord();
        storage.set(recordId, aircraftTypeIdField, aircraftType.getId());
        storage.set(recordId, regNoIdField, strings.findOrAdd(regNo));
        storage.set(recordId, operationalStatusField, OperationalStatus.Idle.code());
        storage.set(recordId, locationStatusField, LocationStatus.ParkedAtAirport.code());
        storage.set(recordId, locationAirportIdField, locationAirport.getId());
        storage.set(recordId, locationLatitudeField, locationAirport.getLatitude());
        storage.set(recordId, locationLongitudeField, locationAirport.getLongitude());
        return new Aircraft(recordId);
    }

    public Optional<Aircraft> byRegNo(final String regNo) {
        checkNotNull(regNo, "regNo is mandatory");
        return storage.findFirst1(recordId -> regNo.equals(strings.byId(storage.getAsInt(recordId, regNoIdField))));
    }

    public Storage.Condition<Aircraft> byLocationStatus(final LocationStatus locationStatus) {
        checkNotNull(locationStatus);

        return recordId -> storage.getAsInt(recordId, locationStatusField) == locationStatus.code();
    }

    public Storage.Condition<Aircraft> byLocationAirportId(final int locationAirportId) {
        checkArgument(locationAirportId > 0);

        return recordId -> storage.getAsInt(recordId, locationAirportIdField) == locationAirportId;
    }

    public Stream<Aircraft> byAircraftOperatorId(final int aircraftOperatorId) {
        checkArgument(aircraftOperatorId > 0);

        return storage.filter1(recordId -> storage.getAsInt(recordId, aircraftOperatorIdField) == aircraftOperatorId);
    }

    @SuppressWarnings("LombokGetterMayBeUsed")
    public class Aircraft {
        private final int id;

        private Aircraft(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public int getAircraftTypeId() {
            return storage.getAsInt(id, aircraftTypeIdField);
        }

        public String getRegNo() {
            return strings.byId(storage.getAsInt(id, regNoIdField));
        }

        public int getAircraftOperatorId() {
            return storage.getAsInt(id, aircraftOperatorIdField);
        }

        public void setAircraftOperatorId(final int aircraftOperatorId) {
            storage.set(id, aircraftOperatorIdField, aircraftOperatorId);
        }

        public int getFlightMissionId() {
            return storage.getAsInt(id, flightMissionIdField);
        }

        public void setFlightMissionId(final int flightMissionId) {
            storage.set(id, flightMissionIdField, flightMissionId);
        }

        public OperationalStatus getOperationalStatus() {
            return OperationalStatus.byCode(getOperationalStatusRaw());
        }

        public int getOperationalStatusRaw() {
            return storage.getAsInt(id, operationalStatusField);
        }

        public void setOperationalStatus(final OperationalStatus operationalStatus) {
            checkNotNull(operationalStatus, "operationalStatus is mandatory");
            storage.set(id, operationalStatusField, operationalStatus.code());
        }

        public LocationStatus getLocationStatus() {
            return LocationStatus.byCode(getLocationStatusRaw());
        }

        public int getLocationStatusRaw() {
            return storage.getAsInt(id, locationStatusField);
        }

        public void setLocationStatus(final LocationStatus locationStatus) {
            checkNotNull(locationStatus, "locationStatus is mandatory");
            storage.set(id, locationStatusField, locationStatus.code());
        }

        public int getLocationAirportId() {
            return storage.getAsInt(id, locationAirportIdField);
        }

        public void setLocationAirportId(final int locationAirportId) {
            storage.set(id, locationAirportIdField, locationAirportId);
        }

        public float getLocationLatitude() {
            return storage.getAsFloat(id, locationLatitudeField);
        }

        public void setLocationLatitude(final float locationLatitude) {
            storage.set(id, locationLatitudeField, locationLatitude);
        }

        public float getLocationLongitude() {
            return storage.getAsFloat(id, locationLongitudeField);
        }

        public void setLocationLongitude(final float locationLongitude) {
            storage.set(id, locationLongitudeField, locationLongitude);
        }

        public Geo.Coords getLocationCoords() {
            return Geo.coords(getLocationLatitude(), getLocationLongitude());
        }
    }

    public enum OperationalStatus {
        Idle(1),
        Active(2),
        Maintenance(90),
        Stored(99);

        private final int code;

        OperationalStatus(final int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static OperationalStatus byCode(final int code) {
            return Arrays.stream(OperationalStatus.values())
                    .filter(status -> status.code == code)
                    .findFirst()
                    .orElse(null);
        }
    }

    public enum LocationStatus {
        ParkedAtAirport(0),
        Flying(1),
        TaxiingOut(2),
        TaxiingIn(3);

        private final int code;

        LocationStatus(final int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static LocationStatus byCode(final int code) {
            return Arrays.stream(LocationStatus.values())
                    .filter(status -> status.code == code)
                    .findFirst()
                    .orElse(null);
        }
    }

    public static boolean isIdleAndParkedAtAirport(final Aircraft aircraft) {
        return aircraft.getOperationalStatus() == Aircrafts.OperationalStatus.Idle
                && aircraft.getLocationStatus() == Aircrafts.LocationStatus.ParkedAtAirport
                && aircraft.getLocationAirportId() != 0;
    }

    public static boolean isIdleAndParkedAtAirportAndNoOperatorAssigned(final Aircraft aircraft) {
        return aircraft.getOperationalStatus() == Aircrafts.OperationalStatus.Idle
                && aircraft.getLocationStatus() == Aircrafts.LocationStatus.ParkedAtAirport
                && aircraft.getLocationAirportId() != 0
                && aircraft.getAircraftOperatorId() == 0;
    }

/*    private void buildStorage0andSave(Path rootPath) throws IOException {
        DataField aircraftTypeIdField0 = storage0.getDataField(0);
        DataField regNoIdField0 = storage0.getDataField(1);
        DataField aircraftOperatorIdField0 = storage0.getDataField(2);
        DataField flightMissionIdField0 = storage0.getDataField(3);
        DataField operationalStatusField0 = storage0.getDataField(4);
        DataField locationStatusField0 = storage0.getDataField(5);
        DataField locationAirportIdField0 = storage0.getDataField(6);
        DataField locationLatitudeField0 = storage0.getDataField(7);
        DataField locationLongitudeField0 = storage0.getDataField(8);

        for (int i = 1; i <= this.storage.getCount(); i++) {
            int newId = storage0.addRecord();
            checkArgument(newId == i);
            storage0.set(i, aircraftTypeIdField0, storage.getAsInt(i, aircraftTypeIdField));
            storage0.set(i, regNoIdField0, storage.getAsInt(i, regNoIdField));
            storage0.set(i, aircraftOperatorIdField0, storage.getAsInt(i, aircraftOperatorIdField));
            storage0.set(i, flightMissionIdField0, storage.getAsInt(i, flightMissionIdField));
            storage0.set(i, operationalStatusField0, storage.getAsInt(i, operationalStatusField));
            storage0.set(i, locationStatusField0, storage.getAsInt(i, locationStatusField));
            storage0.set(i, locationAirportIdField0, storage.getAsInt(i, locationAirportIdField));
            storage0.set(i, locationLatitudeField0, storage.getAsFloat(i, locationLatitudeField));
            storage0.set(i, locationLongitudeField0, storage.getAsFloat(i, locationLongitudeField));
        }

        this.storage0.save(rootPath);

        LoggerFactory.getLogger(Aircrafts.class).warn("aircrafts0 saved");
    }*/
}
