package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public class TransportFlights {
    private final Storage<Flight> storage = Storage.<Flight>builder()
            .name("transport-flights")
            .withInstantiator(Flight::new)
            .withIdOf(DataType.Unsigned24bit)
            .withDataField(DataField.of(DataType.Unsigned8bit)) // status
            .withDataField(DataField.of(DataType.Signed32bit)) // heartbeatTime
            .withDataField(DataField.of(DataType.Unsigned24bit)) // flightMissionId
            .withDataField(DataField.of(DataType.Unsigned24bit)) // scheduledFlightId
            .withDataField(DataField.of(DataType.Signed32bit)) // total tickets - 10bits Y(economy), 8bits W(premium economy), 7bits J(business), 5bit F(first)
            .withDataField(DataField.of(DataType.Signed32bit)) // remained tickets
            .withDataField(DataField.of(DataType.Signed32bit)) // pax on board
            .build();

    private final DataField statusField = storage.getDataField(0);
    private final DataField heartbeatTimeField = storage.getDataField(1);
    private final DataField flightMissionIdField = storage.getDataField(2);
    private final DataField scheduledFlightIdField = storage.getDataField(3);
    // tickets... fields

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Flight create(final FlightMissions.Mission flightMission,
                         final ScheduledFlights.Flight scheduledFlight,
                         final int economyClassTickets) {
        final int id = storage.addRecord();
        final Flight flight = new Flight(id);
        flight.setStatus(Status.Scheduled);
        flight.setHeartbeatTime(0);
        storage.set(id, flightMissionIdField, flightMission.getId());
        storage.set(id, scheduledFlightIdField, scheduledFlight.getId());
        // todo ak1 tickets
        return flight;
    }

    public Optional<Flight> byFlightMissionId(final int flightMissionId) {
        return storage.findFirst(f -> f.getFlightMissionId() == flightMissionId);
    }

    public class Flight {
        private final int id;

        private Flight(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public Status getStatus() {
            return Status.byCode(getStatusCode());
        }

        public int getStatusCode() {
            return storage.getAsInt(id, statusField);
        }

        public void setStatus(final Status status) {
            storage.set(id, statusField, status.code());
        }

        public int getHeartbeatTime() {
            return storage.getAsInt(id, heartbeatTimeField);
        }

        public void setHeartbeatTime(final int heartbeatTime) {
            storage.set(id, heartbeatTimeField, heartbeatTime);
        }

        public int getFlightMissionId() {
            return storage.getAsInt(id, flightMissionIdField);
        }
    }

    public enum Status {
        Scheduled(0),
        Checkin(1),
        WaitingForBoarding(2),
        Boarding(3),
        WaitingForDeparture(4),
        Departure(5),
        Flying(6),
        Arrival(7),
        WaitingForDeboarding(8),
        Deboarding(9),
        Finished(10),
//        CancellationRequested(9001),
//        Cancelled(9999)
        ;

        private final int code;

        Status(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static Status byCode(int code) {
            for (Status value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return code + " - " + name();
        }
    }
}
