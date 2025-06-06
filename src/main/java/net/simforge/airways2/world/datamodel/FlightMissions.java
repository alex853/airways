package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.world.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class FlightMissions {
    private static final Logger log = LoggerFactory.getLogger(FlightMissions.class);
    private final Storage<Mission> storage = Storage.<Mission>builder()
            .name("flight-missions")
            .withInstantiator(Mission::new)
            .withIdOf(DataType.Unsigned24bit)
            .withDataField(DataField.of(DataType.Unsigned24bit)) // aircraftId
            .withDataField(DataField.of(DataType.Unsigned8bit)) // status 0..15, modes 76xxxxxx, bits xx54xxxx are non-used
            .withDataField(DataField.of(DataType.Signed32bit)) // heartbeatTime
            .withDataField(DataField.of(DataType.Unsigned16bit)) // departureAirportId
            .withDataField(DataField.of(DataType.Unsigned16bit)) // destinationAirportId
            // ---------------------------------------------------------------------------------------------------------
            .withDataField(DataField.of(DataType.Unsigned16bit)) // dateOfFlight
            .withDataField(DataField.of(DataType.Unsigned24bit)) // plannedDepartureTime + plannedArrivalTime
            .withDataField(DataField.of(DataType.Unsigned24bit)) // actualDepartureTime + actualTakeoffTime
            .withDataField(DataField.of(DataType.Unsigned24bit)) // actualLandingTime + actualArrivalTime
            .withDataField(DataField.of(DataType.Unsigned24bit)) // plannedDepartureTimeExp + plannedArrivalTimeExp
            .withDataField(DataField.of(DataType.Unsigned24bit)) // actualDepartureTimeExp + actualTakeoffTimeExp
            .withDataField(DataField.of(DataType.Unsigned24bit)) // actualLandingTimeExp + actualArrivalTimeExp
            .withDataField(DataField.of(DataType.Signed32bit)) // reserved
            .build();

    private static final int pcModeMask = 0b10000000;
    private static final int unusedModeMask = 0b01000000;
    private static final int allModesMask = pcModeMask | unusedModeMask;

    private final DataField aircraftIdField = storage.getDataField(0);
    private final DataField statusField = storage.getDataField(1);
    private final DataField heartbeatTimeField = storage.getDataField(2);
    private final DataField departureAirportIdField = storage.getDataField(3);
    private final DataField destinationAirportIdField = storage.getDataField(4);
    private final DataField dateOfFlightField = storage.getDataField(5);
    private final DataField plannedDepartureAndArrivalTimeField = storage.getDataField(6);
    private final DataField actualDepartureAndTakeoffTimeField = storage.getDataField(7);
    private final DataField actualLandingAndArrivalTimeField = storage.getDataField(8);
    private final DataField plannedDepartureAndArrivalTimeFieldExp = storage.getDataField(9);
    private final DataField actualDepartureAndTakeoffTimeFieldExp = storage.getDataField(10);
    private final DataField actualLandingAndArrivalTimeFieldExp = storage.getDataField(11);

    public FlightMissions() {
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Mission createDispatchedMission(final Aircrafts.Aircraft aircraft,
                                           final Airports.Airport origin,
                                           final Airports.Airport destination,
                                           final int departureTime,
                                           final int arrivalTime) {
        final int id = storage.addRecord();
        final Mission mission = new Mission(id);
        storage.set(id, aircraftIdField, aircraft.getId());
        mission.setStatus(Status.Dispatched);
        storage.set(id, departureAirportIdField, origin.getId());
        storage.set(id, destinationAirportIdField, destination.getId());
        mission.setPlannedDepartureWorldTime(departureTime);
        mission.setPlannedArrivalWorldTime(arrivalTime);
        return mission;
    }

    public void deleteById(final int id) {
        storage.deleteRecord(id);
    }

    public Collection<Mission> all() {
        return storage.all();
    }

    public Optional<Mission> byId(final int id) {
        return storage.byId(id);
    }

    public List<Mission> allForAircraft(final Aircrafts.Aircraft aircraft) {
        checkNotNull(aircraft, "aircraft is mandatory");
        return all().stream().filter(m -> m.getAircraftId() == aircraft.getId()).toList();
    }

    public Optional<Mission> theLatestMissionByAircraftId(final Aircrafts.Aircraft aircraft) {
        final List<FlightMissions.Mission> allMissions = new ArrayList<>(allForAircraft(aircraft));
        allMissions.sort(FlightMissions.sortByDepartureTimeFromFutureToPast);
        if (allMissions.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(allMissions.get(0));
        }
    }

    public Optional<Mission> nextForHeartbeat(final int worldTime) {
        return storage.findFirst(mission -> mission.getHeartbeatTime() <= worldTime
                && mission.getHeartbeatTime() != 0);
    }

    public class Mission {
        private final int id;

        private Mission(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public int getAircraftId() {
            return storage.getAsInt(id, aircraftIdField);
        }

        public Status getStatus() {
            return Status.byCode(getStatusCode());
        }

        public int getStatusCode() {
            final int statusRaw = storage.getAsInt(id, statusField);
            return statusRaw & ~allModesMask;
        }

        public void setStatus(final Status status) {
            checkNotNull(status, "status is mandatory");
            checkArgument(status.code() <= 15, "status code should be in [0..15] range");
            final int statusCode = status.code();
            final int statusRaw = storage.getAsInt(id, statusField);
            final int modeBits = statusRaw & allModesMask;
            storage.set(id, statusField, statusCode | modeBits);
        }

        /**
         * NPC aka Non Player Character, means 'automatic' flight
         * PC  aka     Player Character, means 'manual' flight
         */
        public boolean isModePc() {
            return isStatusBitMode(pcModeMask);
        }

        public void setModePc(final boolean enabled) {
            setStatusBitMode(pcModeMask, enabled);
        }

        public boolean isUnusedMode() {
            return isStatusBitMode(unusedModeMask);
        }

        public void setUnusedMode(final boolean enabled) {
            setStatusBitMode(unusedModeMask, enabled);
        }

        private boolean isStatusBitMode(final int bitModeMask) {
            final int statusRaw = storage.getAsInt(id, statusField);
            return (statusRaw & bitModeMask) != 0;
        }

        private void setStatusBitMode(final int bitModeMask, final boolean enabled) {
            final int statusRaw = storage.getAsInt(id, statusField);
            final int statusRawMinusMask = (statusRaw & ~bitModeMask);
            final int newStatusRaw = statusRawMinusMask | (enabled ? bitModeMask : 0);
            storage.set(id, statusField, newStatusRaw);
        }

        public int getHeartbeatTime() {
            return storage.getAsInt(id, heartbeatTimeField);
        }

        public void setHeartbeatTime(final int heartbeatTime) {
            storage.set(id, heartbeatTimeField, heartbeatTime);
        }

        public int getDepartureAirportId() {
            return storage.getAsInt(id, departureAirportIdField);
        }

        public int getDestinationAirportId() {
            return storage.getAsInt(id, destinationAirportIdField);
        }

        public int getPlannedDepartureWorldTime() {
            return getPlannedDepartureTimeNew();
        }

        public void setPlannedDepartureWorldTime(final int plannedDepartureWorldTime) {
            final LocalDateTime ldt = Time.toLdtOrNull(plannedDepartureWorldTime);
            setDateOfFlight(ldt != null ? ldt.toLocalDate() : null);
            setPlannedDepartureTimeNew(plannedDepartureWorldTime);
        }

        public int getPlannedArrivalWorldTime() {
            return getPlannedArrivalTimeNew();
        }

        public void setPlannedArrivalWorldTime(final int plannedArrivalWorldTime) {
            setPlannedArrivalTimeNew(plannedArrivalWorldTime);
        }

        public int getActualDepartureWorldTime() {
            return getActualDepartureTimeNew();
        }

        public void setActualDepartureWorldTime(final int actualDepartureWorldTime) {
            setActualDepartureTimeNew(actualDepartureWorldTime);
        }

        public int getActualTakeoffWorldTime() {
            return getActualTakeoffTimeNew();
        }

        public void setActualTakeoffWorldTime(final int actualTakeoffWorldTime) {
            setActualTakeoffTimeNew(actualTakeoffWorldTime);
        }

        public int getActualLandingWorldTime() {
            return getActualLandingTimeNew();
        }

        public void setActualLandingWorldTime(final int actualLandingWorldTime) {
            setActualLandingTimeNew(actualLandingWorldTime);
        }

        public int getActualArrivalWorldTime() {
            return getActualArrivalTimeNew();
        }

        public void setActualArrivalWorldTime(final int actualArrivalWorldTime) {
            setActualArrivalTimeNew(actualArrivalWorldTime);
        }

        private static final LocalDate DAY_BEFORE_FIRST_DAY = LocalDate.of(2024, 12, 31);

        public LocalDate getDateOfFlight() {
            final int days = storage.getAsIntUnsafe(id, dateOfFlightField);
            if (days == 0) {
                return null;
            }
            if (days > 300) {
                log.error("getDateOfFlight - too big day " + days, new IllegalStateException());
            }
            return DAY_BEFORE_FIRST_DAY.plusDays(days);
        }

        public void setDateOfFlight(final LocalDate dateOfFlight) {
            final int days = dateOfFlight != null
                    ? (int) ChronoUnit.DAYS.between(DAY_BEFORE_FIRST_DAY, dateOfFlight)
                    : 0;
            if (days > 300) {
                log.error("setDateOfFlight - too big day " + days, new IllegalStateException());
            }
            storage.setUnsafe(id, dateOfFlightField, days);
        }

        public int getPlannedDepartureTimeNew() {
            return getTime12bit(plannedDepartureAndArrivalTimeField, true);
        }

        private void setPlannedDepartureTimeNew(final int plannedDepartureWorldTime) {
            setTime12bit(plannedDepartureAndArrivalTimeField, true, plannedDepartureWorldTime);
        }

        public int getPlannedArrivalTimeNew() {
            return getTime12bit(plannedDepartureAndArrivalTimeField, false);
        }

        private void setPlannedArrivalTimeNew(final int plannedArrivalWorldTime) {
            setTime12bit(plannedDepartureAndArrivalTimeField, false, plannedArrivalWorldTime);
        }

        public int getActualDepartureTimeNew() {
            return getTime12bit(actualDepartureAndTakeoffTimeField, true);
        }

        private void setActualDepartureTimeNew(final int actualDepartureWorldTime) {
            setTime12bit(actualDepartureAndTakeoffTimeField, true, actualDepartureWorldTime);
        }

        public int getActualTakeoffTimeNew() {
            return getTime12bit(actualDepartureAndTakeoffTimeField, false);
        }

        private void setActualTakeoffTimeNew(final int actualTakeoffWorldTime) {
            setTime12bit(actualDepartureAndTakeoffTimeField, false, actualTakeoffWorldTime);
        }

        public int getActualLandingTimeNew() {
            return getTime12bit(actualLandingAndArrivalTimeField, true);
        }

        private void setActualLandingTimeNew(final int actualLandingWorldTime) {
            setTime12bit(actualLandingAndArrivalTimeField, true, actualLandingWorldTime);
        }

        public int getActualArrivalTimeNew() {
            return getTime12bit(actualLandingAndArrivalTimeField, false);
        }

        private void setActualArrivalTimeNew(final int actualArrivalWorldTime) {
            setTime12bit(actualLandingAndArrivalTimeField, false, actualArrivalWorldTime);
        }

        public int getPlannedDepartureTimeExp() {
            return getTime12bit(plannedDepartureAndArrivalTimeFieldExp, true);
        }

        public int getPlannedArrivalTimeExp() {
            return getTime12bit(plannedDepartureAndArrivalTimeFieldExp, false);
        }

        public int getActualDepartureTimeExp() {
            return getTime12bit(actualDepartureAndTakeoffTimeFieldExp, true);
        }

        public int getActualTakeoffTimeExp() {
            return getTime12bit(actualDepartureAndTakeoffTimeFieldExp, false);
        }

        public int getActualLandingTimeExp() {
            return getTime12bit(actualLandingAndArrivalTimeFieldExp, true);
        }

        public int getActualArrivalTimeExp() {
            return getTime12bit(actualLandingAndArrivalTimeFieldExp, false);
        }

        private int getTime12bit(final DataField dataField, final boolean high) {
            final LocalDate dateOfFlight = getDateOfFlight();
            final int value = read12bits(dataField, high);
            if (dateOfFlight != null && value != 0) {
                final int minutes = value - 1000;
                checkArgument(-1000 < minutes && minutes < 3000);

                return Time.fromLdt(dateOfFlight.atStartOfDay().plusMinutes(minutes));
            } else {
                return 0;
            }
        }

        private void setTime12bit(final DataField dataField, final boolean high, final int worldTime) {
            final LocalDate dateOfFlight = getDateOfFlight();
            if (dateOfFlight != null && worldTime != 0) {
                final LocalDateTime baseTime = dateOfFlight.atStartOfDay();
                final LocalDateTime thisTime = Time.toLdt(worldTime);
                final int minutes = (int) Duration.between(baseTime, thisTime).getSeconds() / 60;
                checkArgument(-1000 < minutes && minutes < 3000);

                write12bits(dataField, high, minutes + 1000);

                final int check = read12bits(dataField, high);
                if (check != minutes + 1000) {
                    log.warn("setTimeExp high {}, dof {}, worldTime {}, minutes {}, minutes+1000 {}, check {}", high, dateOfFlight, thisTime, minutes, minutes + 1000, check);
                }
            } else {
                write12bits(dataField, high, 0);
            }
        }

        private int read12bits(final DataField dataField, final boolean high) {
            final int mask = high ? 0b111111111111000000000000 : 0b000000000000111111111111;
            final int shift = high ? 12 : 0;

            final int raw = storage.getAsIntUnsafe(id, dataField);
            return (raw & mask) >> shift;
        }

        private void write12bits(final DataField dataField, final boolean high, final int value) {
            final int mask = high ? 0b111111111111000000000000 : 0b000000000000111111111111;
            final int shift = high ? 12 : 0;

            final int shiftedValue = value << shift;
            final int raw = storage.getAsIntUnsafe(id, dataField);
            final int anotherPart = (raw & ~mask);
            storage.setUnsafe(id, dataField, shiftedValue | anotherPart);
        }

/*        public void copyExpToNew() {
            setPlannedDepartureTimeNew(getPlannedDepartureWorldTime());
            setPlannedArrivalTimeNew(getPlannedArrivalWorldTime());
            setActualDepartureTimeNew(getActualDepartureWorldTime());
            setActualTakeoffTimeNew(getActualTakeoffWorldTime());
            setActualLandingTimeNew(getActualLandingWorldTime());
            setActualArrivalTimeNew(getActualArrivalWorldTime());
        }*/

        @Override
        public String toString() {
            return String.format("{ id: %s, status: %s }", id, getStatus());
        }
    }

    public enum Status {
        // available 0
        PlannedManually(1), // old: 1
        PlannedViaSchedule(2), // old: 2
        // available 3
        Dispatched(4), // old: 20
        Preflight(5), // old: 30
        Departure(6), // old: 40
        Flying(7), // old: 50
        Arrival(8), // old: 60
        Postflight(9), // old: 70
        Finished(10), // old: -> 100
        // available 11
        // available 12
        Cancelled(13); // old: 99
        // available 14
        // available 15

        private final int code;

        Status(final int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static Status byCode(final int code) {
            return Arrays.stream(Status.values())
                    .filter(status -> status.code == code)
                    .findFirst()
                    .orElse(null);
        }
    }

    public static final Comparator<Mission> sortByDepartureTimeFromFutureToPast = (m1, m2) -> m2.getPlannedDepartureWorldTime() - m1.getPlannedDepartureWorldTime();

}
