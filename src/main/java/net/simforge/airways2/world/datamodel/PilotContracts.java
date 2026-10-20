package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.BitAccessField;
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
 * The contracts of the pilots with the operators: a contract is for one flight mission, it says who flies it for whom and what the pilot gets, see
 * docs/flight-contracts.md. A contract is for a flight of a booked mission (a plan), the flights which are not paid (the free ferry flight) have none.
 * <p>
 * The contract has the base numbers and the parameters, not the final sums: the final flight pay and income share are calculated from them by the
 * formula (the penalties and the minimum are in the code), so a later event (the manual flight, the journeys of the flight) changes a flag or a number
 * and the result changes with it. A contract lives as long as its flight mission and is deleted with it.
 */
public class PilotContracts {
    private static final int MAX_16BIT = 65535;
    // the flight pay is kept in three bytes
    public static final int MAX_FLIGHT_PAY = 16_777_215;
    // the base share of the income is a percent with one digit after the point (Tenths8bit): 0.0 to 25.5
    public static final float MAX_SHARE_PERCENT = 25.5f;

    private final Storage<Contract> storage = Storage.<Contract>builder()
            .name("pilot-contracts")
            .withInstantiator(Contract::new)
            .withIdOf(DataType.Unsigned24bit)
            // -------------------------------------------------- record deletion status    1 byte
            .withDataField(DataField.of(DataType.Unsigned24bit)) // flightMissionId                3 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // operatorId                     2 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // personId                       3 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // baseFlightPay                  3 bytes
            .withDataField(DataField.of(DataType.Tenths8bit)) // baseIncomeSharePercent, 0.0-25.5 step 0.1  1 byte
            .withDataField(DataField.of(DataType.Unsigned8bit)) // penalties, see Penalty          1 byte
            // ----------------------------------------------------- sum                   14 bytes
            // -------------------------------------------- reserved 18 bytes to align total size to 32 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // reserve                 2 bytes
            .build();

    private final DataField flightMissionIdField = storage.getDataField(0);
    private final DataField operatorIdField = storage.getDataField(1);
    private final DataField personIdField = storage.getDataField(2);
    private final DataField baseFlightPayField = storage.getDataField(3);
    private final DataField baseIncomeSharePercentField = storage.getDataField(4);
    private final DataField penaltiesRawField = storage.getDataField(5);
    private final BitAccessField penaltiesBits = BitAccessField.instance(storage, penaltiesRawField);
    // a one-bit section for every penalty, by the ordinal of the penalty
    private final BitAccessField.Section[] penaltyBitFields = java.util.Arrays.stream(Penalty.values())
            .map(p -> penaltiesBits.section(p.bit(), 1))
            .toArray(BitAccessField.Section[]::new);

    // Flight mission id to id of its contract. A contract is for one flight mission, flightMissionId of a record never changes, the index is kept in
    // create() and deleteByFlightMissionId() and rebuilt after the loading.
    private final Map<Integer, Integer> contractIdByMissionId = new ConcurrentHashMap<>();

    /**
     * What reduces the pay of a flight. The values are the bit numbers in the byte of the penalties.
     */
    public enum Penalty {
        NoTypeCurrency(0),
        NoNightCurrency(1),
        NoCarryPassengers(2),
        Manual(3);

        private final int bit;

        Penalty(final int bit) {
            this.bit = bit;
        }

        public int bit() {
            return bit;
        }
    }

    private static void checkSharePercent(final float percent) {
        checkArgument(percent >= 0.0f && percent <= MAX_SHARE_PERCENT, "the base share is from 0.0 to 25.5 percents");
    }

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
     * A contract for a flight mission, without penalties.
     *
     * @param baseIncomeSharePercent the base share of the income in percents, 0.0 to 25.5 with one digit after the point (0.5 is half a percent)
     */
    public Contract create(final int flightMissionId,
                           final int operatorId,
                           final int personId,
                           final int baseFlightPay,
                           final float baseIncomeSharePercent) {
        checkArgument(flightMissionId > 0, "flightMissionId is mandatory");
        checkArgument(operatorId >= 1 && operatorId <= MAX_16BIT, "operatorId is out of 16 bits");
        checkArgument(personId > 0, "personId is mandatory");
        checkArgument(baseFlightPay >= 0 && baseFlightPay <= MAX_FLIGHT_PAY, "the flight pay is from 0 to 3 bytes");
        checkSharePercent(baseIncomeSharePercent);
        checkArgument(!contractIdByMissionId.containsKey(flightMissionId), "the flight mission has a contract already");

        final int id = storage.addRecord();
        storage.set(id, flightMissionIdField, flightMissionId);
        storage.set(id, operatorIdField, operatorId);
        storage.set(id, personIdField, personId);
        storage.set(id, baseFlightPayField, baseFlightPay);
        storage.set(id, baseIncomeSharePercentField, baseIncomeSharePercent);
        storage.set(id, penaltiesRawField, 0);
        contractIdByMissionId.put(flightMissionId, id);
        return new Contract(id);
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
            checkArgument(baseFlightPay >= 0 && baseFlightPay <= MAX_FLIGHT_PAY, "the flight pay is from 0 to 3 bytes");
            storage.set(id, baseFlightPayField, baseFlightPay);
        }

        /**
         * The base share of the income in percents, with one digit after the point: 0.5 is half a percent.
         */
        public float getBaseIncomeSharePercent() {
            return storage.getAsFloat(id, baseIncomeSharePercentField);
        }

        public void setBaseIncomeSharePercent(final float percent) {
            checkSharePercent(percent);
            storage.set(id, baseIncomeSharePercentField, percent);
        }

        public boolean hasPenalty(final Penalty penalty) {
            return penaltyBitFields[penalty.ordinal()].getBoolean(id);
        }

        public void setPenalty(final Penalty penalty, final boolean on) {
            penaltyBitFields[penalty.ordinal()].setBoolean(id, on);
        }

        @Override
        public String toString() {
            return String.format("{ id: %s, f/m: %s }", id, getFlightMissionId());
        }
    }
}
