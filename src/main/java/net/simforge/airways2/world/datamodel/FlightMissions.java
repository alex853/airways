package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

public class FlightMissions {
    private final Storage<Mission> storage = Storage.<Mission>builder()
            .name("flight-missions")
            .withInstantiator(Mission::new)
            .withIdOf(DataType.Unsigned24bit)
            .withDataField(DataField.of(DataType.Unsigned24bit)) // aircraftId
            .withDataField(DataField.of(DataType.Unsigned8bit)) // status
            .withDataField(DataField.of(DataType.Signed32bit)) // heartbeatTime
            .withDataField(DataField.of(DataType.Unsigned16bit)) // departureAirportId
            .withDataField(DataField.of(DataType.Unsigned16bit)) // destinationAirportId
            .withDataField(DataField.of(DataType.Signed32bit)) // plannedDepartureTime
            .withDataField(DataField.of(DataType.Signed32bit)) // plannedArrivalTime
            .withDataField(DataField.of(DataType.Signed32bit)) // actualDepartureTime
            .withDataField(DataField.of(DataType.Signed32bit)) // actualTakeoffTime
            .withDataField(DataField.of(DataType.Signed32bit)) // actualLandingTime
            .withDataField(DataField.of(DataType.Signed32bit)) // actualArrivalTime
            .build();

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
        final List<FlightMissions.Mission> allMissions = allForAircraft(aircraft);
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
            return Status.byCode(getStatusRaw());
        }

        public int getStatusRaw() {
            return storage.getAsInt(id, statusField);
        }

        public void setStatus(final Status status) {
            checkNotNull(status, "status is mandatory");
            storage.set(id, statusField, status.code());
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

        public int getPlannedDepartureTime() {
            return storage.getAsInt(id, plannedDepartureTimeField);
        }

        public int getDestinationAirportId() {
            return storage.getAsInt(id, destinationAirportIdField);
        }

        public int getPlannedArrivalTime() {
            return storage.getAsInt(id, plannedArrivalTimeField);
        }

        public int getActualDepartureTime() {
            return storage.getAsInt(id, actualDepartureTimeField);
        }

        public void setActualDepartureTime(final int actualDepartureTime) {
            storage.set(id, actualDepartureTimeField, actualDepartureTime);
        }

        public int getActualTakeoffTime() {
            return storage.getAsInt(id, actualTakeoffTimeField);
        }

        public void setActualTakeoffTime(final int actualTakeoffTime) {
            storage.set(id, actualTakeoffTimeField, actualTakeoffTime);
        }

        public int getActualLandingTime() {
            return storage.getAsInt(id, actualLandingTimeField);
        }

        public void setActualLandingTime(final int actualLandingTime) {
            storage.set(id, actualLandingTimeField, actualLandingTime);
        }

        public int getActualArrivalTime() {
            return storage.getAsInt(id, actualArrivalTimeField);
        }

        public void setActualArrivalTime(final int actualArrivalTime) {
            storage.set(id, actualArrivalTimeField, actualArrivalTime);
        }

        @Override
        public String toString() {
            return String.format("{ id: %s, status: %s }", id, getStatus());
        }
    }

    public enum Status {
        Planned(1),
        // todo ak2 Assigned(20)?
        Preflight(30),
        Departure(40),
        Flying(50),
        Arrival(60),
        Postflight(70),
        Finished(100),
        Cancelled(99);

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

    public Mission createPlannedMission(final Aircrafts.Aircraft aircraft,
                                        final Airports.Airport origin,
                                        final Airports.Airport destination,
                                        final int departureTime,
                                        final int arrivalTime) {
        final int id = storage.addRecord();
        storage.set(id, aircraftIdField, aircraft.getId());
        storage.set(id, statusField, Status.Planned.code());
        storage.set(id, departureAirportIdField, origin.getId());
        storage.set(id, destinationAirportIdField, destination.getId());
        storage.set(id, plannedDepartureTimeField, departureTime);
        storage.set(id, plannedArrivalTimeField, arrivalTime);
        return new Mission(id);
    }

    public static final Comparator<Mission> sortByDepartureTimeFromFutureToPast = (m1, m2) -> m2.getPlannedDepartureTime() - m1.getPlannedDepartureTime();

    public static boolean isFinishedOrCancelledOrEmpty(final Optional<Mission> mission) {
        return mission.isEmpty()
                || mission.get().getStatus() == FlightMissions.Status.Finished
                || mission.get().getStatus() == FlightMissions.Status.Cancelled;
    }
}
