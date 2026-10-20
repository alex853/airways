package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.world.computations.PilotPayParams;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The contracts of the pilots with the operators: a contract is for one flight mission, it says who flies it for whom and what the pilot gets, see
 * docs/flight-contracts.md. A contract is for a flight of a booked mission (a plan), the flights which are not paid (the free ferry flight) have none.
 * <p>
 * The contract has the base numbers and the parameters, not the final sums: the base flight pay (the rate of the type by the planned time of the
 * flight), the base income (what the journeys of the flight pay, for a flight without passengers it is zero) and the byte of the parameters of the pay
 * (the currency, the manual flight, the experience level, see PilotPayParams). The final flight pay and income share are calculated from them by
 * PilotPay, so a later event (the manual flight, the journeys of a flight which are known at blocks off) changes a number or a parameter and the result
 * changes with it. A contract lives as long as its flight mission and is deleted with it.
 */
public class PilotContracts {
    private static final int MAX_16BIT = 65535;
    // the base flight pay and the base income are kept in three bytes
    public static final int MAX_AMOUNT = 16_777_215;

    private final Storage<Contract> storage = Storage.<Contract>builder()
            .name("pilot-contracts")
            .withInstantiator(Contract::new)
            .withIdOf(DataType.Unsigned24bit)
            // -------------------------------------------------- record deletion status    1 byte
            .withDataField(DataField.of(DataType.Unsigned24bit)) // flightMissionId                3 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // operatorId                     2 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // personId                       3 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // baseFlightPay                  3 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // baseIncome                     3 bytes
            .withDataField(DataField.of(DataType.Unsigned8bit)) // the parameters of the pay, see PilotPayParams  1 byte
            // ----------------------------------------------------- sum                   16 bytes
            // -------------------------------------------- reserved 16 bytes to align total size to 32 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .build();

    private final DataField flightMissionIdField = storage.getDataField(0);
    private final DataField operatorIdField = storage.getDataField(1);
    private final DataField personIdField = storage.getDataField(2);
    private final DataField baseFlightPayField = storage.getDataField(3);
    private final DataField baseIncomeField = storage.getDataField(4);
    private final DataField payParamsField = storage.getDataField(5);

    // Flight mission id to id of its contract. A contract is for one flight mission, flightMissionId of a record never changes, the index is kept in
    // create() and deleteByFlightMissionId() and rebuilt after the loading.
    private final Map<Integer, Integer> contractIdByMissionId = new ConcurrentHashMap<>();

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
        contractIdByMissionId.clear();
        storage.all().forEach(contract -> contractIdByMissionId.put(contract.getFlightMissionId(), contract.getId()));
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Stream<Contract> all() {
        return storage.all();
    }

    public Optional<Contract> byId(final int id) {
        return storage.byId(id);
    }

    public Optional<Contract> byFlightMissionId(final int flightMissionId) {
        final Integer id = contractIdByMissionId.get(flightMissionId);
        return id != null ? storage.byId(id) : Optional.empty();
    }

    public Storage.Condition<Contract> byPersonId(final int personId) {
        checkArgument(personId > 0);
        return recordId -> storage.getAsInt(recordId, personIdField) == personId;
    }

    public Stream<Contract> filter(final Storage.Condition<Contract> condition) {
        return storage.filter(condition);
    }

    /**
     * A contract for a flight mission.
     *
     * @param baseFlightPay the pay for the flight time before the parameters work, 0 to 16 777 215
     * @param baseIncome    what the journeys of the flight pay, 0 to 16 777 215 (zero for a flight without passengers or while the journeys are not known)
     * @param params        the parameters of the pay
     */
    public Contract create(final int flightMissionId,
                           final int operatorId,
                           final int personId,
                           final int baseFlightPay,
                           final int baseIncome,
                           final PilotPayParams params) {
        checkArgument(flightMissionId > 0, "flightMissionId is mandatory");
        checkArgument(operatorId >= 1 && operatorId <= MAX_16BIT, "operatorId is out of 16 bits");
        checkArgument(personId > 0, "personId is mandatory");
        checkAmount(baseFlightPay, "the flight pay");
        checkAmount(baseIncome, "the income");
        checkArgument(params != null, "the parameters are mandatory");
        checkArgument(!contractIdByMissionId.containsKey(flightMissionId), "the flight mission has a contract already");

        final int id = storage.addRecord();
        storage.set(id, flightMissionIdField, flightMissionId);
        storage.set(id, operatorIdField, operatorId);
        storage.set(id, personIdField, personId);
        storage.set(id, baseFlightPayField, baseFlightPay);
        storage.set(id, baseIncomeField, baseIncome);
        storage.set(id, payParamsField, params.toByte());
        contractIdByMissionId.put(flightMissionId, id);
        return new Contract(id);
    }

    private static void checkAmount(final int amount, final String what) {
        checkArgument(amount >= 0 && amount <= MAX_AMOUNT, "%s is from 0 to 3 bytes: %s", what, amount);
    }

    /**
     * The contract is deleted with its flight mission (or when the flight is cancelled).
     *
     * @return false if the flight mission had no contract
     */
    public boolean deleteByFlightMissionId(final int flightMissionId) {
        final Integer id = contractIdByMissionId.remove(flightMissionId);
        if (id == null) {
            return false;
        }
        storage.deleteRecord(id);
        return true;
    }

    public class Contract {
        private final int id;

        private Contract(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public int getFlightMissionId() {
            return storage.getAsInt(id, flightMissionIdField);
        }

        public int getOperatorId() {
            return storage.getAsInt(id, operatorIdField);
        }

        public int getPersonId() {
            return storage.getAsInt(id, personIdField);
        }

        public int getBaseFlightPay() {
            return storage.getAsInt(id, baseFlightPayField);
        }

        public void setBaseFlightPay(final int baseFlightPay) {
            checkAmount(baseFlightPay, "the flight pay");
            storage.set(id, baseFlightPayField, baseFlightPay);
        }

        /**
         * What the journeys of the flight pay. It is known at the booking for a BusyBirds flight; for a ShadowJet flight it is set when the journeys on
         * board are known (blocks off).
         */
        public int getBaseIncome() {
            return storage.getAsInt(id, baseIncomeField);
        }

        public void setBaseIncome(final int baseIncome) {
            checkAmount(baseIncome, "the income");
            storage.set(id, baseIncomeField, baseIncome);
        }

        /**
         * The parameters of the pay, a copy: change it and give it back with setPayParams.
         */
        public PilotPayParams getPayParams() {
            return PilotPayParams.of(storage.getAsInt(id, payParamsField));
        }

        public void setPayParams(final PilotPayParams params) {
            checkArgument(params != null, "the parameters are mandatory");
            storage.set(id, payParamsField, params.toByte());
        }

        @Override
        public String toString() {
            return String.format("{ id: %s, f/m: %s }", id, getFlightMissionId());
        }
    }
}
