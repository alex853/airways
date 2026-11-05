package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.BitAccessField;
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
            .withDataField(DataField.of(DataType.Unsigned8bit)) // flags 1: status 0..15 (bits 0-3), auto finish (bit 4), coordinates source (bits 5-6), character mode (bit 7)
            .withDataField(DataField.of(DataType.Signed32bit)) // heartbeatTime
            .withDataField(DataField.of(DataType.Unsigned16bit)) // departureAirportId
            .withDataField(DataField.of(DataType.Unsigned16bit)) // destinationAirportId
            .withDataField(DataField.of(DataType.Unsigned16bit)) // dateOfFlight
            .withDataField(DataField.of(DataType.Unsigned24bit)) // plannedDepartureTime + plannedArrivalTime
            .withDataField(DataField.of(DataType.Unsigned24bit)) // actualDepartureTime + actualTakeoffTime
            .withDataField(DataField.of(DataType.Unsigned24bit)) // actualLandingTime + actualArrivalTime
            .withDataField(DataField.of(DataType.Unsigned16bit)) // actualLandingAirportId
            // ---------------------------------------------------------------------------------------------------------
            .withDataField(DataField.of(DataType.Unsigned8bit)) // flags 2: bit 0 night takeoff, bit 1 night landing, bits 2-3 tracking mode, bit 4 divert declared, bit 5 continuation, bits 6-7 are free
            .withDataField(DataField.of(DataType.Unsigned16bit)) // alternateAirportId: the planned alternate, 0 is none; with the divert bit of flags 2 it holds the airport of the divert instead (docs/diversions-and-alternates.md)
            .withDataField(DataField.of(DataType.Unsigned16bit)) // reserved
            .withDataField(DataField.of(DataType.Unsigned16bit)) // reserved
            .withDataField(DataField.of(DataType.Float16bit)) // nightMinutes, half float: the night time from blocks off to blocks on, measured by the sim tracker, see getNightMinutes
            // ---------------------------------------------------------------------------------------------------------
            .withDataField(DataField.of(DataType.Unsigned16bit)) // userId
            .withHeartbeatIndex(2) // heartbeatField index: position among withDataField calls (aircraftId=0, status=1, heartbeatTime=2)
            .build();

    private final DataField aircraftIdField = storage.getDataField(0);
    private final DataField flags1RawField = storage.getDataField(1);
    private final BitAccessField flags1Bits = BitAccessField.instance(storage, flags1RawField);
    private final BitAccessField.Section statusBitField = flags1Bits.section(0, 4);
    private final BitAccessField.Section autoFinishBitField = flags1Bits.section(4, 1);
    private final BitAccessField.Section coordinatesSourceBitField = flags1Bits.section(5, 2);
    private final BitAccessField.Section characterModeBitField = flags1Bits.section(7, 1);
    private final DataField heartbeatTimeField = storage.getDataField(2);
    private final DataField departureAirportIdField = storage.getDataField(3);
    private final DataField destinationAirportIdField = storage.getDataField(4);
    private final DataField dateOfFlightField = storage.getDataField(5);
    private final DataField plannedDepartureAndArrivalTimeField = storage.getDataField(6);
    private final DataField actualDepartureAndTakeoffTimeField = storage.getDataField(7);
    private final DataField actualLandingAndArrivalTimeField = storage.getDataField(8);
    private final DataField actualLandingAirportIdField = storage.getDataField(9);
    // two times of the date of flight in one 24-bit field: the first one is in the high 12 bits, the second one is in the low 12 bits, see DateMinutes
    private final BitAccessField plannedTimesBits = BitAccessField.instance(storage, plannedDepartureAndArrivalTimeField);
    private final BitAccessField.Section plannedDepartureTimeBitField = plannedTimesBits.section(DateMinutes.TIME_BITS, DateMinutes.TIME_BITS);
    private final BitAccessField.Section plannedArrivalTimeBitField = plannedTimesBits.section(0, DateMinutes.TIME_BITS);
    private final BitAccessField actualDepartureTakeoffTimesBits = BitAccessField.instance(storage, actualDepartureAndTakeoffTimeField);
    private final BitAccessField.Section actualDepartureTimeBitField = actualDepartureTakeoffTimesBits.section(DateMinutes.TIME_BITS, DateMinutes.TIME_BITS);
    private final BitAccessField.Section actualTakeoffTimeBitField = actualDepartureTakeoffTimesBits.section(0, DateMinutes.TIME_BITS);
    private final BitAccessField actualLandingArrivalTimesBits = BitAccessField.instance(storage, actualLandingAndArrivalTimeField);
    private final BitAccessField.Section actualLandingTimeBitField = actualLandingArrivalTimesBits.section(DateMinutes.TIME_BITS, DateMinutes.TIME_BITS);
    private final BitAccessField.Section actualArrivalTimeBitField = actualLandingArrivalTimesBits.section(0, DateMinutes.TIME_BITS);
    private final DataField flags2RawField = storage.getDataField(10);
    private final BitAccessField flags2Bits = BitAccessField.instance(storage, flags2RawField);
    private final BitAccessField.Section nightTakeoffBitField = flags2Bits.section(0, 1);
    private final BitAccessField.Section nightLandingBitField = flags2Bits.section(1, 1);
    private final BitAccessField.Section trackingModeBitField = flags2Bits.section(2, 2);
    private final BitAccessField.Section divertDeclaredBitField = flags2Bits.section(4, 1);
    private final BitAccessField.Section continuationBitField = flags2Bits.section(5, 1);
    private final DataField alternateAirportIdField = storage.getDataField(11);
    @SuppressWarnings("unused")
    private final DataField reserved16bitsField2 = storage.getDataField(12);
    @SuppressWarnings("unused")
    private final DataField reserved16bitsField3 = storage.getDataField(13);
    private final DataField nightMinutesField = storage.getDataField(14);
    private final DataField userIdField = storage.getDataField(15);


    // The ids of the missions of every user (the missions of a user are few, the missions are many): it is kept by the methods of this class and rebuilt after the
    // loading. It makes allByUserId() a lookup instead of a scan of all the missions, the dashboard and the sim tracker ask for it on every request.
    private final Map<Integer, Set<Integer>> missionIdsByUserId = new java.util.concurrent.ConcurrentHashMap<>();

    public FlightMissions() {
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
        rebuildMissionIdsByUserId();
    }

    private void rebuildMissionIdsByUserId() {
        missionIdsByUserId.clear();
        storage.all().forEach(mission -> indexUser(mission.getUserId(), mission.getId()));
    }

    private void indexUser(final int userId, final int missionId) {
        if (userId > 0) {
            missionIdsByUserId.computeIfAbsent(userId, id -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(missionId);
        }
    }

    private void unindexUser(final int userId, final int missionId) {
        if (userId > 0) {
            missionIdsByUserId.computeIfPresent(userId, (id, ids) -> {
                ids.remove(missionId);
                return ids.isEmpty() ? null : ids;
            });
        }
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
        storage.byId(id).ifPresent(mission -> unindexUser(mission.getUserId(), id)); // before the record is cleared by the deletion
        storage.deleteRecord(id);
    }

    public Stream<Mission> all() {
        return storage.all();
    }

    public Stream<Mission> filter(final Storage.Condition<Mission> condition) {
        return storage.filter(condition);
    }

    public Optional<Mission> byId(final int id) {
        return storage.byId(id);
    }

    public Stream<Mission> allForAircraft(final Aircrafts.Aircraft aircraft) {
        checkNotNull(aircraft, "aircraft is mandatory");
        return storage.filter(recordId -> storage.getAsInt(recordId, aircraftIdField) == aircraft.getId());
    }

    public Stream<Mission> allByUserId(final int userId) {
        checkArgument(userId > 0, "userId is mandatory");
        // in the order of the ids, as the scan of the storage was
        return missionIdsByUserId.getOrDefault(userId, Set.of()).stream()
                .sorted()
                .map(storage::byId)
                .flatMap(Optional::stream);
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
        return storage.nextForHeartbeat(worldTime);
    }

    /**
     * Differences between the heartbeat index and the data, an empty list means they are consistent (a scan, for tests and diagnostics).
     */
    public List<String> checkHeartbeatIndex() {
        return storage.checkHeartbeatIndex();
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
            statusBitField.setInt(id, status.code());
        }

        /**
         * The flight of a pilot is finished by itself when it comes to the end (see FlightMissionProcessor). The setting of the pilot is copied here when the
         * flight starts, so the change of the setting in the middle of a flight does not change this flight.
         */
        public boolean isAutoFinish() {
            return autoFinishBitField.getBoolean(id);
        }

        public void setAutoFinish(final boolean autoFinish) {
            autoFinishBitField.setBoolean(id, autoFinish);
        }

        public CharacterMode getCharacterMode() {
            return CharacterMode.byCode(characterModeBitField.getInt(id));
        }

        public void setCharacterMode(CharacterMode characterMode) {
            checkNotNull(characterMode);
            characterModeBitField.setInt(id, characterMode.ordinal());
        }

        /**
         * The takeoff was at night, see DayNight. False for the old records and while the takeoff has not happened.
         */
        public boolean isNightTakeoff() {
            return nightTakeoffBitField.getBoolean(id);
        }

        public void setNightTakeoff(final boolean nightTakeoff) {
            nightTakeoffBitField.setBoolean(id, nightTakeoff);
        }

        public boolean isNightLanding() {
            return nightLandingBitField.getBoolean(id);
        }

        public void setNightLanding(final boolean nightLanding) {
            nightLandingBitField.setBoolean(id, nightLanding);
        }

        /**
         * The night time of the flight in minutes (from blocks off to blocks on, the taxi included), as the sim tracker has measured it. It is a half float
         * (the step is 1 minute up to 34 hours), so this is not an accumulator: the sim tracker keeps the exact value in its context and only writes it here,
         * to restore it after a restart. Zero for the old records and the flights which are not tracked.
         */
        public float getNightMinutes() {
            return storage.getAsFloat(id, nightMinutesField);
        }

        public void setNightMinutes(final float nightMinutes) {
            checkArgument(nightMinutes >= 0, "night time can not be negative");
            storage.set(id, nightMinutesField, nightMinutes);
        }

        /**
         * Who does the steps of the flight (blocks off, takeoff, landing, blocks on): set when the flight is started and is not changed after that.
         * None for a flight which is not started yet, for a flight of an NPC and for a flight started before the mode was stored.
         */
        public TrackingMode getTrackingMode() {
            return TrackingMode.byCode(trackingModeBitField.getInt(id));
        }

        public void setTrackingMode(final TrackingMode trackingMode) {
            checkNotNull(trackingMode);
            trackingModeBitField.setInt(id, trackingMode.ordinal());
        }

        public CoordinatesSource getCoordinatesSource() {
            return CoordinatesSource.byCode(coordinatesSourceBitField.getInt(id));
        }

        public void setCoordinatesSource(CoordinatesSource coordinatesSource) {
            checkNotNull(coordinatesSource);
            coordinatesSourceBitField.setInt(id, coordinatesSource.ordinal());
        }

        public int getHeartbeatTime() {
            return readHeartbeatTime(id);
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

        public void setDestinationAirportId(int destinationAirportId) {
            storage.set(id, destinationAirportIdField, destinationAirportId);
        }

        public int getActualLandingAirportId() {
            return storage.getAsInt(id, actualLandingAirportIdField);
        }

        public void setActualLandingAirportId(final int actualLandingAirportId) {
            storage.set(id, actualLandingAirportIdField, actualLandingAirportId);
        }

        /**
         * The alternate airport of the flight, 0 when there is none (and for the old records, and when a divert is declared: the same field holds the divert then).
         */
        public int getAlternateAirportId() {
            return isDivertDeclared() ? 0 : storage.getAsInt(id, alternateAirportIdField);
        }

        public void setAlternateAirportId(final int alternateAirportId) {
            storage.set(id, alternateAirportIdField, alternateAirportId);
            divertDeclaredBitField.setInt(id, 0);
        }

        /**
         * A divert is declared in the flight: the pilot has chosen an airport to land at instead of the destination (the planned alternate is lost).
         */
        public boolean isDivertDeclared() {
            return divertDeclaredBitField.getInt(id) != 0;
        }

        /**
         * The mission goes on after a landing at a wrong airport: the transport flight of the first mission was moved to it (FlightMissionControl.continueNow). When such a
         * mission is cancelled, the transportation of the first one is cancelled too.
         */
        public boolean isContinuation() {
            return continuationBitField.getInt(id) != 0;
        }

        public void setContinuation(final boolean continuation) {
            continuationBitField.setInt(id, continuation ? 1 : 0);
        }

        /**
         * The airport of the declared divert, 0 when there is none.
         */
        public int getDivertAirportId() {
            return isDivertDeclared() ? storage.getAsInt(id, alternateAirportIdField) : 0;
        }

        /**
         * Declares the divert to the airport (the planned alternate is overwritten), or takes it back with 0 (the field is cleared, otherwise it would read as the planned alternate).
         */
        public void setDivertAirportId(final int divertAirportId) {
            storage.set(id, alternateAirportIdField, divertAirportId);
            divertDeclaredBitField.setInt(id, divertAirportId > 0 ? 1 : 0);
        }

        /**
         * The airport the flight is going to land at: the declared divert or the destination.
         */
        public int getTargetAirportId() {
            return isDivertDeclared() ? getDivertAirportId() : getDestinationAirportId();
        }

        public int getPlannedDepartureWorldTime() {
            return getTime(plannedDepartureTimeBitField);
        }

        public void setPlannedDepartureWorldTime(final int plannedDepartureWorldTime) {
            final LocalDateTime ldt = Time.toLdtOrNull(plannedDepartureWorldTime);
            setDateOfFlight(ldt != null ? ldt.toLocalDate() : null);
            setTime(plannedDepartureTimeBitField, plannedDepartureWorldTime);
        }

        public int getPlannedArrivalWorldTime() {
            return getTime(plannedArrivalTimeBitField);
        }

        public void setPlannedArrivalWorldTime(final int plannedArrivalWorldTime) {
            setTime(plannedArrivalTimeBitField, plannedArrivalWorldTime);
        }

        public int getActualDepartureWorldTime() {
            return getTime(actualDepartureTimeBitField);
        }

        public void setActualDepartureWorldTime(final int actualDepartureWorldTime) {
            setTime(actualDepartureTimeBitField, actualDepartureWorldTime);
        }

        public int getActualTakeoffWorldTime() {
            return getTime(actualTakeoffTimeBitField);
        }

        public void setActualTakeoffWorldTime(final int actualTakeoffWorldTime) {
            setTime(actualTakeoffTimeBitField, actualTakeoffWorldTime);
        }

        public int getActualLandingWorldTime() {
            return getTime(actualLandingTimeBitField);
        }

        public void setActualLandingWorldTime(final int actualLandingWorldTime) {
            setTime(actualLandingTimeBitField, actualLandingWorldTime);
        }

        public int getActualArrivalWorldTime() {
            return getTime(actualArrivalTimeBitField);
        }

        public void setActualArrivalWorldTime(final int actualArrivalWorldTime) {
            setTime(actualArrivalTimeBitField, actualArrivalWorldTime);
        }

        public LocalDate getDateOfFlight() {
            return DateMinutes.fromDays(storage.getAsIntUnsafe(id, dateOfFlightField));
        }

        public void setDateOfFlight(final LocalDate dateOfFlight) {
            storage.setUnsafe(id, dateOfFlightField, DateMinutes.toDays(dateOfFlight));
        }

        public int getUserId() {
            return storage.getAsInt(id, userIdField);
        }

        public void setUserId(final int userId) {
            unindexUser(getUserId(), id);
            storage.set(id, userIdField, userId);
            indexUser(userId, id);
        }

        private int getTime(final BitAccessField.Section section) {
            return DateMinutes.fromStored(getDateOfFlight(), section.getInt(id));
        }

        private void setTime(final BitAccessField.Section section, final int worldTime) {
            section.setInt(id, DateMinutes.toStored(getDateOfFlight(), worldTime));
        }

        @Override
        public String toString() {
            return String.format("{ id: %s, status: %s }", id, getStatus());
        }
    }

    private int readStatusCode(int recordId) {
        return statusBitField.getInt(recordId);
    }

    public String printHeartbeatTimeIndex() {
        final StringBuilder sb = new StringBuilder();
        storage.heartbeatIndexEntries().forEach(e -> sb
                .append(TimeTools.ts(e[0])).append(' ').append(e[1]).append(System.lineSeparator()));
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

    public enum CharacterMode {
        NPC, PC;

        public static CharacterMode byCode(int code) {
            return switch (code) {
                case 0 -> NPC;
                case 1 -> PC;
                default -> throw new IllegalArgumentException("Invalid character mode code: " + code);
            };
        }
    }

    public enum TrackingMode {
        None, Manual, SimTracker, Vatsim;

        public static TrackingMode byCode(final int code) {
            return switch (code) {
                case 0 -> None;
                case 1 -> Manual;
                case 2 -> SimTracker;
                case 3 -> Vatsim;
                default -> throw new IllegalArgumentException("Invalid tracking mode code: " + code);
            };
        }
    }

    public enum CoordinatesSource {
        // FlyToTarget: the aircraft of a manual flight of a pilot flies step by step to the target airport (the destination or the divert) from where it is
        AutomaticSimpleFlight, TrackedViaTracker, FlyHeadingMode, FlyToTarget;

        public static CoordinatesSource byCode(int code) {
            return switch (code) {
                case 0 -> AutomaticSimpleFlight;
                case 1 -> TrackedViaTracker;
                case 2 -> FlyHeadingMode;
                case 3 -> FlyToTarget;
                default -> throw new IllegalArgumentException("Invalid coordinates source code: " + code);
            };
        }
    }

    public static final Comparator<Mission> sortByDepartureTimeFromFutureToPast = (m1, m2) -> m2.getPlannedDepartureWorldTime() - m1.getPlannedDepartureWorldTime();
    /**
     * The order of the flights of a pilot or an aircraft: by the time of the departure. A flight which has departed already is placed by its actual blocks off, not by the plan:
     * it can leave much earlier than planned, and another flight of the roster may be moved to the time of the plan of this one (it has the same planned departure, or even an
     * earlier one), but it is still after it. The ties are broken by the plan and then by the id, so the order does not depend on how the storage returns the flights.
     */
    public static final Comparator<Mission> sortByDepartureTimeFromPastToFuture = Comparator
            .comparingInt((Mission m) -> m.getActualDepartureWorldTime() != 0 ? m.getActualDepartureWorldTime() : m.getPlannedDepartureWorldTime())
            .thenComparingInt(Mission::getPlannedDepartureWorldTime)
            .thenComparingInt(Mission::getId);

}
