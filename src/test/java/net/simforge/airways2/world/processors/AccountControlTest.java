package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Accounts;
import net.simforge.airways2.world.datamodel.Transactions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The accounts, the transactions and the transfer, the only place where the money moves.
 */
class AccountControlTest {
    private World world;
    private Accounts.Account passengers;
    private Accounts.Account operator;
    private Accounts.Account pilot;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        // the passengers and the operator may go negative, the pilot may not
        passengers = world.accounts().create(true, world.getWorldTime());
        operator = world.accounts().create(true, world.getWorldTime());
        pilot = world.accounts().create(false, world.getWorldTime());
    }

    private long sumOfBalances() {
        return world.accounts().all().mapToLong(Accounts.Account::getBalance).sum();
    }

    @Test
    public void test__a_new_account_is_empty_and_has_a_number() {
        assertEquals(0, pilot.getBalance());
        assertFalse(pilot.canGoNegative());
        assertTrue(operator.canGoNegative());
        assertEquals(100000 + pilot.getId(), pilot.getNumber());
        assertEquals(world.getWorldTime(), pilot.getCreatedTime());
        assertEquals(world.getWorldTime(), pilot.getUpdatedTime());
    }

    @Test
    public void test__transfer_moves_the_money_and_writes_the_transaction() {
        final Transactions.Transaction t = world.accountControl().transfer(passengers.getId(), operator.getId(), 10_484, Transactions.Type.JourneyPayment);

        assertEquals(-10_484, passengers.getBalance());
        assertEquals(10_484, operator.getBalance());
        assertEquals(passengers.getId(), t.getFromAccountId());
        assertEquals(operator.getId(), t.getToAccountId());
        assertEquals(10_484, t.getAmount());
        assertEquals(Transactions.Type.JourneyPayment, t.getType());
        assertEquals(world.getWorldTime(), t.getTime());
        assertEquals(1, world.transactions().all().count());
    }

    @Test
    public void test__the_updated_time_follows_the_balance() {
        world.setWorldTime(world.getWorldTime() + 100);

        world.accountControl().transfer(passengers.getId(), operator.getId(), 5, Transactions.Type.JourneyPayment);

        assertEquals(world.getWorldTime(), passengers.getUpdatedTime());
        assertEquals(world.getWorldTime(), operator.getUpdatedTime());
        // an account which was not touched keeps its time, the creation time stays
        assertEquals(world.getWorldTime() - 100, pilot.getUpdatedTime());
        assertEquals(world.getWorldTime() - 100, operator.getCreatedTime());
    }

    @Test
    public void test__the_sum_of_all_the_balances_is_always_zero() {
        world.accountControl().transfer(passengers.getId(), operator.getId(), 10_484, Transactions.Type.JourneyPayment);
        assertEquals(0, sumOfBalances());

        world.accountControl().transfer(operator.getId(), pilot.getId(), 296, Transactions.Type.PilotFlightPay);
        world.accountControl().transfer(operator.getId(), pilot.getId(), 39, Transactions.Type.PilotIncomeShare);

        assertEquals(0, sumOfBalances());
        assertEquals(335, pilot.getBalance());
        assertEquals(10_484 - 335, operator.getBalance());
        assertEquals(3, world.transactions().all().count());
    }

    @Test
    public void test__an_account_which_cannot_go_negative_cannot_pay_more_than_it_has() {
        world.accountControl().transfer(operator.getId(), pilot.getId(), 100, Transactions.Type.PilotFlightPay);

        assertThrows(IllegalArgumentException.class,
                () -> world.accountControl().transfer(pilot.getId(), operator.getId(), 101, Transactions.Type.PilotFlightPay));

        // nothing has changed
        assertEquals(100, pilot.getBalance());
        assertEquals(-100, operator.getBalance());
        assertEquals(1, world.transactions().all().count());
        // all the money can be paid
        world.accountControl().transfer(pilot.getId(), operator.getId(), 100, Transactions.Type.PilotFlightPay);
        assertEquals(0, pilot.getBalance());
    }

    @Test
    public void test__the_transfer_is_checked() {
        final AccountControl control = world.accountControl();

        assertThrows(IllegalArgumentException.class, () -> control.transfer(passengers.getId(), operator.getId(), 0, Transactions.Type.JourneyPayment));
        assertThrows(IllegalArgumentException.class, () -> control.transfer(passengers.getId(), operator.getId(), -5, Transactions.Type.JourneyPayment));
        assertThrows(IllegalArgumentException.class, () -> control.transfer(passengers.getId(), passengers.getId(), 5, Transactions.Type.JourneyPayment));
        assertThrows(IllegalArgumentException.class, () -> control.transfer(passengers.getId(), 999, 5, Transactions.Type.JourneyPayment));
        assertThrows(IllegalArgumentException.class, () -> control.transfer(999, operator.getId(), 5, Transactions.Type.JourneyPayment));
        assertThrows(IllegalArgumentException.class, () -> control.transfer(passengers.getId(), operator.getId(), 5, null));

        assertEquals(0, sumOfBalances());
        assertEquals(0, passengers.getBalance());
        assertEquals(0, world.transactions().all().count());
    }

    @Test
    public void test__a_big_amount_does_not_overflow() {
        final long big = 5_000_000_000L;

        world.accountControl().transfer(passengers.getId(), operator.getId(), big, Transactions.Type.JourneyPayment);

        assertEquals(big, operator.getBalance());
        assertEquals(-big, passengers.getBalance());
        assertEquals(big, world.transactions().all().findFirst().orElseThrow().getAmount());
    }

    @Test
    public void test__the_statement_of_an_account_has_what_it_paid_and_what_it_received() {
        final Accounts.Account other = world.accounts().create(true, world.getWorldTime());
        world.accountControl().transfer(passengers.getId(), operator.getId(), 100, Transactions.Type.JourneyPayment);
        world.accountControl().transfer(operator.getId(), pilot.getId(), 30, Transactions.Type.PilotFlightPay);
        world.accountControl().transfer(passengers.getId(), other.getId(), 7, Transactions.Type.JourneyPayment);

        final List<Transactions.Type> ofOperator = world.transactions()
                .filter(world.transactions().byAccountId(operator.getId()))
                .map(Transactions.Transaction::getType)
                .toList();

        assertEquals(List.of(Transactions.Type.JourneyPayment, Transactions.Type.PilotFlightPay), ofOperator);
        assertEquals(1, world.transactions().filter(world.transactions().byAccountId(pilot.getId())).count());
    }

    @Test
    public void test__the_world_keeps_the_accounts_and_the_transactions(@TempDir final Path dir) throws IOException {
        world.accountControl().transfer(passengers.getId(), operator.getId(), 10_484, Transactions.Type.JourneyPayment);

        world.accounts().save(dir);
        world.transactions().save(dir);
        assertTrue(Files.size(dir.resolve("accounts")) > 0);
        assertTrue(Files.size(dir.resolve("transactions")) > 0);

        final World loaded = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        loaded.accounts().loadIfExists(dir);
        loaded.transactions().loadIfExists(dir);

        assertEquals(-10_484, loaded.accounts().byId(passengers.getId()).orElseThrow().getBalance());
        assertTrue(loaded.accounts().byId(operator.getId()).orElseThrow().canGoNegative());
        assertFalse(loaded.accounts().byId(pilot.getId()).orElseThrow().canGoNegative());
        assertEquals(Transactions.Type.JourneyPayment, loaded.transactions().all().findFirst().orElseThrow().getType());
        assertEquals(10_484, loaded.transactions().all().findFirst().orElseThrow().getAmount());
    }

    @Test
    public void test__a_checkpoint_fixes_the_balance_and_moves_nothing() {
        world.accountControl().transfer(passengers.getId(), operator.getId(), 1_000, Transactions.Type.JourneyPayment);

        final Transactions.Transaction checkpoint = world.accountControl().fixBalance(passengers.getId());

        assertEquals(Transactions.Type.BalanceCheckpoint, checkpoint.getType());
        assertEquals(passengers.getId(), checkpoint.getFromAccountId());
        assertEquals(passengers.getId(), checkpoint.getToAccountId());
        // the balance may be negative
        assertEquals(-1_000, checkpoint.getAmount());
        assertEquals(-1_000, passengers.getBalance());
        assertEquals(1_000, operator.getBalance());
        assertEquals(0, sumOfBalances());
    }

    @Test
    public void test__the_balance_is_calculated_from_the_last_checkpoint_and_what_came_after() {
        world.accountControl().transfer(passengers.getId(), operator.getId(), 1_000, Transactions.Type.JourneyPayment);
        world.accountControl().transfer(operator.getId(), pilot.getId(), 300, Transactions.Type.PilotFlightPay);
        world.accountControl().fixBalance(operator.getId());
        world.accountControl().transfer(operator.getId(), pilot.getId(), 40, Transactions.Type.PilotIncomeShare);
        world.accountControl().transfer(passengers.getId(), operator.getId(), 500, Transactions.Type.JourneyPayment);

        // 700 at the checkpoint, -40, +500
        assertEquals(1_160, world.accountControl().calculateBalance(operator.getId()));
        assertEquals(1_160, operator.getBalance());
        assertTrue(world.accountControl().validate(operator.getId()));
        assertTrue(world.accountControl().validate(passengers.getId()));
        assertTrue(world.accountControl().validate(pilot.getId()));
    }

    @Test
    public void test__without_a_checkpoint_the_balance_is_calculated_from_zero() {
        world.accountControl().transfer(passengers.getId(), operator.getId(), 1_000, Transactions.Type.JourneyPayment);
        world.accountControl().transfer(operator.getId(), pilot.getId(), 335, Transactions.Type.PilotFlightPay);

        assertEquals(665, world.accountControl().calculateBalance(operator.getId()));
        assertEquals(0, world.accountControl().calculateBalance(world.accounts().create(true, world.getWorldTime()).getId()));
    }

    @Test
    public void test__a_balance_which_does_not_match_the_transactions_is_found() {
        world.accountControl().transfer(passengers.getId(), operator.getId(), 1_000, Transactions.Type.JourneyPayment);
        world.accountControl().fixBalance(operator.getId());
        assertTrue(world.accountControl().validate(operator.getId()));

        // somebody has changed the balance bypassing the transfer
        operator.changeBalance(5, world.getWorldTime());

        assertFalse(world.accountControl().validate(operator.getId()));
        assertTrue(world.accountControl().validate(passengers.getId()));
    }

    @Test
    public void test__the_types_are_stored_by_their_numbers() {
        assertEquals(0, Transactions.Type.BalanceCheckpoint.ordinal());
        assertEquals(1, Transactions.Type.JourneyPayment.ordinal());
        assertEquals(2, Transactions.Type.PilotFlightPay.ordinal());
        assertEquals(3, Transactions.Type.PilotIncomeShare.ordinal());
        assertEquals(4, Transactions.Type.values().length);
    }

    @Test
    public void test__a_checkpoint_survives_save_and_load(@TempDir final Path dir) throws IOException {
        world.accountControl().transfer(passengers.getId(), operator.getId(), 1_000, Transactions.Type.JourneyPayment);
        world.accountControl().fixBalance(operator.getId());
        world.accounts().save(dir);
        world.transactions().save(dir);

        final World loaded = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        loaded.accounts().loadIfExists(dir);
        loaded.transactions().loadIfExists(dir);

        assertEquals(2, loaded.transactions().all().count());
        assertEquals(1_000, loaded.accountControl().calculateBalance(operator.getId()));
        assertTrue(loaded.accountControl().validate(operator.getId()));
    }
}
