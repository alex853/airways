package net.simforge.airways2.app.admin;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.worldbuilder.World25;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one-time update which creates the first five accounts.
 */
class BankInitialAccountsTest {
    private World world;
    private Persons.Person person;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        world.aircraftOperators().create("AA", "AAA", "first");
        world.aircraftOperators().create("SJ", "SJT", "shadowJet");
        world.aircraftOperators().create("BB", "BBD", "busyBirds");
        assertEquals(World25.ShadowJetOperatorId, 2);
        assertEquals(World25.BusyBirdsOperatorId, 3);
        person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 7, Persons.LocationType.None, 0, 0, "AK", 0);
        assertEquals(1, person.getId());
    }

    @Test
    void dryRunChangesNothing() {
        final String result = BankController.createInitialAccounts(world, true);

        assertTrue(result.startsWith("DRY RUN - nothing is changed"), result);
        assertEquals(5, result.lines().filter(l -> l.contains("should be created")).count(), result);
        assertEquals(0, world.bankAccounts().all().count());
        assertEquals(0, person.getBankAccountId());
    }

    @Test
    void theFiveAccountsAreCreatedInTheOrder() {
        BankController.createInitialAccounts(world, false);

        assertEquals(5, world.bankAccounts().all().count());
        assertAccount(1, BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK, true);
        assertAccount(2, BankAccounts.Kind.System, BankAccounts.SYSTEM_PASSENGERS, true);
        assertAccount(BankAccounts.ShadowJetBankAccountId, BankAccounts.Kind.Operator, World25.ShadowJetOperatorId, true);
        assertAccount(BankAccounts.BusyBirdsBankAccountId, BankAccounts.Kind.Operator, World25.BusyBirdsOperatorId, true);
        assertEquals(3, BankAccounts.ShadowJetBankAccountId);
        assertEquals(4, BankAccounts.BusyBirdsBankAccountId);
        assertAccount(5, BankAccounts.Kind.Person, 1, false);
        // the balances are zero
        assertEquals(0, world.bankAccounts().all().mapToLong(BankAccounts.BankAccount::getBalance).sum());
    }

    private void assertAccount(final int id, final BankAccounts.Kind kind, final int ownerId, final boolean canGoNegative) {
        final BankAccounts.BankAccount account = world.bankAccounts().byId(id).orElseThrow();
        assertEquals(kind, account.getKind(), "account " + id);
        assertEquals(ownerId, account.getOwnerId(), "account " + id);
        assertEquals(canGoNegative, account.canGoNegative(), "account " + id);
        assertEquals(0, account.getBalance(), "account " + id);
    }

    @Test
    void thePersonRefersToItsAccount() {
        BankController.createInitialAccounts(world, false);

        assertEquals(5, person.getBankAccountId());
        assertEquals(BankAccounts.Kind.Person, world.bankAccounts().byId(person.getBankAccountId()).orElseThrow().getKind());
    }

    @Test
    void aRepeatedCallCreatesNothingMore() {
        BankController.createInitialAccounts(world, false);

        final String second = BankController.createInitialAccounts(world, false);

        assertEquals(5, world.bankAccounts().all().count());
        assertEquals(5, second.lines().filter(l -> l.contains("exists already")).count(), second);
        assertFalse(second.contains("created, account"), second);
    }

    @Test
    void aMissingOwnerIsSkipped() {
        // a world without the person #1: nothing for the person, the other four are created
        final World other = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        other.aircraftOperators().create("AA", "AAA", "first");
        other.aircraftOperators().create("SJ", "SJT", "shadowJet");
        other.aircraftOperators().create("BB", "BBD", "busyBirds");

        final String result = BankController.createInitialAccounts(other, false);

        assertEquals(4, other.bankAccounts().all().count());
        assertTrue(result.lines().anyMatch(l -> l.startsWith("Person #1") && l.endsWith("owner not found, skipped")), result);
    }

    @Test
    void anAccountWhichIsNotWhereTheConstantSaysIsWarnedAbout() {
        // somebody has created another account first: the accounts of the operators are not #3 and #4
        world.bankAccounts().create(BankAccounts.Kind.Person, 99, false, world.getWorldTime());

        final String result = BankController.createInitialAccounts(world, false);

        assertTrue(result.contains("WARNING: the account is #4, but the constant of the code says #3"), result);
        assertTrue(result.contains("WARNING: the account is #5, but the constant of the code says #4"), result);
    }

    @Test
    void theNormalRunHasNoWarnings() {
        assertFalse(BankController.createInitialAccounts(world, false).contains("WARNING"));
    }
}
