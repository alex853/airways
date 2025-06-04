package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.world.Time;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class FlightMissions {
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
            .withDataField(DataField.of(DataType.Signed32bit)) // plannedDepartureTime
            .withDataField(DataField.of(DataType.Signed32bit)) // plannedArrivalTime
            .withDataField(DataField.of(DataType.Signed32bit)) // actualDepartureTime
            .withDataField(DataField.of(DataType.Signed32bit)) // actualTakeoffTime
            .withDataField(DataField.of(DataType.Signed32bit)) // actualLandingTime
            .withDataField(DataField.of(DataType.Signed32bit)) // actualArrivalTime
            .build();
/*    private final Storage<Mission> storage = Storage.<Mission>builder()
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
            .withDataField(DataField.of(DataType.Unsigned8bit)) // reserved
            .withDataField(DataField.of(DataType.Signed32bit)) // reserved
            .withDataField(DataField.of(DataType.Signed32bit)) // reserved
            .withDataField(DataField.of(DataType.Signed32bit)) // reserved
            .build();*/
    // todo ak0 all those 6 time related fields can packed into 10-11 bytes instead of 24 bytes

    private static final int pcModeMask = 0b10000000;
    private static final int timeModeMask = 0b01000000;
    private static final int allModesMask = pcModeMask | timeModeMask;

    private final DataField aircraftIdField = storage.getDataField(0);
    private final DataField statusField = storage.getDataField(1);
    private final DataField heartbeatTimeField = storage.getDataField(2);
    private final DataField departureAirportIdField = storage.getDataField(3);
    private final DataField destinationAirportIdField = storage.getDataField(4);
    private final DataField plannedDepartureTimeField = storage.getDataField(5);
    private final DataField plannedArrivalTimeField = storage.getDataField(6);
    private final DataField actualDepartureTimeField = storage.getDataField(7);
    private final DataField actualTakeoffTimeField = storage.getDataField(8);
    private final DataField actualLandingTimeField = storage.getDataField(9);
    private final DataField actualArrivalTimeField = storage.getDataField(10);

    private final DataField dateOfFlightField = DataField.of(DataType.Unsigned16bit).after(destinationAirportIdField);
    private final DataField plannedDepartureAndArrivalTimeField = DataField.of(DataType.Unsigned24bit).after(dateOfFlightField);
    private final DataField actualDepartureAndTakeoffTimeField = DataField.of(DataType.Unsigned24bit).after(plannedDepartureAndArrivalTimeField);
    private final DataField actualLandingAndArrivalTimeField = DataField.of(DataType.Unsigned24bit).after(actualDepartureAndTakeoffTimeField);

    public FlightMissions() {
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
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
            final int modeBits = (isModePc() ? pcModeMask : 0);
            storage.set(id, statusField, statusCode ^ modeBits);
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

        private boolean isTimeMode() {
            return isStatusBitMode(timeModeMask);
        }

        private void setTimeMode(final boolean enabled) {
            setStatusBitMode(timeModeMask, enabled);
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

        public int getPlannedDepartureTime() {
            if (!isTimeMode()) {
                return storage.getAsInt(id, plannedDepartureTimeField);
            } else {
                return Time.fromLdLt(getDateOfFlight(), getPlannedDepartureTimeLT());
            }
        }

        public void setPlannedDepartureTime(final int plannedDepartureTime) {
            if (!isTimeMode()) {
                storage.set(id, plannedDepartureTimeField, plannedDepartureTime);
            } else {
                final LocalDateTime ldt = Time.toLdt(plannedDepartureTime);
                setDateOfFlight(ldt.toLocalDate());
                setPlannedDepartureTimeLT(ldt.toLocalTime());
            }
        }

        public int getPlannedArrivalTime() {
            if (!isTimeMode()) {
                return storage.getAsInt(id, plannedArrivalTimeField);
            } else {
                return Time.fromLdLt(getDateOfFlight(), getPlannedArrivalTimeLT());
            }
        }

        public void setPlannedArrivalTime(final int plannedArrivalTime) {
            if (!isTimeMode()) {
                storage.set(id, plannedArrivalTimeField, plannedArrivalTime);
            } else {
                setPlannedArrivalTimeLT(Time.toLtOrNull(plannedArrivalTime));
            }
        }

        public int getActualDepartureTime() {
            if (!isTimeMode()) {
                return storage.getAsInt(id, actualDepartureTimeField);
            } else {
                return Time.fromLdLt(getDateOfFlight(), getActualDepartureTimeLT());
            }
        }

        public void setActualDepartureTime(final int actualDepartureTime) {
            if (!isTimeMode()) {
                storage.set(id, actualDepartureTimeField, actualDepartureTime);
            } else {
                setActualDepartureTimeLT(Time.toLtOrNull(actualDepartureTime));
            }
        }

        public int getActualTakeoffTime() {
            if (!isTimeMode()) {
                return storage.getAsInt(id, actualTakeoffTimeField);
            } else {
                return Time.fromLdLt(getDateOfFlight(), getActualTakeoffTimeLT());
            }
        }

        public void setActualTakeoffTime(final int actualTakeoffTime) {
            if (!isTimeMode()) {
                storage.set(id, actualTakeoffTimeField, actualTakeoffTime);
            } else {
                setActualTakeoffTimeLT(Time.toLtOrNull(actualTakeoffTime));
            }
        }

        public int getActualLandingTime() {
            if (!isTimeMode()) {
                return storage.getAsInt(id, actualLandingTimeField);
            } else {
                return Time.fromLdLt(getDateOfFlight(), getActualLandingTimeLT());
            }
        }

        public void setActualLandingTime(final int actualLandingTime) {
            if (!isTimeMode()) {
                storage.set(id, actualLandingTimeField, actualLandingTime);
            } else {
                setActualLandingTimeLT(Time.toLtOrNull(actualLandingTime));
            }
        }

        public int getActualArrivalTime() {
            if (!isTimeMode()) {
                return storage.getAsInt(id, actualArrivalTimeField);
            } else {
                return Time.fromLdLt(getDateOfFlight(), getActualArrivalTimeLT());
            }
        }

        public void setActualArrivalTime(final int actualArrivalTime) {
            if (!isTimeMode()) {
                storage.set(id, actualArrivalTimeField, actualArrivalTime);
            } else {
                setActualArrivalTimeLT(Time.toLtOrNull(actualArrivalTime));
            }
        }

        private static final LocalDate DAY_BEFORE_FIRST_DAY = LocalDate.of(2024, 12, 31);

        public LocalDate getDateOfFlight() {
            checkArgument(isTimeMode());
            final int days = storage.getAsIntUnsafe(id, dateOfFlightField);
            if (days == 0) {
                return null;
            }
            return DAY_BEFORE_FIRST_DAY.plusDays(days);
        }

        public void setDateOfFlight(final LocalDate dateOfFlight) {
            checkArgument(isTimeMode());
            final int days = dateOfFlight != null
                    ? (int) ChronoUnit.DAYS.between(DAY_BEFORE_FIRST_DAY, dateOfFlight)
                    : 0;
            storage.setUnsafe(id, dateOfFlightField, days);
        }

        public LocalTime getPlannedDepartureTimeLT() {
            checkArgument(isTimeMode());
            return getLocalTimeFromHighOfU24(plannedDepartureAndArrivalTimeField);
        }

        public void setPlannedDepartureTimeLT(final LocalTime plannedDepartureTime) {
            checkArgument(isTimeMode());
            setLocalTimeToHighOfU24(plannedDepartureAndArrivalTimeField, plannedDepartureTime);
        }

        public LocalTime getPlannedArrivalTimeLT() {
            checkArgument(isTimeMode());
            return getLocalTimeFromLowOfU24(plannedDepartureAndArrivalTimeField);
        }

        public void setPlannedArrivalTimeLT(final LocalTime plannedArrivalTime) {
            checkArgument(isTimeMode());
            setLocalTimeToLowOfU24(plannedDepartureAndArrivalTimeField, plannedArrivalTime);
        }

        public LocalTime getActualDepartureTimeLT() {
            checkArgument(isTimeMode());
            return getLocalTimeFromHighOfU24(actualDepartureAndTakeoffTimeField);
        }

        public void setActualDepartureTimeLT(final LocalTime actualDepartureTime) {
            checkArgument(isTimeMode());
            setLocalTimeToHighOfU24(actualDepartureAndTakeoffTimeField, actualDepartureTime);
        }

        public LocalTime getActualTakeoffTimeLT() {
            checkArgument(isTimeMode());
            return getLocalTimeFromLowOfU24(actualDepartureAndTakeoffTimeField);
        }

        public void setActualTakeoffTimeLT(final LocalTime actualTakeoffTime) {
            checkArgument(isTimeMode());
            setLocalTimeToLowOfU24(actualDepartureAndTakeoffTimeField, actualTakeoffTime);
        }

        public LocalTime getActualLandingTimeLT() {
            checkArgument(isTimeMode());
            return getLocalTimeFromHighOfU24(actualLandingAndArrivalTimeField);
        }

        public void setActualLandingTimeLT(final LocalTime actualLandingTime) {
            checkArgument(isTimeMode());
            setLocalTimeToHighOfU24(actualLandingAndArrivalTimeField, actualLandingTime);
        }

        public LocalTime getActualArrivalTimeLT() {
            checkArgument(isTimeMode());
            return getLocalTimeFromLowOfU24(actualLandingAndArrivalTimeField);
        }

        public void setActualArrivalTimeLT(final LocalTime actualArrivalTime) {
            checkArgument(isTimeMode());
            setLocalTimeToLowOfU24(actualLandingAndArrivalTimeField, actualArrivalTime);
        }

        private LocalTime getLocalTimeFromHighOfU24(final DataField dataField) {
            return getLocalTimeFromU24(dataField, 0b111111111111000000000000, 12);
        }

        private LocalTime getLocalTimeFromLowOfU24(final DataField dataField) {
            return getLocalTimeFromU24(dataField, 0b000000000000111111111111, 0);
        }

        private void setLocalTimeToHighOfU24(final DataField dataField, final LocalTime localTime) {
            setLocalTimeToU24(dataField, localTime, 0b111111111111000000000000, 12);
        }

        private void setLocalTimeToLowOfU24(final DataField dataField, final LocalTime localTime) {
            setLocalTimeToU24(dataField, localTime, 0b000000000000111111111111, 0);
        }

        private LocalTime getLocalTimeFromU24(final DataField dataField, final int mask, final int shift) {
            final int raw = storage.getAsIntUnsafe(id, dataField);
            final int minutes = (raw & mask) >> shift;
            return minutes != 0 ? LocalTime.ofSecondOfDay(minutes * 60L) : null;
        }

        private void setLocalTimeToU24(final DataField dataField, final LocalTime localTime, final int mask, final int shift) {
            final Integer minutesRaw = localTime != null ? localTime.toSecondOfDay() / 60 : null;
            final int minutes = minutesRaw != null ? (minutesRaw == 0 ? 1440 : minutesRaw) : 0;
            final int shiftedMinutes = minutes << shift;
            final int raw = storage.getAsIntUnsafe(id, dataField);
            final int anotherPart = (raw & ~mask);
            storage.setUnsafe(id, dataField, shiftedMinutes | anotherPart);
        }

        public void convertTimeToLT() {
            if (isTimeMode()) {
                return;
            }

            final int plannedDepartureTime = getPlannedDepartureTime();
            final int plannedArrivalTime = getPlannedArrivalTime();
            final int actualDepartureTime = getActualDepartureTime();
            final int actualTakeoffTime = getActualTakeoffTime();
            final int actualLandingTime = getActualLandingTime();
            final int actualArrivalTime = getActualArrivalTime();

            setTimeMode(true);

            setDateOfFlight(Time.toLdOrNull(plannedDepartureTime));
            setPlannedDepartureTimeLT(Time.toLtOrNull(plannedDepartureTime));
            setPlannedArrivalTimeLT(Time.toLtOrNull(plannedArrivalTime));
            setActualDepartureTimeLT(Time.toLtOrNull(actualDepartureTime));
            setActualTakeoffTimeLT(Time.toLtOrNull(actualTakeoffTime));
            setActualLandingTimeLT(Time.toLtOrNull(actualLandingTime));
            setActualArrivalTimeLT(Time.toLtOrNull(actualArrivalTime));
        }

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

    public Mission createDispatchedMission(final Aircrafts.Aircraft aircraft,
                                           final Airports.Airport origin,
                                           final Airports.Airport destination,
                                           final int departureTime,
                                           final int arrivalTime) {
        final int id = storage.addRecord();
        storage.set(id, aircraftIdField, aircraft.getId());
        storage.set(id, statusField, Status.Dispatched.code());
        storage.set(id, departureAirportIdField, origin.getId());
        storage.set(id, destinationAirportIdField, destination.getId());
        storage.set(id, plannedDepartureTimeField, departureTime);
        storage.set(id, plannedArrivalTimeField, arrivalTime);
        return new Mission(id);
    }

    public static final Comparator<Mission> sortByDepartureTimeFromFutureToPast = (m1, m2) -> m2.getPlannedDepartureTime() - m1.getPlannedDepartureTime();

}
