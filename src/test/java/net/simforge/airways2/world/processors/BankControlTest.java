package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.BankTransactions;
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
class BankControlTest {
    private World world;
    private BankAccounts.BankAccount passengers;
    private BankAccounts.BankAccount operator;
    private BankAccounts.BankAccount pilot;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        // the passengers and the operator may go negative, the pilot may not
        passengers = world.bankAccounts().create(BankAccounts.Kind.System, BankAccounts.SYSTEM_PASSENGERS, true, world.getWorldTime());
        operator = world.bankAccounts().create(BankAccounts.Kind.Operator, 3, true, world.getWorldTime());
        pilot = world.bankAccounts().create(BankAccounts.Kind.Person, 7, false, world.getWorldTime());
    }

    private long sumOfBalances() {
        return world.bankAccounts().all().mapToLong(BankAccounts.BankAccount::getBalance).sum();
    }

    @Test
    public void test__a_new_account_is_empty() {
        assertEquals(0, pilot.getBalance());
        assertFalse(pilot.canGoNegative());
        assertTrue(operator.canGoNegative());
        assertEquals(world.getWorldTime(), pilot.getCreatedTime());
        assertEquals(world.getWorldTime(), pilot.getUpdatedTime());
    }

    @Test
    public void test__an_account_has_the_kind_and_the_owner() {
        assertEquals(BankAccounts.Kind.System, passengers.getKind());
        assertEquals(BankAccounts.SYSTEM_PASSENGERS, passengers.getOwnerId());
        assertEquals(BankAccounts.Kind.Operator, operator.getKind());
        assertEquals(3, operator.getOwnerId());
        assertEquals(BankAccounts.Kind.Person, pilot.getKind());
        assertEquals(7, pilot.getOwnerId());

        // the same id of an owner of another kind is another owner
        assertEquals(operator.getId(), world.bankAccounts().byOwner(BankAccounts.Kind.Operator, 3).orElseThrow().getId());
        assertEquals(pilot.getId(), world.bankAccounts().byOwner(BankAccounts.Kind.Person, 7).orElseThrow().getId());
        assertTrue(world.bankAccounts().byOwner(BankAccounts.Kind.Person, 3).isEmpty());
        assertTrue(world.bankAccounts().byOwner(BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK).isEmpty());
    }

    @Test
    public void test__a_person_refers_to_its_bank_account() {
        final net.simforge.airways2.world.datamodel.Persons.Person person = world.persons().create(
                net.simforge.airways2.world.datamodel.Persons.Type.Pilot, net.simforge.airways2.world.datamodel.Persons.State.Idle, 0,
                net.simforge.airways2.world.datamodel.Persons.LocationType.None, 0, 0, "AK", 0);
        assertEquals(0, person.getBankAccountId());

        final BankAccounts.BankAccount account = world.bankAccounts().create(BankAccounts.Kind.Person, person.getId(), false, world.getWorldTime());
        person.setBankAccountId(account.getId());

        // both ways: the person has the account, the account has its owner
        assertEquals(account.getId(), person.getBankAccountId());
        assertEquals(person.getId(), world.bankAccounts().byId(person.getBankAccountId()).orElseThrow().getOwnerId());
        assertEquals(BankAccounts.Kind.Person, world.bankAccounts().byId(person.getBankAccountId()).orElseThrow().getKind());
        // the other fields of the person are in place
        assertEquals("AK", person.getInitials());
        assertThrows(IllegalArgumentException.class, () -> person.setBankAccountId(16_777_216));
        assertThrows(IllegalArgumentException.class, () -> person.setBankAccountId(-1));
    }

    @Test
    public void test__an_owner_has_one_account_and_the_owner_is_checked() {
        final BankAccounts accounts = world.bankAccounts();
        final int now = world.getWorldTime();

        assertThrows(IllegalArgumentException.class, () -> accounts.create(BankAccounts.Kind.Operator, 3, true, now));
        assertThrows(IllegalArgumentException.class, () -> accounts.create(BankAccounts.Kind.Person, 0, false, now));
        assertThrows(IllegalArgumentException.class, () -> accounts.create(BankAccounts.Kind.Person, 16_777_216, false, now));
        assertThrows(IllegalArgumentException.class, () -> accounts.create(BankAccounts.Kind.Operator, 65_536, true, now));
        assertThrows(IllegalArgumentException.class, () -> accounts.create(BankAccounts.Kind.System, 99, true, now));
        assertThrows(IllegalArgumentException.class, () -> accounts.create(null, 1, true, now));
        assertEquals(3, accounts.all().count());

        // the bank is a system account too
        assertEquals(BankAccounts.SYSTEM_BANK, accounts.create(BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK, true, now).getOwnerId());
    }

    @Test
    public void test__transfer_moves_the_money_and_writes_the_transaction() {
        final BankTransactions.BankTransaction t = world.bankControl().transfer(passengers.getId(), operator.getId(), 10_484, BankTransactions.Type.JourneyPayment);

        assertEquals(-10_484, passengers.getBalance());
        assertEquals(10_484, operator.getBalance());
        assertEquals(passengers.getId(), t.getFromAccountId());
        assertEquals(operator.getId(), t.getToAccountId());
        assertEquals(10_484, t.getAmount());
        assertEquals(BankTransactions.Type.JourneyPayment, t.getType());
        assertEquals(world.getWorldTime(), t.getTime());
        assertEquals(1, world.bankTransactions().all().count());
    }

    @Test
    public void test__the_updated_time_follows_the_balance() {
        world.setWorldTime(world.getWorldTime() + 100);

        world.bankControl().transfer(passengers.getId(), operator.getId(), 5, BankTransactions.Type.JourneyPayment);

        assertEquals(world.getWorldTime(), passengers.getUpdatedTime());
        assertEquals(world.getWorldTime(), operator.getUpdatedTime());
        // an account which was not touched keeps its time, the creation time stays
        assertEquals(world.getWorldTime() - 100, pilot.getUpdatedTime());
        assertEquals(world.getWorldTime() - 100, operator.getCreatedTime());
    }

    @Test
    public void test__the_sum_of_all_the_balances_is_always_zero() {
        world.bankControl().transfer(passengers.getId(), operator.getId(), 10_484, BankTransactions.Type.JourneyPayment);
        assertEquals(0, sumOfBalances());

        world.bankControl().transfer(operator.getId(), pilot.getId(), 296, BankTransactions.Type.PilotFlightPay);
        world.bankControl().transfer(operator.getId(), pilot.getId(), 39, BankTransactions.Type.PilotIncomeShare);

        assertEquals(0, sumOfBalances());
        assertEquals(335, pilot.getBalance());
        assertEquals(10_484 - 335, operator.getBalance());
        assertEquals(3, world.bankTransactions().all().count());
    }

    @Test
    public void test__an_account_which_cannot_go_negative_cannot_pay_more_than_it_has() {
        world.bankControl().transfer(operator.getId(), pilot.getId(), 100, BankTransactions.Type.PilotFlightPay);

        assertThrows(IllegalArgumentException.class,
                () -> world.bankControl().transfer(pilot.getId(), operator.getId(), 101, BankTransactions.Type.PilotFlightPay));

        // nothing has changed
        assertEquals(100, pilot.getBalance());
        assertEquals(-100, operator.getBalance());
        assertEquals(1, world.bankTransactions().all().count());
        // all the money can be paid
        world.bankControl().transfer(pilot.getId(), operator.getId(), 100, BankTransactions.Type.PilotFlightPay);
        assertEquals(0, pilot.getBalance());
    }

    @Test
    public void test__the_transfer_is_checked() {
        final BankControl control = world.bankControl();

        assertThrows(IllegalArgumentException.class, () -> control.transfer(passengers.getId(), operator.getId(), 0, BankTransactions.Type.JourneyPayment));
        assertThrows(IllegalArgumentException.class, () -> control.transfer(passengers.getId(), operator.getId(), -5, BankTransactions.Type.JourneyPayment));
        assertThrows(IllegalArgumentException.class, () -> control.transfer(passengers.getId(), passengers.getId(), 5, BankTransactions.Type.JourneyPayment));
        assertThrows(IllegalArgumentException.class, () -> control.transfer(passengers.getId(), 999, 5, BankTransactions.Type.JourneyPayment));
        assertThrows(IllegalArgumentException.class, () -> control.transfer(999, operator.getId(), 5, BankTransactions.Type.JourneyPayment));
        assertThrows(IllegalArgumentException.class, () -> control.transfer(passengers.getId(), operator.getId(), 5, null));

        assertEquals(0, sumOfBalances());
        assertEquals(0, passengers.getBalance());
        assertEquals(0, world.bankTransactions().all().count());
    }

    @Test
    public void test__a_big_amount_does_not_overflow() {
        final long big = 5_000_000_000L;

        world.bankControl().transfer(passengers.getId(), operator.getId(), big, BankTransactions.Type.JourneyPayment);

        assertEquals(big, operator.getBalance());
        assertEquals(-big, passengers.getBalance());
        assertEquals(big, world.bankTransactions().all().findFirst().orElseThrow().getAmount());
    }

    @Test
    public void test__the_statement_of_an_account_has_what_it_paid_and_what_it_received() {
        final BankAccounts.BankAccount other = world.bankAccounts().create(BankAccounts.Kind.Operator, 4, true, world.getWorldTime());
        world.bankControl().transfer(passengers.getId(), operator.getId(), 100, BankTransactions.Type.JourneyPayment);
        world.bankControl().transfer(operator.getId(), pilot.getId(), 30, BankTransactions.Type.PilotFlightPay);
        world.bankControl().transfer(passengers.getId(), other.getId(), 7, BankTransactions.Type.JourneyPayment);

        final List<BankTransactions.Type> ofOperator = world.bankTransactions()
                .filter(world.bankTransactions().byAccountId(operator.getId()))
                .map(BankTransactions.BankTransaction::getType)
                .toList();

        assertEquals(List.of(BankTransactions.Type.JourneyPayment, BankTransactions.Type.PilotFlightPay), ofOperator);
        assertEquals(1, world.bankTransactions().filter(world.bankTransactions().byAccountId(pilot.getId())).count());
    }

    @Test
    public void test__the_world_keeps_the_accounts_and_the_transactions(@TempDir final Path dir) throws IOException {
        world.bankControl().transfer(passengers.getId(), operator.getId(), 10_484, BankTransactions.Type.JourneyPayment);

        world.bankAccounts().save(dir);
        world.bankTransactions().save(dir);
        assertTrue(Files.size(dir.resolve("bank-accounts")) > 0);
        assertTrue(Files.size(dir.resolve("bank-transactions")) > 0);

        final World loaded = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        loaded.bankAccounts().loadIfExists(dir);
        loaded.bankTransactions().loadIfExists(dir);

        assertEquals(-10_484, loaded.bankAccounts().byId(passengers.getId()).orElseThrow().getBalance());
        assertEquals(BankAccounts.Kind.Person, loaded.bankAccounts().byId(pilot.getId()).orElseThrow().getKind());
        assertEquals(7, loaded.bankAccounts().byId(pilot.getId()).orElseThrow().getOwnerId());
        assertTrue(loaded.bankAccounts().byId(operator.getId()).orElseThrow().canGoNegative());
        assertFalse(loaded.bankAccounts().byId(pilot.getId()).orElseThrow().canGoNegative());
        assertEquals(BankTransactions.Type.JourneyPayment, loaded.bankTransactions().all().findFirst().orElseThrow().getType());
        assertEquals(10_484, loaded.bankTransactions().all().findFirst().orElseThrow().getAmount());
    }

    @Test
    public void test__a_checkpoint_fixes_the_balance_and_moves_nothing() {
        world.bankControl().transfer(passengers.getId(), operator.getId(), 1_000, BankTransactions.Type.JourneyPayment);

        final BankTransactions.BankTransaction checkpoint = world.bankControl().fixBalance(passengers.getId());

        assertEquals(BankTransactions.Type.BalanceCheckpoint, checkpoint.getType());
        // the account which is verified is "from", "to" is not used
        assertEquals(passengers.getId(), checkpoint.getFromAccountId());
        assertEquals(0, checkpoint.getToAccountId());
        // the balance may be negative
        assertEquals(-1_000, checkpoint.getAmount());
        assertEquals(-1_000, passengers.getBalance());
        assertEquals(1_000, operator.getBalance());
        assertEquals(0, sumOfBalances());
    }

    @Test
    public void test__the_balance_is_validated_from_the_last_checkpoint_and_what_came_after() {
        world.bankControl().transfer(passengers.getId(), operator.getId(), 1_000, BankTransactions.Type.JourneyPayment);
        world.bankControl().transfer(operator.getId(), pilot.getId(), 300, BankTransactions.Type.PilotFlightPay);
        world.bankControl().fixBalance(operator.getId());
        world.bankControl().transfer(operator.getId(), pilot.getId(), 40, BankTransactions.Type.PilotIncomeShare);
        world.bankControl().transfer(passengers.getId(), operator.getId(), 500, BankTransactions.Type.JourneyPayment);

        // 700 at the checkpoint, -40, +500
        assertEquals(1_160, operator.getBalance());
        assertTrue(world.bankControl().validate(operator.getId()));
        assertTrue(world.bankControl().validate(passengers.getId()));
        assertTrue(world.bankControl().validate(pilot.getId()));
    }

    @Test
    public void test__without_a_checkpoint_the_balance_is_validated_from_zero() {
        world.bankControl().transfer(passengers.getId(), operator.getId(), 1_000, BankTransactions.Type.JourneyPayment);
        world.bankControl().transfer(operator.getId(), pilot.getId(), 335, BankTransactions.Type.PilotFlightPay);

        assertEquals(665, operator.getBalance());
        assertTrue(world.bankControl().validate(operator.getId()));
        assertTrue(world.bankControl().validate(world.bankAccounts().create(BankAccounts.Kind.Operator, 5, true, world.getWorldTime()).getId()));
    }

    @Test
    public void test__a_balance_which_does_not_match_the_transactions_is_found() {
        world.bankControl().transfer(passengers.getId(), operator.getId(), 1_000, BankTransactions.Type.JourneyPayment);
        world.bankControl().fixBalance(operator.getId());
        assertTrue(world.bankControl().validate(operator.getId()));

        // somebody has changed the balance bypassing the transfer
        operator.changeBalance(5, world.getWorldTime());

        assertFalse(world.bankControl().validate(operator.getId()));
        assertTrue(world.bankControl().validate(passengers.getId()));
    }

    @Test
    public void test__the_types_are_stored_by_their_numbers() {
        assertEquals(0, BankTransactions.Type.BalanceCheckpoint.ordinal());
        assertEquals(1, BankTransactions.Type.JourneyPayment.ordinal());
        assertEquals(2, BankTransactions.Type.PilotFlightPay.ordinal());
        assertEquals(3, BankTransactions.Type.PilotIncomeShare.ordinal());
        assertEquals(4, BankTransactions.Type.Travel.ordinal());
        assertEquals(5, BankTransactions.Type.PilotMeals.ordinal());
        assertEquals(6, BankTransactions.Type.PilotLodging.ordinal());
        assertEquals(7, BankTransactions.Type.values().length);
    }

    @Test
    public void test__a_checkpoint_survives_save_and_load(@TempDir final Path dir) throws IOException {
        world.bankControl().transfer(passengers.getId(), operator.getId(), 1_000, BankTransactions.Type.JourneyPayment);
        world.bankControl().fixBalance(operator.getId());
        world.bankAccounts().save(dir);
        world.bankTransactions().save(dir);

        final World loaded = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        loaded.bankAccounts().loadIfExists(dir);
        loaded.bankTransactions().loadIfExists(dir);

        assertEquals(2, loaded.bankTransactions().all().count());
        assertEquals(1_000, loaded.bankAccounts().byId(operator.getId()).orElseThrow().getBalance());
        assertTrue(loaded.bankControl().validate(operator.getId()));
    }

    @Test
    public void test__a_checkpoint_is_a_value_and_not_a_change() {
        world.bankControl().transfer(passengers.getId(), operator.getId(), 1_000, BankTransactions.Type.JourneyPayment);
        world.bankControl().fixBalance(operator.getId());
        world.bankControl().fixBalance(operator.getId());
        world.bankControl().fixBalance(operator.getId());

        // three checkpoints of 1000 are not 3000
        assertTrue(world.bankControl().validate(operator.getId()));
        assertEquals(1_000, operator.getBalance());
    }

    @Test
    public void test__a_checkpoint_of_one_account_is_not_in_the_statement_of_another() {
        world.bankControl().fixBalance(operator.getId());

        assertEquals(1, world.bankTransactions().filter(world.bankTransactions().byAccountId(operator.getId())).count());
        assertEquals(0, world.bankTransactions().filter(world.bankTransactions().byAccountId(pilot.getId())).count());
    }

    @Test
    public void test__a_transfer_which_does_not_fit_five_bytes_is_refused_and_nothing_changes() {
        final long max = net.simforge.airways2.storage.DataType.Signed40bit.maxValue();
        world.bankControl().transfer(passengers.getId(), operator.getId(), max, BankTransactions.Type.JourneyPayment);

        // the amount is too big
        assertThrows(IllegalArgumentException.class,
                () -> world.bankControl().transfer(passengers.getId(), operator.getId(), max + 1, BankTransactions.Type.JourneyPayment));
        // the account which receives would be over the maximum
        assertThrows(IllegalArgumentException.class,
                () -> world.bankControl().transfer(passengers.getId(), operator.getId(), 1, BankTransactions.Type.JourneyPayment));
        // the account which pays may go down to the minimum, that is -max-1, and not more
        final BankAccounts.BankAccount other = world.bankAccounts().create(BankAccounts.Kind.Operator, 9, true, world.getWorldTime());
        world.bankControl().transfer(passengers.getId(), other.getId(), 1, BankTransactions.Type.JourneyPayment);
        assertEquals(-max - 1, passengers.getBalance());
        assertThrows(IllegalArgumentException.class,
                () -> world.bankControl().transfer(passengers.getId(), other.getId(), 1, BankTransactions.Type.JourneyPayment));

        assertEquals(max, operator.getBalance());
        assertEquals(-max - 1, passengers.getBalance());
        assertEquals(2, world.bankTransactions().all().count());
        assertEquals(0, sumOfBalances());
    }
}
