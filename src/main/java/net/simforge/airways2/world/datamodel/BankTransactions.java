package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The transactions: who paid whom, how much and what for (the type), see docs/flight-contracts.md, section 6. A transaction does not refer to a flight
 * mission or a journey, they are deleted with time, and it has no text. A transaction is written only by the transfer of BankControl, together with
 * the change of the two balances.
 */
public class BankTransactions {
    /**
     * What the money is for. It is stored as the number of the type (BalanceCheckpoint is 0), the numbers never change: add new types to the end.
     */
    public enum Type {
        // Not a payment: the balance of an account which was verified at the moment of the transaction. The account is fromAccountId (toAccountId is not used,
        // it is zero), amount is the verified balance (it may be zero or negative), no money moves. It is a value of the balance, not a change of it: the
        // balance of an account is checked by the last checkpoint and the transactions after it, see BankControl.validate.
        BalanceCheckpoint,
        // the journey pays for its transportation to the operator of the flight
        JourneyPayment,
        // the operator pays the pilot for the flown hours
        PilotFlightPay,
        // the operator pays the pilot his share of the income of the flight
        PilotIncomeShare,
        // the pilot pays for a travel to the bank (PersonControl.travel)
        Travel
    }

    private final Storage<BankTransaction> storage = Storage.<BankTransaction>builder()
            .name("bank-transactions")
            .withInstantiator(BankTransaction::new)
            .withIdOf(DataType.Unsigned24bit)
            // -------------------------------------------------- record deletion status    1 byte
            .withDataField(DataField.of(DataType.Signed32bit))   // time                            4 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // fromAccountId                   3 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // toAccountId                     3 bytes
            .withDataField(DataField.of(DataType.Signed40bit))   // amount                          5 bytes
            .withDataField(DataField.of(DataType.Unsigned8bit))  // type, see Type (0 is a checkpoint)  1 byte
            // ----------------------------------------------------- sum                   17 bytes
            // -------------------------------------------- reserved 3 bytes to align total size to 20 bytes (there are going to be many transactions)
            .withDataField(DataField.of(DataType.Unsigned24bit)) // reserve                   3 bytes
            .build();

    private final DataField timeField = storage.getDataField(0);
    private final DataField fromAccountIdField = storage.getDataField(1);
    private final DataField toAccountIdField = storage.getDataField(2);
    private final DataField amountField = storage.getDataField(3);
    private final DataField typeField = storage.getDataField(4);

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Stream<BankTransaction> all() {
        return storage.all();
    }

    public Optional<BankTransaction> byId(final int id) {
        return storage.byId(id);
    }

    /**
     * The transactions of an account, both the ones it has paid and the ones it has received (a statement).
     */
    public Storage.Condition<BankTransaction> byAccountId(final int accountId) {
        checkArgument(accountId > 0);
        return recordId -> storage.getAsInt(recordId, fromAccountIdField) == accountId
                || storage.getAsInt(recordId, toAccountIdField) == accountId;
    }

    public Stream<BankTransaction> filter(final Storage.Condition<BankTransaction> condition) {
        return storage.filter(condition);
    }

    /**
     * Writes a transaction. It is for BankControl only, nobody else writes them. A payment has an amount more than zero and two different accounts, a
     * balance checkpoint has the verified account as fromAccountId, zero as toAccountId and the verified balance as the amount.
     */
    public BankTransaction create(final int time, final int fromAccountId, final int toAccountId, final long amount, final Type type) {
        checkArgument(type != null, "the type is mandatory");
        checkArgument(fromAccountId > 0, "the account is mandatory");
        if (type == Type.BalanceCheckpoint) {
            checkArgument(toAccountId == 0, "a checkpoint has no account to");
        } else {
            checkArgument(toAccountId > 0, "the accounts are mandatory");
            checkArgument(amount > 0, "the amount must be more than zero: %s", amount);
            checkArgument(fromAccountId != toAccountId, "the accounts must be different: %s", fromAccountId);
        }

        final int id = storage.addRecord();
        storage.set(id, timeField, time);
        storage.set(id, fromAccountIdField, fromAccountId);
        storage.set(id, toAccountIdField, toAccountId);
        storage.setLong(id, amountField, amount);
        storage.set(id, typeField, type.ordinal());
        return new BankTransaction(id);
    }

    public class BankTransaction {
        private final int id;

        private BankTransaction(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public int getTime() {
            return storage.getAsInt(id, timeField);
        }

        public int getFromAccountId() {
            return storage.getAsInt(id, fromAccountIdField);
        }

        public int getToAccountId() {
            return storage.getAsInt(id, toAccountIdField);
        }

        public long getAmount() {
            return storage.getAsLong(id, amountField);
        }

        public Type getType() {
            final int stored = storage.getAsInt(id, typeField);
            checkArgument(stored >= 0 && stored < Type.values().length, "unknown transaction type %s", stored);
            return Type.values()[stored];
        }

        @Override
        public String toString() {
            return String.format("{ id: %s, %s -> %s, %s, %s }", id, getFromAccountId(), getToAccountId(), getAmount(), getType());
        }
    }
}
