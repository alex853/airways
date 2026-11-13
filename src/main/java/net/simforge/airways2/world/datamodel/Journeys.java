package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.BitAccessField;
import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.tools.CabinLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class Journeys {
    private static final Logger log = LoggerFactory.getLogger(Journeys.class);

    private final Storage<Journey> storage = Storage.<Journey>builder()
            .name("journeys")
            .withInstantiator(Journey::new)
            .withIdOf(DataType.Unsigned24bit)
            // the storage is big and gets new records all the time, a growth of one array copies it whole: so it is the pages, nothing is copied (docs/storage-paged.md)
            .paged()
            // -------------------------------------------------- record deletion status       1 byte
            .withDataField(DataField.of(DataType.Unsigned8bit)) // status                      1 byte
            .withDataField(DataField.of(DataType.Signed32bit)) // heartbeatTime                4 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // fromCityId                 2 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // toCityId                   2 bytes
            .withDataField(DataField.of(DataType.Unsigned8bit)) // groupSize                   1 byte
            .withDataField(DataField.of(DataType.Unsigned8bit)) // type/mode                   1 byte
            .withDataField(DataField.of(DataType.Unsigned24bit)) // transportFlight1Id         3 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // transportFlight2Id         3 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // locationCityId             2 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // lastKnownLocationCityId    2 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // price1 (the first flight)  3 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // price2 (the second flight) 3 bytes
            // ----------------------------------------------------- used                     28 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                      4 bytes
            // ----------------------------------------------------- total                    32 bytes
            .withHeartbeatIndex(1) // heartbeatField index: position among withDataField calls (status=0, heartbeatTime=1)
            .build();

    private final DataField statusRawField = storage.getDataField(0);
    private final BitAccessField statusFieldBits = BitAccessField.instance(storage, statusRawField);
    private final BitAccessField.Section statusBitField = statusFieldBits.section(0, 4);
    // bit 6: the journey of a pilot (a person travels by airline, see Persons.Person.getPilotJourneyId): the special handling of such a journey is by this bit
    private final BitAccessField.Section pilotJourneyBitField = statusFieldBits.section(6, 1);
    // bit 5: the special group (the immortal journey, see Journey.isSpecialGroup and docs/special-group-journeys.md)
    private final BitAccessField.Section specialGroupBitField = statusFieldBits.section(5, 1);
    private final BitAccessField.Section returningBackBitField = statusFieldBits.section(7, 1);
    private final DataField heartbeatTimeField = storage.getDataField(1);
    private final DataField fromCityIdField = storage.getDataField(2);
    private final DataField toCityIdField = storage.getDataField(3);
    private final DataField groupSizeField = storage.getDataField(4);
    private final DataField typeModeRawField = storage.getDataField(5);
    private final BitAccessField typeModeFieldBits = BitAccessField.instance(storage, typeModeRawField);
    private final BitAccessField.Section preferredCabinServiceBitField = typeModeFieldBits.section(0, 2);
    private final BitAccessField.Section attemptCounterBitField = typeModeFieldBits.section(2, 2);
    private final BitAccessField.Section bookedCabinServiceBitField = typeModeFieldBits.section(5, 2);
    private final DataField transportFlight1IdField = storage.getDataField(6);
    private final DataField transportFlight2IdField = storage.getDataField(7);
    private final DataField locationCityIdField = storage.getDataField(8);
    private final DataField lastKnownLocationCityIdField = storage.getDataField(9);
    private final DataField price1Field = storage.getDataField(10);
    private final DataField price2Field = storage.getDataField(11);

    public static final int MAX_PRICE = (int) DataType.Unsigned24bit.maxValue();

    // The journeys of every transport flight, by both of its flight fields (transportFlight1Id and transportFlight2Id): the index is an array of arrays, the index in the outer
    // one is the id of the transport flight, the inner one holds the ids of the journeys, sorted. A change makes a new inner array (the old one stays as it was for whoever
    // is walking along it), so a journey may be changed while the journeys of its flight are walked. It replaces the scans of all the journeys of the world (about 90 ms each).
    private static final int[] NO_IDS = new int[0];
    private int[][] journeyIdsByTransportFlightId = new int[0][];

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
        rebuildTransportFlightIndex();
        logPageFill("after load");
    }

    private void rebuildTransportFlightIndex() {
        journeyIdsByTransportFlightId = new int[0][];
        storage.all().forEach(journey -> {
            indexTransportFlight(journey.getTransportFlight1Id(), journey.getId());
            indexTransportFlight(journey.getTransportFlight2Id(), journey.getId());
        });
    }

    // the id of the journey is put to the journeys of the flight (nothing if it is there already)
    private void indexTransportFlight(final int transportFlightId, final int journeyId) {
        if (transportFlightId <= 0) {
            return;
        }
        if (transportFlightId >= journeyIdsByTransportFlightId.length) {
            journeyIdsByTransportFlightId = Arrays.copyOf(journeyIdsByTransportFlightId, Math.max(transportFlightId + 1, journeyIdsByTransportFlightId.length * 2));
        }
        final int[] ids = journeyIdsByTransportFlightId[transportFlightId];
        if (ids == null) {
            journeyIdsByTransportFlightId[transportFlightId] = new int[]{journeyId};
            return;
        }
        final int position = Arrays.binarySearch(ids, journeyId);
        if (position >= 0) {
            return;
        }
        final int insertAt = -position - 1;
        final int[] newIds = new int[ids.length + 1];
        System.arraycopy(ids, 0, newIds, 0, insertAt);
        newIds[insertAt] = journeyId;
        System.arraycopy(ids, insertAt, newIds, insertAt + 1, ids.length - insertAt);
        journeyIdsByTransportFlightId[transportFlightId] = newIds;
    }

    private void unindexTransportFlight(final int transportFlightId, final int journeyId) {
        if (transportFlightId <= 0 || transportFlightId >= journeyIdsByTransportFlightId.length) {
            return;
        }
        final int[] ids = journeyIdsByTransportFlightId[transportFlightId];
        if (ids == null) {
            return;
        }
        final int position = Arrays.binarySearch(ids, journeyId);
        if (position < 0) {
            return;
        }
        if (ids.length == 1) {
            journeyIdsByTransportFlightId[transportFlightId] = null;
            return;
        }
        final int[] newIds = new int[ids.length - 1];
        System.arraycopy(ids, 0, newIds, 0, position);
        System.arraycopy(ids, position + 1, newIds, position, ids.length - position - 1);
        journeyIdsByTransportFlightId[transportFlightId] = newIds;
    }

    private Stream<Journey> journeysOfTransportFlight(final int transportFlightId) {
        checkArgument(transportFlightId > 0);

        final int[] ids = transportFlightId < journeyIdsByTransportFlightId.length && journeyIdsByTransportFlightId[transportFlightId] != null
                ? journeyIdsByTransportFlightId[transportFlightId]
                : NO_IDS;
        return Arrays.stream(ids).mapToObj(storage::byId).flatMap(Optional::stream);
    }

    /**
     * The journeys which have the transport flight as the first or as the second one, the lowest id first. The journeys are the ones of the moment of the call, a journey
     * which is changed or deleted while the stream is walked is still in it (check what is needed in the stream).
     */
    public Stream<Journey> allByAnyTransportFlightId(final int transportFlightId) {
        return journeysOfTransportFlight(transportFlightId);
    }

    /**
     * The journeys whose first transport flight is the one, the lowest id first (a journey which has been changed before the stream is walked is checked again).
     */
    public Stream<Journey> allByTransportFlight1Id(final int transportFlightId) {
        return journeysOfTransportFlight(transportFlightId).filter(journey -> journey.getTransportFlight1Id() == transportFlightId);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    /**
     * Cuts the dead end of the storage (see Storage#tryTrimTail): the journeys die in a wave and the last pages become empty, then they are released. Called before the
     * world is saved. The ids of the dropped journeys are given to the new ones again.
     */
    public void tryTrimTail() {
        logPageFill("before tail trim");
        final Storage.TrimResult result = storage.tryTrimTail();
        log.info("j/y - paged storage tail trim: {} records on {} pages before, dropped {} records and {} pages, {} records on {} pages remain; "
                        + "the last page holds {} records, {} of them live{}; {} deleted records are known",
                result.recordsBefore(), result.pagesBefore(), result.droppedRecords(), result.droppedPages(),
                result.recordsBefore() - result.droppedRecords(), result.pagesAfter(),
                result.recordsOnLastPage(), result.liveOnLastPage(),
                result.droppedRecords() == 0 ? ", nothing to drop" : "",
                result.deletedRecordsKnown());
    }

    // how full the pages of the storage are, in percent of the existing journeys, see Storage#getPageFillPercents
    private void logPageFill(final String when) {
        final int[] percents = storage.getPageFillPercents();
        log.info("j/y - paged storage status ({}): {} pages, fill of each page in % of existing records: {}", when, percents.length,
                String.join(",", Arrays.stream(percents).mapToObj(String::valueOf).toList()));
    }

    public Stream<Journey> all() {
        return storage.all();
    }

    public Stream<Journey> filter(final Storage.Condition<Journey> condition) {
        return storage.filter(condition);
    }

    public Optional<Journey> findFirst(final Storage.Condition<Journey> condition) {
        return storage.findFirst(condition);
    }

    public Optional<Journey> byId(final int id) {
        return storage.byId(id);
    }

    public Optional<Journey> nextForHeartbeat(final int worldTime) {
        return storage.nextForHeartbeat(worldTime);
    }

    /**
     * Differences between the heartbeat index and the data, an empty list means they are consistent (a scan, for tests and diagnostics).
     */
    public List<String> checkHeartbeatIndex() {
        return storage.checkHeartbeatIndex();
    }

    public Stream<Journey> allWithZeroHeartbeat() {
        return storage.filter(storage.byZeroHeartbeat(heartbeatTimeField));
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
        preferredCabinServiceBitField.setInt(id, service.ordinal());
        bookedCabinServiceBitField.setInt(id, service.ordinal());
        returningBackBitField.setBoolean(id, false);
        pilotJourneyBitField.setBoolean(id, false);
        specialGroupBitField.setBoolean(id, false);
        attemptCounterBitField.setInt(id, 0);

        return journey;
    }

    public void deleteById(final int id) {
        storage.byId(id).ifPresent(journey -> { // before the record is cleared by the deletion
            unindexTransportFlight(journey.getTransportFlight1Id(), id);
            unindexTransportFlight(journey.getTransportFlight2Id(), id);
        });
        storage.deleteRecord(id);
    }

    @SuppressWarnings("LombokGetterMayBeUsed")
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
            return readStatusCode(id);
        }

        public void setStatus(final Status status) {
            checkNotNull(status);
            statusBitField.setInt(id, status.code());
        }

        /**
         * This is the journey of a pilot (one person, city to city, travel by airline): it is looked after in its own way (the search every 10-15 minutes, no limit of the attempts,
         * the payment and the exit are the pilot's). The person of the journey is found by Persons.byPilotJourneyId.
         */
        public boolean isPilotJourney() {
            return pilotJourneyBitField.getBoolean(id);
        }

        public void setPilotJourney(final boolean pilotJourney) {
            pilotJourneyBitField.setBoolean(id, pilotJourney);
        }

        /**
         * The special group: an immortal journey (a fixed group of travellers who go from city to city for ever). It is an ordinary journey for the search and for the bookings (BusyBirds
         * and ShadowJet see it as any other), but it never ends: instead of CouldNotFindTickets it chooses another random city, instead of the return trip it goes to a random city,
         * and it is never Finished (JourneyControl). It is not of a flow of the cities (no success rate).
         */
        public boolean isSpecialGroup() {
            return specialGroupBitField.getBoolean(id);
        }

        public void setSpecialGroup(final boolean specialGroup) {
            specialGroupBitField.setBoolean(id, specialGroup);
        }

        public boolean isReturningBack() {
            return returningBackBitField.getBoolean(id);
        }

        public void setReturningBack(final boolean returningBack) {
            returningBackBitField.setBoolean(id, returningBack);
        }

        public int getAttemptCounter() {
            return attemptCounterBitField.getInt(id);
        }

        public void setAttemptCounter(final int attemptCounter) {
            checkArgument(0 <= attemptCounter && attemptCounter <= 3);
            attemptCounterBitField.setInt(id, attemptCounter);
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

        public void setFromCityId(final int fromCityId) {
            checkArgument(fromCityId > 0);
            storage.set(id, fromCityIdField, fromCityId);
        }

        public int getToCityId() {
            return storage.getAsInt(id, toCityIdField);
        }

        public void setToCityId(final int toCityId) {
            checkArgument(toCityId > 0);
            storage.set(id, toCityIdField, toCityId);
        }

        public int getGroupSize() {
            return storage.getAsInt(id, groupSizeField);
        }

        public void setGroupSize(final int groupSize) {
            checkArgument(groupSize >= 1);
            storage.set(id, groupSizeField, groupSize);
        }

        public void setPreferredCabinService(final CabinLayout.Service service) {
            checkNotNull(service);
            preferredCabinServiceBitField.setInt(id, service.ordinal());
        }

        public CabinLayout.Service getPreferredCabinService() {
            return CabinLayout.Service.values()[preferredCabinServiceBitField.getInt(id)];
        }

        public CabinLayout.Service getBookedCabinService() {
            return CabinLayout.Service.values()[bookedCabinServiceBitField.getInt(id)];
        }

        public void setBookedCabinService(CabinLayout.Service bookedCabinService) {
            checkNotNull(bookedCabinService);
            bookedCabinServiceBitField.setInt(id, bookedCabinService.ordinal());
        }

        public int getTransportFlight1Id() {
            return readTransportFlight1Id(id);
        }

        public void setTransportFlight1Id(final int transportFlight1Id) {
            final int old = readTransportFlight1Id(id);
            storage.set(id, transportFlight1IdField, transportFlight1Id);
            if (old != readTransportFlight2Id(id)) { // the flight stays in the index while the journey has it as the second one
                unindexTransportFlight(old, id);
            }
            indexTransportFlight(transportFlight1Id, id);
        }

        public int getTransportFlight2Id() {
            return readTransportFlight2Id(id);
        }

        public void setTransportFlight2Id(final int transportFlight2Id) {
            final int old = readTransportFlight2Id(id);
            storage.set(id, transportFlight2IdField, transportFlight2Id);
            if (old != readTransportFlight1Id(id)) {
                unindexTransportFlight(old, id);
            }
            indexTransportFlight(transportFlight2Id, id);
        }

        public int getLocationCityId() {
            return readLocationCityId(id);
        }

        public void setLocationCityId(int locationCityId) {
            storage.set(id, locationCityIdField, locationCityId);
        }

        /**
         * The last non-zero location city: locationCityId is zeroed while the journey is in the flight, so this is
         * the place to return to if the flight is lost. Zero when it is not known (old records).
         */
        /**
         * What the journey is going to pay for the trip it is on (the price of the first flight, price1): the price which the operator has accepted (BusyBirds sets it when
         * the pilot gets a quote). Zero when there is no price. The journey pays it when it arrives (JourneyControl) and the price goes back to zero.
         */
        public int getPrice1() {
            return storage.getAsInt(id, price1Field);
        }

        public void setPrice1(final int price) {
            checkArgument(price >= 0, "price can not be negative");
            checkArgument(price <= MAX_PRICE, "price does not fit into 3 bytes");
            storage.set(id, price1Field, price);
        }

        /**
         * The price of the second flight of a journey with a stopover (transportFlight2Id): the same as price1 is for the first one (2026-10-30). Zero when there is no
         * second flight or no price. Nobody sets it yet: the routes with a stopover are switched off (JourneyProcessor), the field is made for the day they come back.
         */
        public int getPrice2() {
            return storage.getAsInt(id, price2Field);
        }

        public void setPrice2(final int price) {
            checkArgument(price >= 0, "price can not be negative");
            checkArgument(price <= MAX_PRICE, "price does not fit into 3 bytes");
            storage.set(id, price2Field, price);
        }

        public int getLastKnownLocationCityId() {
            return readLastKnownLocationCityId(id);
        }

        public void setLastKnownLocationCityId(int lastKnownLocationCityId) {
            storage.set(id, lastKnownLocationCityIdField, lastKnownLocationCityId);
        }

        @Override
        public String toString() {
            return String.format("{ id: %s, status: %s }", id, getStatus());
        }
    }

    public Storage.Condition<Journey> byStatus(final Status status) {
        checkNotNull(status);

        return recordId -> readStatusCode(recordId) == status.code();
    }

    public Storage.Condition<Journey> byStatusIn(final Collection<Status> statuses) {
        checkNotNull(statuses);

        final int[] codes = statuses.stream().mapToInt(Status::code).toArray();
        return recordId -> {
            final int code = readStatusCode(recordId);
            for (final int c : codes) {
                if (c == code) {
                    return true;
                }
            }
            return false;
        };
    }

    public Storage.Condition<Journey> byLocationCityIdIn(final Collection<Integer> cityIds) {
        checkNotNull(cityIds);

        return recordId -> cityIds.contains(readLocationCityId(recordId));
    }

    private int readStatusCode(int recordId) {
        return statusBitField.getInt(recordId);
    }

    private int readTransportFlight1Id(int recordId) {
        return storage.getAsInt(recordId, transportFlight1IdField);
    }

    private int readTransportFlight2Id(int recordId) {
        return storage.getAsInt(recordId, transportFlight2IdField);
    }

    private int readLocationCityId(int recordId) {
        return storage.getAsInt(recordId, locationCityIdField);
    }

    private int readLastKnownLocationCityId(int recordId) {
        return storage.getAsInt(recordId, lastKnownLocationCityIdField);
    }

    public enum Status {
        // persons LookingForPersons(0),
        LookingForTickets(1),
        // location WaitingForFlight(2),
        // locationTransferToAirport(3),
        WaitingForCheckIn(4),
        WaitingForBoarding(5),
        OnBoard(6),
        WaitingForDeboarding(7),
        JustArrived(8),
        // location TransferToCity(...),
        ItinerariesDone(9),
        Finished(10),
        // persons CouldNotFindPersons(11),
        CouldNotFindTickets(12),
        TooLateToBoard(13),
        // terminal, the transport flight of the journey is not known anymore, the journey is cleaned up after a day
        Orphaned(14),
        SpendingTheirTime(15)
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
