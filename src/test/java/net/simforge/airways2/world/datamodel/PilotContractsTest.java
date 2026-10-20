package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.world.datamodel.PilotContracts.Penalty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The storage of the contracts of the pilots: a contract per flight mission with the base numbers and the penalty flags.
 */
class PilotContractsTest {
    @Test
    void aNewContractHasTheBaseNumbersAndNoPenalties() {
        final PilotContracts contracts = new PilotContracts();

        final PilotContracts.Contract contract = contracts.create(1234, 3, 7, 650, 5);

        assertEquals(1234, contract.getFlightMissionId());
        assertEquals(3, contract.getOperatorId());
        assertEquals(7, contract.getPersonId());
        assertEquals(650, contract.getBaseFlightPay());
        assertEquals(5, contract.getBaseIncomeSharePercentTenths());
        for (final Penalty penalty : Penalty.values()) {
            assertFalse(contract.hasPenalty(penalty), penalty.name());
        }
    }

    @Test
    void everyPenaltyIsItsOwnFlagAndTheOthersAreNotTouched() {
        final PilotContracts.Contract contract = new PilotContracts().create(1, 3, 7, 100, 10);

        contract.setPenalty(Penalty.NoNightCurrency, true);
        assertTrue(contract.hasPenalty(Penalty.NoNightCurrency));
        assertFalse(contract.hasPenalty(Penalty.NoTypeCurrency));
        assertFalse(contract.hasPenalty(Penalty.NoCarryPassengers));
        assertFalse(contract.hasPenalty(Penalty.Manual));

        contract.setPenalty(Penalty.Manual, true);
        contract.setPenalty(Penalty.NoTypeCurrency, true);
        contract.setPenalty(Penalty.NoNightCurrency, false);
        assertTrue(contract.hasPenalty(Penalty.Manual));
        assertTrue(contract.hasPenalty(Penalty.NoTypeCurrency));
        assertFalse(contract.hasPenalty(Penalty.NoNightCurrency));
        assertFalse(contract.hasPenalty(Penalty.NoCarryPassengers));

        // the numbers stay as they are
        assertEquals(100, contract.getBaseFlightPay());
        assertEquals(10, contract.getBaseIncomeSharePercentTenths());
    }

    @Test
    void theBaseNumbersCanBeChangedByAnEvent() {
        final PilotContracts.Contract contract = new PilotContracts().create(1, 3, 7, 100, 10);

        contract.setBaseFlightPay(250);
        contract.setBaseIncomeSharePercentTenths(70);

        assertEquals(250, contract.getBaseFlightPay());
        assertEquals(70, contract.getBaseIncomeSharePercentTenths());
        assertThrows(IllegalArgumentException.class, () -> contract.setBaseFlightPay(-1));
        assertThrows(IllegalArgumentException.class, () -> contract.setBaseIncomeSharePercentTenths(1001));
    }

    @Test
    void theContractIsFoundByTheFlightMission() {
        final PilotContracts contracts = new PilotContracts();
        final PilotContracts.Contract first = contracts.create(10, 3, 7, 100, 10);
        final PilotContracts.Contract second = contracts.create(20, 3, 8, 200, 20);

        assertEquals(first.getId(), contracts.byFlightMissionId(10).orElseThrow().getId());
        assertEquals(second.getId(), contracts.byFlightMissionId(20).orElseThrow().getId());
        assertEquals(Optional.empty(), contracts.byFlightMissionId(30));
        assertEquals(1, contracts.filter(contracts.byPersonId(8)).count());
    }

    @Test
    void aFlightMissionHasOneContract() {
        final PilotContracts contracts = new PilotContracts();
        contracts.create(10, 3, 7, 100, 10);

        assertThrows(IllegalArgumentException.class, () -> contracts.create(10, 3, 7, 100, 10));
    }

    @Test
    void theContractIsDeletedWithItsFlightMission() {
        final PilotContracts contracts = new PilotContracts();
        contracts.create(10, 3, 7, 100, 10);
        contracts.create(20, 3, 7, 200, 20);

        assertTrue(contracts.deleteByFlightMissionId(10));

        assertEquals(Optional.empty(), contracts.byFlightMissionId(10));
        assertTrue(contracts.byFlightMissionId(20).isPresent());
        assertEquals(1, contracts.all().count());
        assertFalse(contracts.deleteByFlightMissionId(10));
        // the flight mission can get a new contract, for instance after a rebooking
        contracts.create(10, 3, 7, 300, 30);
        assertEquals(300, contracts.byFlightMissionId(10).orElseThrow().getBaseFlightPay());
    }

    @Test
    void theNumbersAreChecked() {
        final PilotContracts contracts = new PilotContracts();

        assertThrows(IllegalArgumentException.class, () -> contracts.create(0, 3, 7, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 0, 7, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 65536, 7, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 3, 0, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 3, 7, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 3, 7, 100, 1001));
        assertEquals(0, contracts.all().count());
    }

    @Test
    void aRecordIs32BytesAndTheContractsSurviveSaveAndLoad(@TempDir final Path dir) throws IOException {
        final PilotContracts contracts = new PilotContracts();
        final PilotContracts.Contract contract = contracts.create(10, 3, 7, 650, 5);
        contract.setPenalty(Penalty.Manual, true);
        contract.setPenalty(Penalty.NoTypeCurrency, true);

        contracts.save(dir);
        assertEquals(32, Files.readAllBytes(dir.resolve("pilot-contracts")).length);

        final PilotContracts loaded = new PilotContracts();
        loaded.loadIfExists(dir);

        final PilotContracts.Contract restored = loaded.byFlightMissionId(10).orElseThrow();
        assertEquals(contract.getId(), restored.getId());
        assertEquals(3, restored.getOperatorId());
        assertEquals(7, restored.getPersonId());
        assertEquals(650, restored.getBaseFlightPay());
        assertEquals(5, restored.getBaseIncomeSharePercentTenths());
        assertTrue(restored.hasPenalty(Penalty.Manual));
        assertTrue(restored.hasPenalty(Penalty.NoTypeCurrency));
        assertFalse(restored.hasPenalty(Penalty.NoNightCurrency));
        // the index by the flight mission is rebuilt after the loading
        assertThrows(IllegalArgumentException.class, () -> loaded.create(10, 3, 7, 1, 1));
    }

    @Test
    void theStorageIsPartOfTheWorld() {
        final net.simforge.airways2.world.World world = net.simforge.airways2.world.World.create(
                new net.simforge.airways2.world.InMemoryStorageStrategy(), net.simforge.airways2.world.Time.START_TIME_EPOCH_SECONDS);

        world.pilotContracts().create(5, 3, 1, 100, 10);

        assertTrue(world.pilotContracts().byFlightMissionId(5).isPresent());
    }
}
