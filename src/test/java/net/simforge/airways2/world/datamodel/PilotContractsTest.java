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

        final PilotContracts.Contract contract = contracts.create(1234, 3, 7, 650, 0.5f);

        assertEquals(1234, contract.getFlightMissionId());
        assertEquals(3, contract.getOperatorId());
        assertEquals(7, contract.getPersonId());
        assertEquals(650, contract.getBaseFlightPay());
        assertEquals(0.5f, contract.getBaseIncomeSharePercent(), 0.0f);
        for (final Penalty penalty : Penalty.values()) {
            assertFalse(contract.hasPenalty(penalty), penalty.name());
        }
    }

    @Test
    void everyPenaltyIsItsOwnFlagAndTheOthersAreNotTouched() {
        final PilotContracts.Contract contract = new PilotContracts().create(1, 3, 7, 100, 1.0f);

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
        assertEquals(1.0f, contract.getBaseIncomeSharePercent(), 0.0f);
    }

    @Test
    void theBaseNumbersCanBeChangedByAnEvent() {
        final PilotContracts.Contract contract = new PilotContracts().create(1, 3, 7, 100, 1.0f);

        contract.setBaseFlightPay(250);
        contract.setBaseIncomeSharePercent(7.0f);

        assertEquals(250, contract.getBaseFlightPay());
        assertEquals(7.0f, contract.getBaseIncomeSharePercent(), 0.0f);
        assertThrows(IllegalArgumentException.class, () -> contract.setBaseFlightPay(-1));
        assertThrows(IllegalArgumentException.class, () -> contract.setBaseFlightPay(PilotContracts.MAX_FLIGHT_PAY + 1));
        assertThrows(IllegalArgumentException.class, () -> contract.setBaseIncomeSharePercent(25.6f));
        assertThrows(IllegalArgumentException.class, () -> contract.setBaseIncomeSharePercent(-0.1f));

        // the flight pay fits three bytes and the neighbours do not suffer
        contract.setBaseFlightPay(PilotContracts.MAX_FLIGHT_PAY);
        assertEquals(16_777_215, contract.getBaseFlightPay());
        assertEquals(7.0f, contract.getBaseIncomeSharePercent(), 0.0f);
        assertEquals(7, contract.getPersonId());
    }

    @Test
    void theContractIsFoundByTheFlightMission() {
        final PilotContracts contracts = new PilotContracts();
        final PilotContracts.Contract first = contracts.create(10, 3, 7, 100, 1.0f);
        final PilotContracts.Contract second = contracts.create(20, 3, 8, 200, 2.0f);

        assertEquals(first.getId(), contracts.byFlightMissionId(10).orElseThrow().getId());
        assertEquals(second.getId(), contracts.byFlightMissionId(20).orElseThrow().getId());
        assertEquals(Optional.empty(), contracts.byFlightMissionId(30));
        assertEquals(1, contracts.filter(contracts.byPersonId(8)).count());
    }

    @Test
    void aFlightMissionHasOneContract() {
        final PilotContracts contracts = new PilotContracts();
        contracts.create(10, 3, 7, 100, 1.0f);

        assertThrows(IllegalArgumentException.class, () -> contracts.create(10, 3, 7, 100, 1.0f));
    }

    @Test
    void theContractIsDeletedWithItsFlightMission() {
        final PilotContracts contracts = new PilotContracts();
        contracts.create(10, 3, 7, 100, 1.0f);
        contracts.create(20, 3, 7, 200, 2.0f);

        assertTrue(contracts.deleteByFlightMissionId(10));

        assertEquals(Optional.empty(), contracts.byFlightMissionId(10));
        assertTrue(contracts.byFlightMissionId(20).isPresent());
        assertEquals(1, contracts.all().count());
        assertFalse(contracts.deleteByFlightMissionId(10));
        // the flight mission can get a new contract, for instance after a rebooking
        contracts.create(10, 3, 7, 300, 3.0f);
        assertEquals(300, contracts.byFlightMissionId(10).orElseThrow().getBaseFlightPay());
    }

    @Test
    void theNumbersAreChecked() {
        final PilotContracts contracts = new PilotContracts();

        assertThrows(IllegalArgumentException.class, () -> contracts.create(0, 3, 7, 100, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 0, 7, 100, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 65536, 7, 100, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 3, 0, 100, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 3, 7, -1, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 3, 7, PilotContracts.MAX_FLIGHT_PAY + 1, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 3, 7, 100, 25.6f));
        assertEquals(0, contracts.all().count());
    }

    @Test
    void aRecordIs32BytesAndTheContractsSurviveSaveAndLoad(@TempDir final Path dir) throws IOException {
        final PilotContracts contracts = new PilotContracts();
        final PilotContracts.Contract contract = contracts.create(10, 3, 7, 650, 0.5f);
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
        assertEquals(0.5f, restored.getBaseIncomeSharePercent(), 0.0f);
        assertTrue(restored.hasPenalty(Penalty.Manual));
        assertTrue(restored.hasPenalty(Penalty.NoTypeCurrency));
        assertFalse(restored.hasPenalty(Penalty.NoNightCurrency));
        // the index by the flight mission is rebuilt after the loading
        assertThrows(IllegalArgumentException.class, () -> loaded.create(10, 3, 7, 1, 0.1f));
    }

    @Test
    void theStorageIsPartOfTheWorld() {
        final net.simforge.airways2.world.World world = net.simforge.airways2.world.World.create(
                new net.simforge.airways2.world.InMemoryStorageStrategy(), net.simforge.airways2.world.Time.START_TIME_EPOCH_SECONDS);

        world.pilotContracts().create(5, 3, 1, 100, 1.0f);

        assertTrue(world.pilotContracts().byFlightMissionId(5).isPresent());
    }
}
