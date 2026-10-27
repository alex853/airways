package net.simforge.airways2.app.user;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.BankTransactions;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GET /pilot/bank: the last 10 operations of the account of the pilot, the latest first, with the signed amount and the balance after the operation.
 */
class PilotBankEndpointTest {
    private World world;
    private Persons.Person person;
    private BankAccounts.BankAccount pilotAccount;
    private BankAccounts.BankAccount bank;
    private BankAccounts.BankAccount operator;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1, Persons.LocationType.None, 0, 0, "AK", 0);
        bank = world.bankAccounts().create(BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK, true, world.getWorldTime());
        operator = world.bankAccounts().create(BankAccounts.Kind.Operator, 3, true, world.getWorldTime());
        pilotAccount = world.bankAccounts().create(BankAccounts.Kind.Person, person.getId(), false, world.getWorldTime());
        person.setBankAccountId(pilotAccount.getId());
    }

    @Test
    void aPilotWithoutAnAccountHasNothing() {
        person.setBankAccountId(0);

        final PilotController.BankDto bankDto = PilotController.buildBank(world, person);

        assertNull(bankDto.getBalance());
        assertTrue(bankDto.getOperations().isEmpty());
    }

    @Test
    void theLastTenOperationsLatestFirstWithTheBalanceAfterEach() {
        world.bankControl().transfer(operator.getId(), pilotAccount.getId(), 1000, BankTransactions.Type.PilotFlightPay);
        world.bankControl().fixBalance(pilotAccount.getId());
        world.setWorldTime(world.getWorldTime() + 3600 + 5 * 60 + 17);
        world.bankControl().transfer(pilotAccount.getId(), bank.getId(), 44, BankTransactions.Type.PilotLodging);
        world.bankControl().transfer(pilotAccount.getId(), bank.getId(), 7, BankTransactions.Type.PilotMeals);
        world.bankControl().transfer(operator.getId(), pilotAccount.getId(), 300, BankTransactions.Type.PilotIncomeShare);

        final PilotController.BankDto dto = PilotController.buildBank(world, person);

        assertEquals(1000 - 44 - 7 + 300, dto.getBalance());
        assertEquals(4, dto.getOperations().size(), "the checkpoint is not an operation");
        // the latest first
        assertEquals("Income share", dto.getOperations().get(0).getType());
        assertEquals(300, dto.getOperations().get(0).getAmount());
        assertEquals(1249, dto.getOperations().get(0).getBalance());
        assertEquals("Meals", dto.getOperations().get(1).getType());
        assertEquals(-7, dto.getOperations().get(1).getAmount());
        assertEquals(949, dto.getOperations().get(1).getBalance());
        assertEquals("Night", dto.getOperations().get(2).getType());
        assertEquals(-44, dto.getOperations().get(2).getAmount());
        assertEquals(956, dto.getOperations().get(2).getBalance());
        assertEquals("Flight pay", dto.getOperations().get(3).getType());
        assertEquals(1000, dto.getOperations().get(3).getBalance());
        // the time is to the minute: 2026-.. T hh:mm
        assertTrue(dto.getOperations().get(0).getTime().matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d"), dto.getOperations().get(0).getTime());
    }

    @Test
    void notMoreThanTenOperations() {
        world.bankControl().transfer(operator.getId(), pilotAccount.getId(), 1000, BankTransactions.Type.PilotFlightPay);
        for (int i = 0; i < 14; i++) {
            world.bankControl().transfer(pilotAccount.getId(), bank.getId(), 10, BankTransactions.Type.PilotMeals);
        }

        final PilotController.BankDto dto = PilotController.buildBank(world, person);

        assertEquals(10, dto.getOperations().size());
        assertEquals(860, dto.getOperations().get(0).getBalance());
        assertEquals(950, dto.getOperations().get(9).getBalance());
    }
}
