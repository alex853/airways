package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.BitAccessField;
import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The accounts of the money, see docs/flight-contracts.md, section 6. An account has a balance and an owner: the kind of the owner and the id of the owner,
 * what the id means depends on the kind (see Kind). There is one account for an owner. The balance is changed only by the transfer of BankControl, so the
 * balances and the transactions never differ.
 */
public class BankAccounts {
    /**
     * Whose the account is. It is stored as the number of the kind, the numbers never change: add new kinds to the end. The meaning of the owner id of an
     * account depends on the kind.
     */
    public enum Kind {
        // A system account, the owner id is its code, see SYSTEM_BANK and SYSTEM_PASSENGERS.
        System,
        // An account of an operator, the owner id is the id of the operator (AircraftOperators).
        Operator,
        // An account of a person, the owner id is the id of the person (Persons).
        Person
    }

    // the codes of the system accounts (the owner id of an account of the kind System)
    public static final int SYSTEM_BANK = 1;
    public static final int SYSTEM_PASSENGERS = 2;

    private static final int MAX_OWNER_ID = 16_777_215;
    private static final int MAX_OPERATOR_ID = 65_535;

    // the balance is in whole units, a long (8 bytes)
    private final Storage<BankAccount> storage = Storage.<BankAccount>builder()
            .name("bank-accounts")
            .withInstantiator(BankAccount::new)
            .withIdOf(DataType.Unsigned24bit)
            // -------------------------------------------------- record deletion status    1 byte
            .withDataField(DataField.of(DataType.Signed64bit))   // balance                         8 bytes
            .withDataField(DataField.of(DataType.Unsigned8bit))  // kind, see Kind                  1 byte
            .withDataField(DataField.of(DataType.Unsigned24bit)) // ownerId, by the kind            3 bytes
            .withDataField(DataField.of(DataType.Unsigned8bit))  // flags: bit 0 can go negative   1 byte
            .withDataField(DataField.of(DataType.Signed32bit))   // createdTime                     4 bytes
            .withDataField(DataField.of(DataType.Signed32bit))   // updatedTime                     4 bytes
            // ----------------------------------------------------- sum                   22 bytes
            // -------------------------------------------- reserved 10 bytes to align total size to 32 bytes
            .withDataField(DataField.of(DataType.Signed32bit))   // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit))   // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // reserve                   2 bytes
            .build();

    private final DataField balanceField = storage.getDataField(0);
    private final DataField kindField = storage.getDataField(1);
    private final DataField ownerIdField = storage.getDataField(2);
    private final DataField flagsField = storage.getDataField(3);
    private final DataField createdTimeField = storage.getDataField(4);
    private final DataField updatedTimeField = storage.getDataField(5);

    private final BitAccessField flagsBits = BitAccessField.instance(storage, flagsField);
    private final BitAccessField.Section canGoNegativeBit = flagsBits.section(0, 1);

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Stream<BankAccount> all() {
        return storage.all();
    }

    public Optional<BankAccount> byId(final int id) {
        return storage.byId(id);
    }

    /**
     * The account of an owner, if it has one.
     */
    public Optional<BankAccount> byOwner(final Kind kind, final int ownerId) {
        checkArgument(kind != null, "the kind is mandatory");
        return storage.filter(recordId -> storage.getAsInt(recordId, kindField) == kind.ordinal()
                        && storage.getAsInt(recordId, ownerIdField) == ownerId)
                .findFirst();
    }

    /**
     * A new account with a zero balance. An owner has one account.
     *
     * @param kind          whose the account is
     * @param ownerId       the owner by the kind: the code of a system account (SYSTEM_BANK, SYSTEM_PASSENGERS), the id of an operator or the id of a person
     * @param canGoNegative whether the balance may be below zero (the bank, the passengers, the operators): then the sum of all the balances is always zero
     * @param time          the world time of the creation
     */
    public BankAccount create(final Kind kind, final int ownerId, final boolean canGoNegative, final int time) {
        checkArgument(kind != null, "the kind is mandatory");
        checkArgument(ownerId > 0 && ownerId <= MAX_OWNER_ID, "the owner id is from 1 to 3 bytes: %s", ownerId);
        checkArgument(kind != Kind.Operator || ownerId <= MAX_OPERATOR_ID, "the id of an operator is from 1 to 2 bytes: %s", ownerId);
        checkArgument(kind != Kind.System || ownerId == SYSTEM_BANK || ownerId == SYSTEM_PASSENGERS, "unknown system account %s", ownerId);
        checkArgument(byOwner(kind, ownerId).isEmpty(), "the owner has an account already: %s %s", kind, ownerId);

        final int id = storage.addRecord();
        storage.setLong(id, balanceField, 0);
        storage.set(id, kindField, kind.ordinal());
        storage.set(id, ownerIdField, ownerId);
        final BankAccount account = new BankAccount(id);
        account.setCanGoNegative(canGoNegative);
        storage.set(id, createdTimeField, time);
        storage.set(id, updatedTimeField, time);
        return account;
    }

    public class BankAccount {
        private final int id;

        private BankAccount(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public Kind getKind() {
            final int stored = storage.getAsInt(id, kindField);
            checkArgument(stored >= 0 && stored < Kind.values().length, "unknown account kind %s", stored);
            return Kind.values()[stored];
        }

        /**
         * The owner by the kind: the code of a system account, the id of an operator or the id of a person.
         */
        public int getOwnerId() {
            return storage.getAsInt(id, ownerIdField);
        }

        public long getBalance() {
            return storage.getAsLong(id, balanceField);
        }

        public boolean canGoNegative() {
            return canGoNegativeBit.getBoolean(id);
        }

        private void setCanGoNegative(final boolean value) {
            canGoNegativeBit.setBoolean(id, value);
        }

        public int getCreatedTime() {
            return storage.getAsInt(id, createdTimeField);
        }

        public int getUpdatedTime() {
            return storage.getAsInt(id, updatedTimeField);
        }

        /**
         * Changes the balance. It is for the transfer of BankControl only, nobody else changes a balance.
         */
        public void changeBalance(final long delta, final int time) {
            final long balance = getBalance();
            checkArgument(!canGoNegative() ? balance + delta >= 0 : true, "the account #%s has %s and cannot pay %s", id, balance, -delta);
            storage.setLong(id, balanceField, balance + delta);
            storage.set(id, updatedTimeField, time);
        }

        @Override
        public String toString() {
            return String.format("{ id: %s, %s %s, balance: %s }", id, getKind(), getOwnerId(), getBalance());
        }
    }
}
