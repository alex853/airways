package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The extra data of a transport flight, the data which only a few transport flights need. It is a storage of its own and not a part of the record of the transport
 * flight (TransportFlights): the record of the transport flight has no room for it (a few free bits only, and a new field there would change the size of the record,
 * the saved file of the storage would not load without a conversion), and most of the transport flights do not need it, so the records are made only for the ones
 * which do (the transport flights of the missions of the pilots, ShadowJet first of all), not for all of them.
 * <p>
 * A record is for one transport flight (transportFlightId, it never changes), it is created when the flight needs the data and it is deleted with the transport flight
 * (FlightCleanup, the admin deletions of transport flights). The records are found by the transport flight, the index is kept in create() and
 * deleteByTransportFlightId() and rebuilt after the loading, as in PilotContracts.
 * <p>
 * The record is 32 bytes: 1 byte of the deletion status, 3 bytes of the transport flight, 3 bytes of the income and 25 bytes of the reserve.
 * <p>
 * THE INCOME of a transport flight: the sum of the price of the journeys which are booked to the flight (what the passengers pay), in the money of the game, 0 to
 * 16 777 215. It is kept for the flights of the pilots only (the PC flights, FlightMissions.CharacterMode.PC); a flight of an NPC has no record and all the operations
 * with the income skip it (TransportFlightControl). A record is created when the income of a flight becomes more than zero, a flight without income has no record and
 * its income is zero (my-flights tells 0). How it lives (TransportFlightControl):
 * - every booking of a journey to the flight adds the price of the journey (JourneyControl.bookDirectFlightJourneyNoChecks, the booking of BusyBirds);
 * - when the boarding is over (PaxManager.Boarding.finish) the income is calculated again, from the journeys which are on board: the sum of their prices overwrites it,
 * so the income is what really boarded;
 * - my-flights tells it as transportFlight.income, the roster page shows it.
 * <p>
 * THE PAY AND THE PRICES (done 2026-10-30): the income share of the pilot is taken from here (BusyBirdsMissionControl.payPilot, the manual pay of the dashboard), the
 * contract has no income any more (its three bytes are a reserve). Both operators fill the price of the journey, it is the same scheme with no special case: BusyBirds
 * puts the price of its business charter (the quote) in journey.price, ShadowJet sets journey.price by the distance, the passengers and the class when the journey is booked
 * to a flight of a pilot (ShadowJetPricing, JourneyControl.bookDirectFlightJourneyNoChecks); a ShadowJet journey which is too late to board loses the price.
 * <p>
 * todo ak1 WHAT IS LEFT FOR THE INCOME:
 * 1. THE NUMBERS OF SHADOWJET PRICING (ShadowJetPricing) ARE A DRAFT: the fixed part, the price of a mile, the factors of the classes. Agree them. The journeys of ShadowJet pay
 * the price to the operator on arrival now for the flights of the pilots (JourneyControl.payForTransportation, from the account of the passengers), check the balance of the
 * account of ShadowJet after the first real flights.
 * 2. THE PAY OF A SHADOWJET PILOT is done (2026-10-30): the contract is made at the booking (ShadowJetBooking), the flight is paid at the finish by the rates of its operator
 * (OperatorPay, BusyBirdsMissionControl.payPilot), the income is taken from here. What is left is to look at the real payments after the first flights.
 * 3. THE STOPOVER JOURNEYS (two transport flights, JourneyControl.bookStopoverFlightsJourneyNoChecks): the price of the journey is for the whole trip, how it is split
 * between the two flights is not decided, so the stopover booking adds nothing to either now. Decide it with the price of ShadowJet (hub test journeys have price 0 anyway).
 * 4. THE EXPECTED INCOME of the forecast of the sales team (ShadowJetController.ExpectedLoadDto.income, null now): the seats of the flight * the load percent * the price of
 * a seat; it can use ShadowJetPricing now (item 1: the numbers are a draft).
 * 5. THE RELEASE of a journey from the flight: a ShadowJet journey which is too late to board or is reset (JourneyControl.tooLateToBoard, resetJourneyForcefully) gets its price
 * cleared (releaseShadowJetPrice) and the income of the flight is NOT touched there: it is calculated again from the journeys on board at the end of the boarding, a
 * journey which is too late is not among them, and taking its price off after that would count it twice. The second flight of a stopover journey has no income (item 3).
 * If a journey is going to leave a flight before the end of the boarding for good (the booking is cancelled), it must call TransportFlightControl.subtractIncome.
 * 6. THE RESERVE, 25 bytes, is for what comes next to such flights: the booking time (for "the earlier the booking, the more passengers", ShadowJetSalesForecast), the target
 * of the sales team (20-30% at once, 40-50% in the first 4 hours, docs/clients-and-flight-modes.md), the flag of the closed booking (closedBooking, docs/status-and-backlog.md).
 * Decide the layout when the second tenant comes and write it in the comment of the storage below, as in PilotContracts.
 */
public class TransportFlightExtras {
    // the income is kept in three bytes
    public static final int MAX_INCOME = (int) DataType.Unsigned24bit.maxValue();

    private final Storage<Extra> storage = Storage.<Extra>builder()
            .name("transport-flight-extra")
            .withInstantiator(Extra::new)
            .withIdOf(DataType.Unsigned24bit)
            // -------------------------------------------------- record deletion status    1 byte
            .withDataField(DataField.of(DataType.Unsigned24bit)) // transportFlightId              3 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // income                         3 bytes
            // ----------------------------------------------------- sum                    6 bytes
            // -------------------------------------------- reserved 25 bytes to make the record 32 bytes, see todo ak1 above
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Unsigned8bit)) // reserve                  1 byte
            .build();

    private final DataField transportFlightIdField = storage.getDataField(0);
    private final DataField incomeField = storage.getDataField(1);

    // Transport flight id to id of its extra record. The record is for one transport flight, transportFlightId of a record never changes, the index is kept in
    // create() and deleteByTransportFlightId() and rebuilt after the loading.
    private final Map<Integer, Integer> extraIdByTransportFlightId = new ConcurrentHashMap<>();

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
        extraIdByTransportFlightId.clear();
        storage.all().forEach(extra -> extraIdByTransportFlightId.put(extra.getTransportFlightId(), extra.getId()));
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Stream<Extra> all() {
        return storage.all();
    }

    public Optional<Extra> byId(final int id) {
        return storage.byId(id);
    }

    public Optional<Extra> byTransportFlightId(final int transportFlightId) {
        final Integer id = extraIdByTransportFlightId.get(transportFlightId);
        return id != null ? storage.byId(id) : Optional.empty();
    }

    /**
     * The extra record of a transport flight, a new one if there is none yet. The flights which do not need the extra data have no record.
     */
    public Extra getOrCreate(final int transportFlightId) {
        final Optional<Extra> existing = byTransportFlightId(transportFlightId);
        return existing.orElseGet(() -> create(transportFlightId));
    }

    /**
     * An extra record for a transport flight, it is an error if the flight has one already.
     */
    public Extra create(final int transportFlightId) {
        checkArgument(transportFlightId > 0, "transportFlightId is mandatory");
        checkArgument(!extraIdByTransportFlightId.containsKey(transportFlightId), "the transport flight has an extra record already");

        final int id = storage.addRecord();
        storage.set(id, transportFlightIdField, transportFlightId);
        extraIdByTransportFlightId.put(transportFlightId, id);
        return new Extra(id);
    }

    /**
     * The record is deleted with its transport flight.
     *
     * @return false if the transport flight had no extra record
     */
    public boolean deleteByTransportFlightId(final int transportFlightId) {
        final Integer id = extraIdByTransportFlightId.remove(transportFlightId);
        if (id == null) {
            return false;
        }
        storage.deleteRecord(id);
        return true;
    }

    public class Extra {
        private final int id;

        private Extra(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public int getTransportFlightId() {
            return storage.getAsInt(id, transportFlightIdField);
        }

        /**
         * The income of the transport flight: the sum of the price of its journeys, 0 to 16 777 215.
         */
        public int getIncome() {
            return storage.getAsInt(id, incomeField);
        }

        public void setIncome(final int income) {
            checkArgument(income >= 0 && income <= MAX_INCOME, "the income is from 0 to 3 bytes: %s", income);
            storage.set(id, incomeField, income);
        }

        @Override
        public boolean equals(final Object o) {
            return o instanceof Extra other && other.id == id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
    }
}
