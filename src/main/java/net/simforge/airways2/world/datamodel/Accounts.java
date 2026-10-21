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
 * The accounts of the money: a balance and nothing more, see docs/flight-contracts.md, section 6. Who owns an account is told from the owner's side
 * (a person has its account id, an operator has its one), not from the account. The balance is changed only by the transfer of AccountControl, so the
 * balances and the transactions never differ.
 */
public class Accounts {
    // the balance is in whole units, a long (8 bytes)
    private final Storage<Account> storage = Storage.<Account>builder()
            .name("accounts")
            .withInstantiator(Account::new)
            .withIdOf(DataType.Unsigned24bit)
            // -------------------------------------------------- record deletion status    1 byte
            .withDataField(DataField.of(DataType.Signed64bit))   // balance                         8 bytes
            .withDataField(DataField.of(DataType.Unsigned8bit))  // flags: bit 0 can go negative   1 byte
            .withDataField(DataField.of(DataType.Signed32bit))   // createdTime                     4 bytes
            .withDataField(DataField.of(DataType.Signed32bit))   // updatedTime                     4 bytes
            // ----------------------------------------------------- sum                   18 bytes
            // -------------------------------------------- reserved 14 bytes to align total size to 32 bytes
            .withDataField(DataField.of(DataType.Signed32bit))   // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit))   // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Signed32bit))   // reserve                   4 bytes
            .withDataField(DataField.of(DataType.Unsigned16bit)) // reserve                   2 bytes
            .build();

    private final DataField balanceField = storage.getDataField(0);
    private final DataField flagsField = storage.getDataField(1);
    private final DataField createdTimeField = storage.getDataField(2);
    private final DataField updatedTimeField = storage.getDataField(3);

    private final BitAccessField flagsBits = BitAccessField.instance(storage, flagsField);
    private final BitAccessField.Section canGoNegativeBit = flagsBits.section(0, 1);

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Stream<Account> all() {
        return storage.all();
    }

    public Optional<Account> byId(final int id) {
        return storage.byId(id);
    }

    /**
     * A new account with a zero balance.
     *
     * @param canGoNegative whether the balance may be below zero (the bank, the passengers, the operators): then the sum of all the balances is always zero
     * @param time          the world time of the creation
     */
    public Account create(final boolean canGoNegative, final int time) {
        final int id = storage.addRecord();
        storage.setLong(id, balanceField, 0);
        final Account account = new Account(id);
        account.setCanGoNegative(canGoNegative);
        storage.set(id, createdTimeField, time);
        storage.set(id, updatedTimeField, time);
        return account;
    }

    public class Account {
        private final int id;

        private Account(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        /**
         * The number of the account for showing, it is not stored.
         */
        public int getNumber() {
            return 100000 + id;
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
         * Changes the balance. It is for the transfer of AccountControl only, nobody else changes a balance.
         */
        public void changeBalance(final long delta, final int time) {
            final long balance = getBalance();
            checkArgument(!canGoNegative() ? balance + delta >= 0 : true, "the account #%s has %s and cannot pay %s", id, balance, -delta);
            storage.setLong(id, balanceField, balance + delta);
            storage.set(id, updatedTimeField, time);
        }

        @Override
        public String toString() {
            return String.format("{ id: %s, balance: %s }", id, getBalance());
        }
    }
}
