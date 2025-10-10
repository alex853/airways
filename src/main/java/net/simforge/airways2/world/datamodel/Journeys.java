package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.BitAccessField;
import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.tools.CabinLayout;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class Journeys {
    private final Storage<Journey> storage = Storage.<Journey>builder()
            .name("journeys")
            .withInstantiator(Journey::new)
            .withIdOf(DataType.Unsigned24bit)
            // -------------------------------------------------- record deletion status    1 byte
            .withDataField(DataField.of(DataType.Unsigned8bit)) // status                   1 byte
            .withDataField(DataField.of(DataType.Signed32bit)) // heartbeatTime             4 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // fromCityId              2 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // toCityId                2 bytes
            .withDataField(DataField.of(DataType.Unsigned8bit)) // groupSize                1 byte
            .withDataField(DataField.of(DataType.Unsigned8bit)) // type/mode                1 byte
            .withDataField(DataField.of(DataType.Unsigned24bit)) // transportFlight1Id      3 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // transportFlight2Id      3 bytes
            // ----------------------------------------------------- sum                   18 bytes
            // -------------------------------------------- reserved 14 bytes to align total size to 32 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // reserve                 2 bytes
            .build();

    private final DataField statusField = storage.getDataField(0);
    private final DataField heartbeatTimeField = storage.getDataField(1);
    private final DataField fromCityIdField = storage.getDataField(2);
    private final DataField toCityIdField = storage.getDataField(3);
    private final DataField groupSizeField = storage.getDataField(4);
    private final DataField typeModeField = storage.getDataField(5);
    private final BitAccessField typeModeFieldBits = BitAccessField.instance(storage, typeModeField);
    private final BitAccessField.Section typeModeServiceBitField = typeModeFieldBits.section(0, 2);
    private final DataField transportFlight1IdField = storage.getDataField(6);
    private final DataField transportFlight2IdField = storage.getDataField(7);

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Collection<Journey> all() {
        return storage.all();
    }

    public Collection<Journey> filter(final Predicate<Journey> condition) {
        return storage.filter(condition);
    }

    public Optional<Journey> findFirst(final Storage.Condition<Journey> condition) {
        return storage.findFirst1(condition);
    }

    public Optional<Journey> byId(final int id) {
        return storage.byId(id);
    }

    public Optional<Journey> nextForHeartbeat(final int worldTime) {
        return storage.findFirst1(storage.nextForHeartbeatCondition(heartbeatTimeField, worldTime));
    }

    public Journey create(final Status status, final int fromCityId, final int toCityId, final int groupSize, final CabinLayout.Service service) {
        checkNotNull(status);
        checkArgument(fromCityId >= 1);
        checkArgument(toCityId >= 1);
        checkArgument(groupSize >= 1);

        final int id = storage.addRecord();
        final Journey journey = new Journey(id);
        journey.setStatus(status);
        journey.setHeartbeatTime(0);
        storage.set(id, fromCityIdField, fromCityId);
        storage.set(id, toCityIdField, toCityId);
        storage.set(id, groupSizeField, groupSize);
        typeModeServiceBitField.setInt(id, service.ordinal());

        return journey;
    }

    public void deleteById(final int id) {
        storage.deleteRecord(id);
    }

    public class Journey {
        private final int id;

        private Journey(final int id) {
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
            checkNotNull(status);
            storage.set(id, statusField, status.code());
        }

        public int getHeartbeatTime() {
            return storage.getAsInt(id, heartbeatTimeField);
        }

        public void setHeartbeatTime(final int heartbeatTime) {
            storage.set(id, heartbeatTimeField, heartbeatTime);
        }

        public int getFromCityId() {
            return storage.getAsInt(id, fromCityIdField);
        }

        public int getToCityId() {
            return storage.getAsInt(id, toCityIdField);
        }

        public int getGroupSize() {
            return storage.getAsInt(id, groupSizeField);
        }

        public CabinLayout.Service getCabinService() {
            return CabinLayout.Service.values()[typeModeServiceBitField.getInt(id)];
        }

        public int getTransportFlight1Id() {
            return storage.getAsInt(id, transportFlight1IdField);
        }

        public void setTransportFlight1Id(final int transportFlight1Id) {
            storage.set(id, transportFlight1IdField, transportFlight1Id);
        }

        public int getTransportFlight2Id() {
            return storage.getAsInt(id, transportFlight2IdField);
        }

        public void setTransportFlight2Id(final int transportFlight2Id) {
            storage.set(id, transportFlight2IdField, transportFlight2Id);
        }
    }

    public Storage.Condition<Journey> byTransportFlight1IdAndStatus(final int transportFlightId, final Status status) {
        checkArgument(transportFlightId > 0);
        checkNotNull(status);

        return recordId -> storage.getAsInt(recordId, transportFlight1IdField) == transportFlightId
                && storage.getAsInt(recordId, statusField) == status.code();
    }

    public enum Status {
        LookingForPersons(0),
        LookingForTickets(1),
        //todo ak2 persons WaitingForFlight(2),
        //todo ak2 persons TransferToAirport(3),
        WaitingForCheckIn(4),
        WaitingForBoarding(5),
        OnBoard(6),
        WaitingForDeboarding(7),
        JustArrived(8),
        //todo ak2 persons TransferToCity(...),
        ItinerariesDone(9),
        Finished(10),
        //todo ak2 persons CouldNotFindPersons(11),
        CouldNotFindTickets(12),
        TooLateToBoard(13),
        //todo ak2 not sure we need it Terminated(14),
        ;

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
}
