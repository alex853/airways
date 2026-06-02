package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.BitAccessField;
import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.tools.CabinLayout;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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
            .withDataField(DataField.of(DataType.Signed32bit)) // checked-in / on board / ...
            .build();

    private final DataField statusField = storage.getDataField(0);
    private final DataField heartbeatTimeField = storage.getDataField(1);
    private final DataField flightMissionIdField = storage.getDataField(2);
    private final DataField scheduledFlightIdField = storage.getDataField(3);
    private final DataField totalTicketsField = storage.getDataField(4);
    private final DataField remainedTicketsField = storage.getDataField(5);
    private final DataField paxCheckedInOnBoardField = storage.getDataField(6);
    private final BitAccessField paxCheckedInOnBoardFieldBits = BitAccessField.instance(storage, paxCheckedInOnBoardField);
    private final BitAccessField.Section paxCheckedInBitField = paxCheckedInOnBoardFieldBits.section(0, 10);
    private final BitAccessField.Section paxOnBoardBitField = paxCheckedInOnBoardFieldBits.section(10, 10);

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Flight create(final FlightMissions.Mission flightMission,
                         final ScheduledFlights.Flight scheduledFlight,
                         final CabinLayout totalTickets) {
        checkNotNull(flightMission);
        checkNotNull(totalTickets);
        final int id = storage.addRecord();
        final Flight flight = new Flight(id);
        flight.setStatus(Status.Scheduled);
        flight.setHeartbeatTime(0);
        storage.set(id, flightMissionIdField, flightMission.getId());
        storage.set(id, scheduledFlightIdField, scheduledFlight != null ? scheduledFlight.getId() : 0);
        storage.set(id, totalTicketsField, totalTickets.toSigned32bit());
        storage.set(id, remainedTicketsField, totalTickets.toSigned32bit());
        storage.set(id, paxCheckedInOnBoardField, 0);
        return flight;
    }

    public void deleteById(final int id) {
        storage.deleteRecord(id);
    }

    public Stream<Flight> all() {
        return storage.all();
    }

    @Deprecated
    public Collection<Flight> filter(final Predicate<Flight> condition) {
        return storage.filter(condition); // todo ak0 migrate to filter1
    }

    public Optional<Flight> nextForHeartbeat(final int worldTime) {
        return storage.findFirst(tf -> tf.getHeartbeatTime() <= worldTime
                && tf.getHeartbeatTime() != 0);
    }

    public Optional<Flight> byId(final int id) {
        return storage.byId(id);
    }

    public Optional<Flight> byFlightMissionId(final int flightMissionId) {
        return storage.findFirst(f -> f.getFlightMissionId() == flightMissionId);
    }

    @SuppressWarnings("LombokGetterMayBeUsed")
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

        public int getScheduledFlightId() {
            return storage.getAsInt(id, scheduledFlightIdField);
        }

        public CabinLayout getTotalTickets() {
            return CabinLayout.fromSigned32bit(storage.getAsInt(id, totalTicketsField));
        }

        public CabinLayout getRemainedTickets() {
            return CabinLayout.fromSigned32bit(storage.getAsInt(id, remainedTicketsField));
        }

        public void setRemainedTickets(final CabinLayout remainedTickets) {
            checkNotNull(remainedTickets);
            storage.set(id, remainedTicketsField, remainedTickets.toSigned32bit());
        }

        public int getSoldTickets() {
            return getTotalTickets().getTotal() - getRemainedTickets().getTotal();
        }

        public int getPaxCheckedIn() {
            return paxCheckedInBitField.getInt(id);
        }

        public void setPaxCheckedIn(final int paxCheckedIn) {
            checkArgument(paxCheckedIn >= 0);
            paxCheckedInBitField.setInt(id, paxCheckedIn);
        }

        public int getPaxOnBoard() {
            return paxOnBoardBitField.getInt(id);
        }

        public void setPaxOnBoard(final int paxOnBoard) {
            checkArgument(paxOnBoard >= 0);
            paxOnBoardBitField.setInt(id, paxOnBoard);
        }

        @Override
        public String toString() {
            return String.format("{ id: %s, status: %s }", id, getStatus());
        }
    }

    public enum Status {
        Scheduled(0),
        CheckIn(1),
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
        Cancelled(15);

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
