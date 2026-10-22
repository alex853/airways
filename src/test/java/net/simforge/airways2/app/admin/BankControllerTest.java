package net.simforge.airways2.app.admin;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.BankTransactions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The accounts as a plain text table for the admin.
 */
class BankControllerTest {
    private static String lines(final String text) {
        // the line ends of the platform and the spaces at the end do not matter
        return text.replace("\r\n", "\n").lines().map(String::stripTrailing).collect(java.util.stream.Collectors.joining("\n"));
    }

    @Test
    void noAccountsIsTheHeaderOnly() {
        final World world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);

        assertEquals("ID                  BALANCE  KIND       OWNER", lines(BankController.formatAccounts(world)));
    }

    @Test
    void everyAccountIsALineWithItsIdBalanceKindAndOwner() {
        final World world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final int now = world.getWorldTime();
        final BankAccounts.BankAccount bank = world.bankAccounts().create(BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK, true, now);
        final BankAccounts.BankAccount passengers = world.bankAccounts().create(BankAccounts.Kind.System, BankAccounts.SYSTEM_PASSENGERS, true, now);
        final BankAccounts.BankAccount operator = world.bankAccounts().create(BankAccounts.Kind.Operator, 3, true, now);
        final BankAccounts.BankAccount pilot = world.bankAccounts().create(BankAccounts.Kind.Person, 17, false, now);
        world.bankControl().transfer(passengers.getId(), operator.getId(), 10_484, BankTransactions.Type.JourneyPayment);
        world.bankControl().transfer(operator.getId(), pilot.getId(), 335, BankTransactions.Type.PilotFlightPay);

        assertEquals(String.join("\n",
                "ID                  BALANCE  KIND       OWNER",
                "1                         0  System     1",
                "2                    -10484  System     2",
                "3                     10149  Operator   3",
                "4                       335  Person     17"), lines(BankController.formatAccounts(world)));
        assertEquals(1, bank.getId());
    }

    @Test
    void accountStatementHasTheDetailsAndEveryTransactionOfTheAccount() {
        final World world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final int now = world.getWorldTime();
        world.bankAccounts().create(BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK, true, now);
        final BankAccounts.BankAccount passengers = world.bankAccounts().create(BankAccounts.Kind.System, BankAccounts.SYSTEM_PASSENGERS, true, now);
        final BankAccounts.BankAccount operator = world.bankAccounts().create(BankAccounts.Kind.Operator, 3, true, now);
        final BankAccounts.BankAccount pilot = world.bankAccounts().create(BankAccounts.Kind.Person, 17, false, now);
        world.bankControl().transfer(passengers.getId(), operator.getId(), 10_484, BankTransactions.Type.JourneyPayment);
        world.bankControl().transfer(operator.getId(), pilot.getId(), 335, BankTransactions.Type.PilotFlightPay);

        final String text = lines(BankController.formatAccount(world, operator.getId()));

        assertTrue(text.contains("Account #3"), text);
        assertTrue(text.contains("Kind:            Operator"), text);
        assertTrue(text.contains("Balance:         10149"), text);
        assertTrue(text.contains("Transactions: 2"), text);
        assertTrue(text.contains("10484  JourneyPayment"), text);
        assertTrue(text.contains("-335  PilotFlightPay"), text);
        assertTrue(text.contains("Balance check: OK"), text);
    }

    @Test
    void unknownAccountIsReported() {
        final World world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);

        assertEquals("Account #5 not found", lines(BankController.formatAccount(world, 5)));
    }
}
