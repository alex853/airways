package net.simforge.airways2.app.user;

import net.simforge.airways2.app.user.PilotController.PilotDto;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.BankTransactions;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The pilot of the user as the page of the pilot shows it: who, the state, where and how much money.
 */
class PilotEndpointTest {
    private World world;
    private Airports.Airport oakland;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        oakland = world.airports().create(37.72, -122.22, "OAK", "KOAK", "Oakland");
    }

    @Test
    void pilotHasTheBalanceOfItsBankAccount() {
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1, Persons.LocationType.Airport, oakland.getId(), 0, "AK", 0);
        final BankAccounts.BankAccount pilotAccount = world.bankAccounts().create(BankAccounts.Kind.Person, person.getId(), false, world.getWorldTime());
        final BankAccounts.BankAccount operatorAccount = world.bankAccounts().create(BankAccounts.Kind.Operator, 3, true, world.getWorldTime());
        person.setBankAccountId(pilotAccount.getId());
        world.bankControl().transfer(operatorAccount.getId(), pilotAccount.getId(), 12_345, BankTransactions.Type.PilotFlightPay);

        final PilotDto pilot = PilotController.buildPilot(world, person);

        assertEquals(12_345L, pilot.getBankBalance());
        assertEquals("AK", pilot.getInitials());
        assertEquals("Idle", pilot.getState());
        assertEquals("KOAK", pilot.getAirportIcao());
    }

    @Test
    void pilotWithoutAnAccountHasNoBalance() {
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.OnDuty, 1, Persons.LocationType.None, 0, 0, "AK", 0);

        final PilotDto pilot = PilotController.buildPilot(world, person);

        assertNull(pilot.getBankBalance());
        assertEquals("None", pilot.getLocationType());
        assertNull(pilot.getAirportIcao());
    }
}
