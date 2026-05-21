package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.storage.Strings;
import net.simforge.commons.misc.Geo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class Aircrafts {
    public static final int NO_AIRCRAFT_OPERATOR_ID = 0;

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
            .withDataField(DataField.of(DataType.Unsigned8bit)) // locationHeading, 0-255 scaled to 0-359 degrees
            .withDataField(DataField.of(DataType.Unsigned16bit)) // locationAltitude, 0-65535 feet
            .withDataField(DataField.of(DataType.Unsigned24bit)) // flight time, minutes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // flown cycles, times
            // todo ak3 lastMoved, seconds since epoch, 32bits, optional....
            .withDataField(DataField.of(DataType.Signed32bit)) // reserved3
            .withDataField(DataField.of(DataType.Signed32bit)) // reserved4
            .build();

    private final DataField aircraftTypeIdField = storage.getDataField(0);
    private final DataField regNoIdField = storage.getDataField(1);
    private final DataField aircraftOperatorIdField = storage.getDataField(2);
    private final DataField flightMissionIdField = storage.getDataField(3);
    private final DataField operationalStatusField = storage.getDataField(4);
    private final DataField locationStatusField = storage.getDataField(5);
    private final DataField locationAirportIdField = storage.getDataField(6);
    private final DataField locationLatitudeField = storage.getDataField(7);
    private final DataField locationLongitudeField = storage.getDataField(8);
    private final DataField locationHeadingField = storage.getDataField(9);
    private final DataField locationAltitudeField = storage.getDataField(10);
    private final DataField flightTimeField = storage.getDataField(11);
    private final DataField flownCyclesField = storage.getDataField(12);

    public Aircrafts(final Strings strings) {
        this.strings = strings;
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Stream<Aircraft> all() {
        return storage.all();
    }

    public Stream<Aircraft> filter(final Storage.Condition<Aircraft> condition) {
        return storage.filter1(condition);
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

        return recordId -> readLocationStatus(recordId) == locationStatus.code();
    }

    public Storage.Condition<Aircraft> byLocationAirportId(final int locationAirportId) {
        checkArgument(locationAirportId > 0);

        return recordId -> readLocationAirportId(recordId) == locationAirportId;
    }

    @SuppressWarnings("unused")
    public Stream<Aircraft> byAircraftOperatorId(int aircraftOperatorId) {
        return storage.filter1(recordId -> readAircraftOperatorId(recordId) == aircraftOperatorId);
    }

    @SuppressWarnings("unused")
    public Stream<Aircraft> idleAndParkedAtAirport() {
        return storage.filter1(recordId -> readOperationalStatus(recordId) == OperationalStatus.Idle.code()
                && readLocationStatus(recordId) == LocationStatus.ParkedAtAirport.code()
                && readLocationAirportId(recordId) > 0);
    }

    public Stream<Aircraft> byAircraftOperatorIdAndIdleAndParkedAtAirport(int aircraftOperatorId) {
        return storage.filter1(recordId -> readAircraftOperatorId(recordId) == aircraftOperatorId
                && readOperationalStatus(recordId) == OperationalStatus.Idle.code()
                && readLocationStatus(recordId) == LocationStatus.ParkedAtAirport.code()
                && readLocationAirportId(recordId) > 0);
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
            return readAircraftOperatorId(id);
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
            return readOperationalStatus(id);
        }

        public void setOperationalStatus(final OperationalStatus operationalStatus) {
            checkNotNull(operationalStatus, "operationalStatus is mandatory");
            storage.set(id, operationalStatusField, operationalStatus.code());
        }

        public LocationStatus getLocationStatus() {
            return LocationStatus.byCode(getLocationStatusRaw());
        }

        public int getLocationStatusRaw() {
            return readLocationStatus(id);
        }

        public void setLocationStatus(final LocationStatus locationStatus) {
            checkNotNull(locationStatus, "locationStatus is mandatory");
            storage.set(id, locationStatusField, locationStatus.code());
        }

        public int getLocationAirportId() {
            return readLocationAirportId(id);
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

        public int getLocationHeading() {
            return (int) Math.round(storage.getAsInt(id, locationHeadingField) * 359.0/255.0);
        }

        public void setLocationHeading(int heading) {
            while (heading < 0) heading += 360;
            while (heading >= 360) heading -= 360;
            int scaled255 = (int) Math.round(heading * 255.0/359.0);
            storage.set(id, locationHeadingField, scaled255);
        }

        public int getLocationAltitude() {
            return storage.getAsInt(id, locationAltitudeField);
        }

        public void setLocationAltitude(int altitude) {
            if (altitude < 0) altitude = 0;
            if (altitude > 65535) altitude = 65535;
            storage.set(id, locationAltitudeField, altitude);
        }

        public int getFlightTime() {
            return storage.getAsInt(id, flightTimeField);
        }

        public void setFlightTime(int flightTime) {
            storage.set(id, flightTimeField, flightTime);
        }

        public int getFlownCycles() {
            return storage.getAsInt(id, flownCyclesField);
        }

        public void setFlownCycles(int flownCycles) {
            storage.set(id, flownCyclesField, flownCycles);
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

    private int readAircraftOperatorId(int recordId) {
        return storage.getAsInt(recordId, aircraftOperatorIdField);
    }

    private int readOperationalStatus(int recordId) {
        return storage.getAsInt(recordId, operationalStatusField);
    }

    private int readLocationStatus(int recordId) {
        return storage.getAsInt(recordId, locationStatusField);
    }

    private int readLocationAirportId(int recordId) {
        return storage.getAsInt(recordId, locationAirportIdField);
    }
}
