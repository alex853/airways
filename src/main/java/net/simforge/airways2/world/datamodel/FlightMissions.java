package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.tools.TimeTools;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            .withDataField(DataField.of(DataType.Unsigned16bit)) // dateOfFlight
            .withDataField(DataField.of(DataType.Unsigned24bit)) // plannedDepartureTime + plannedArrivalTime
            .withDataField(DataField.of(DataType.Unsigned24bit)) // actualDepartureTime + actualTakeoffTime
            .withDataField(DataField.of(DataType.Unsigned24bit)) // actualLandingTime + actualArrivalTime
            .withDataField(DataField.of(DataType.Unsigned16bit)) // actualLandingAirportId
            // ---------------------------------------------------------------------------------------------------------
            .withDataField(DataField.of(DataType.Unsigned8bit)) // reserved
            .withDataField(DataField.of(DataType.Unsigned24bit)) // reserved
            .withDataField(DataField.of(DataType.Unsigned24bit)) // reserved
            .withDataField(DataField.of(DataType.Unsigned16bit)) // reserved
            // ---------------------------------------------------------------------------------------------------------
            .withDataField(DataField.of(DataType.Unsigned16bit)) // userId
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
    private final DataField actualLandingAirportIdField = storage.getDataField(9);
    @SuppressWarnings("unused")
    private final DataField reserved8bitsField2 = storage.getDataField(10);
    @SuppressWarnings("unused")
    private final DataField reserved24bitsField3 = storage.getDataField(11);
    @SuppressWarnings("unused")
    private final DataField reserved24bitsField4 = storage.getDataField(12);
    @SuppressWarnings("unused")
    private final DataField reserved16bitsField5 = storage.getDataField(13);
    private final DataField userIdField = storage.getDataField(14);

    private static final LocalDate DAY_BEFORE_FIRST_DAY = LocalDate.of(2024, 12, 31);

    private final TreeMap<Integer, List<Integer>> heartbeatTimeIndex = new TreeMap<>();

    public FlightMissions() {
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
        heartbeatTimeIndex_rebuild();
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
        int oldHeartbeatTime = readHeartbeatTime(id);
        heartbeatTimeIndex_remove(oldHeartbeatTime, id);

        storage.deleteRecord(id);
    }

    public Stream<Mission> all() {
        return storage.all();
    }

    public Stream<Mission> filter(final Storage.Condition<Mission> condition) {
        return storage.filter1(condition);
    }

    public Optional<Mission> byId(final int id) {
        return storage.byId(id);
    }

    public Stream<Mission> allForAircraft(final Aircrafts.Aircraft aircraft) {
        checkNotNull(aircraft, "aircraft is mandatory");
        return storage.filter1(recordId -> storage.getAsInt(recordId, aircraftIdField) == aircraft.getId());
    }

    public Stream<Mission> allByUserId(final int userId) {
        checkArgument(userId > 0, "userId is mandatory");
        return storage.filter1(recordId -> storage.getAsInt(recordId, userIdField) == userId);
    }

    public Optional<Mission> theLatestMissionByAircraftId(final Aircrafts.Aircraft aircraft) {
        return allForAircraft(aircraft).min(FlightMissions.sortByDepartureTimeFromFutureToPast);
    }

    public Storage.Condition<Mission> anyStatus(Status... statuses) {
        checkNotNull(statuses, "statuses is mandatory");
        checkArgument(statuses.length > 0, "statuses is mandatory");
        Set<Integer> codes = Arrays.stream(statuses).toList().stream().map(Status::code).collect(Collectors.toSet());

        return recordId -> codes.contains(readStatusCode(recordId));
    }

    public Optional<Mission> nextForHeartbeat(final int worldTime) {
        try (Timing.Timer ignored = Timing.label("FlightMissions.nextForHeartbeat")) {
            while (true) {
                if (heartbeatTimeIndex.isEmpty()) {
                    return Optional.empty();
                }

                int minimalHeartbeatTime = heartbeatTimeIndex.firstKey();
                List<Integer> ids = heartbeatTimeIndex.get(minimalHeartbeatTime);
                if (ids == null || ids.isEmpty()) {
                    heartbeatTimeIndex.remove(minimalHeartbeatTime);
                    continue;
                }

                if (minimalHeartbeatTime > worldTime) {
                    return Optional.empty();
                }

                int recordId = ids.get(0);
                int actualHeartbeatTime = readHeartbeatTime(recordId);
                if (actualHeartbeatTime != minimalHeartbeatTime) {
                    log.warn("MISMATCH BETWEEN ACTUAL AND INDEXED HEARTBEAT TIMES"); // todo ak1 will deletion fix resolve it?
                    //noinspection EmptyTryBlock
                    try (Timing.Timer ignored2 = Timing.label("FlightMissions.nextForHeartbeat.MISMATCH")) {} // this should highlight this occurence in the timing report

                    ids.remove(0);
                    continue;
                }

                return storage.byId(recordId);
            }
        }
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
            return readStatusCode(id);
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
        public boolean isModePlayerCharacter() {
            return isStatusBitMode(pcModeMask);
        }

        public void setModePlayerCharacter(final boolean enabled) {
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
            return readHeartbeatTime(id);
        }

        public void setHeartbeatTime(final int heartbeatTime) {
            int oldHeartbeatTime = readHeartbeatTime(id);
            heartbeatTimeIndex_remove(oldHeartbeatTime, id);

            storage.set(id, heartbeatTimeField, heartbeatTime);

            heartbeatTimeIndex_add(heartbeatTime, id);
        }

        public int getDepartureAirportId() {
            return storage.getAsInt(id, departureAirportIdField);
        }

        public int getDestinationAirportId() {
            return storage.getAsInt(id, destinationAirportIdField);
        }

        public int getActualLandingAirportId() {
            return storage.getAsInt(id, actualLandingAirportIdField);
        }

        public void setActualLandingAirportId(final int actualLandingAirportId) {
            storage.set(id, actualLandingAirportIdField, actualLandingAirportId);
        }

        public int getPlannedDepartureWorldTime() {
            return getTime12bit(plannedDepartureAndArrivalTimeField, true);
        }

        public void setPlannedDepartureWorldTime(final int plannedDepartureWorldTime) {
            final LocalDateTime ldt = Time.toLdtOrNull(plannedDepartureWorldTime);
            setDateOfFlight(ldt != null ? ldt.toLocalDate() : null);
            setTime12bit(plannedDepartureAndArrivalTimeField, true, plannedDepartureWorldTime);
        }

        public int getPlannedArrivalWorldTime() {
            return getTime12bit(plannedDepartureAndArrivalTimeField, false);
        }

        public void setPlannedArrivalWorldTime(final int plannedArrivalWorldTime) {
            setTime12bit(plannedDepartureAndArrivalTimeField, false, plannedArrivalWorldTime);
        }

        public int getActualDepartureWorldTime() {
            return getTime12bit(actualDepartureAndTakeoffTimeField, true);
        }

        public void setActualDepartureWorldTime(final int actualDepartureWorldTime) {
            setTime12bit(actualDepartureAndTakeoffTimeField, true, actualDepartureWorldTime);
        }

        public int getActualTakeoffWorldTime() {
            return getTime12bit(actualDepartureAndTakeoffTimeField, false);
        }

        public void setActualTakeoffWorldTime(final int actualTakeoffWorldTime) {
            setTime12bit(actualDepartureAndTakeoffTimeField, false, actualTakeoffWorldTime);
        }

        public int getActualLandingWorldTime() {
            return getTime12bit(actualLandingAndArrivalTimeField, true);
        }

        public void setActualLandingWorldTime(final int actualLandingWorldTime) {
            setTime12bit(actualLandingAndArrivalTimeField, true, actualLandingWorldTime);
        }

        public int getActualArrivalWorldTime() {
            return getTime12bit(actualLandingAndArrivalTimeField, false);
        }

        public void setActualArrivalWorldTime(final int actualArrivalWorldTime) {
            setTime12bit(actualLandingAndArrivalTimeField, false, actualArrivalWorldTime);
        }

        public LocalDate getDateOfFlight() {
            final int days = storage.getAsIntUnsafe(id, dateOfFlightField);
            if (days == 0) {
                return null;
            }
            return DAY_BEFORE_FIRST_DAY.plusDays(days);
        }

        public void setDateOfFlight(final LocalDate dateOfFlight) {
            final int days = dateOfFlight != null
                    ? (int) ChronoUnit.DAYS.between(DAY_BEFORE_FIRST_DAY, dateOfFlight)
                    : 0;
            storage.setUnsafe(id, dateOfFlightField, days);
        }

        public int getUserId() {
            return storage.getAsInt(id, userIdField);
        }

        public void setUserId(final int userId) {
            storage.set(id, userIdField, userId);
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

        @Override
        public String toString() {
            return String.format("{ id: %s, status: %s }", id, getStatus());
        }
    }

    private int readStatusCode(int recordId) {
        final int statusRaw = storage.getAsInt(recordId, statusField);
        return statusRaw & ~allModesMask;
    }

    private void heartbeatTimeIndex_rebuild() {
        heartbeatTimeIndex.clear();
        storage.all().forEach(m -> heartbeatTimeIndex_add(m.getHeartbeatTime(), m.getId()));
        int minimalHeartbeatTime = heartbeatTimeIndex.firstKey();
        log.info("heartbeatTimeIndex_rebuild minimalHeartbeatTime: {}, size {}, size {}",
                minimalHeartbeatTime,
                heartbeatTimeIndex.get(minimalHeartbeatTime).size(),
                heartbeatTimeIndex.size());
    }

    private void heartbeatTimeIndex_remove(int heartbeatTime, int recordId) {
        List<Integer> ids = heartbeatTimeIndex.get(heartbeatTime);
        if (ids == null) {
            return;
        }
        if (ids.isEmpty()) {
            heartbeatTimeIndex.remove(heartbeatTime);
            return;
        }
        int index = ids.indexOf(recordId);
        if (index == -1) {
            return;
        }
        ids.remove(index);
    }

    private void heartbeatTimeIndex_add(int heartbeatTime, int recordId) {
        if (heartbeatTime == 0) {
            return;
        }
        heartbeatTimeIndex.computeIfAbsent(heartbeatTime, k -> new ArrayList<>()).add(recordId);
    }

    public String printHeartbeatTimeIndex() {
        StringBuffer sb = new StringBuffer();
        heartbeatTimeIndex.forEach((ts, list) -> sb
                .append(TimeTools.ts(ts)).append('\n')
                .append(list.toString()).append('\n')
                .append('\n'));
        return sb.toString();
    }

    private int readHeartbeatTime(int recordId) {
        return storage.getAsInt(recordId, heartbeatTimeField);
    }

    public enum Status {
        // available 0
        PlannedManually(1),
        PlannedViaSchedule(2),
        // available 3
        Dispatched(4),
        Preflight(5),
        Departure(6),
        Flying(7),
        Arrival(8),
        Postflight(9),
        Finished(10),
        // available 11
        // available 12
        Cancelled(13);
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
    public static final Comparator<Mission> sortByDepartureTimeFromPastToFuture = (m1, m2) -> m1.getPlannedDepartureWorldTime() - m2.getPlannedDepartureWorldTime();

}
