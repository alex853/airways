package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.world.computations.PilotPayParams;
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
 * The storage of the contracts of the pilots: a contract per flight mission with the base flight pay, the base income and the byte of the parameters.
 */
class PilotContractsTest {
    private static PilotPayParams params(final int level, final boolean type, final boolean night, final boolean pax, final boolean manual) {
        final PilotPayParams params = PilotPayParams.none();
        params.setExperienceLevel(level);
        params.setTypeCurrency(type);
        params.setNightCurrency(night);
        params.setCanCarryPassengers(pax);
        params.setManual(manual);
        return params;
    }

    private static PilotPayParams anyParams() {
        return params(3, true, true, true, false);
    }

    @Test
    void aNewContractHasTheBaseNumbersAndTheParameters() {
        final PilotContracts contracts = new PilotContracts();

        final PilotContracts.Contract contract = contracts.create(1234, 3, 7, 650, params(2, true, false, true, false));

        assertEquals(1234, contract.getFlightMissionId());
        assertEquals(3, contract.getOperatorId());
        assertEquals(7, contract.getPersonId());
        assertEquals(650, contract.getBaseFlightPay());
        final PilotPayParams stored = contract.getPayParams();
        assertEquals(2, stored.getExperienceLevel());
        assertTrue(stored.hasTypeCurrency());
        assertFalse(stored.hasNightCurrency());
        assertTrue(stored.canCarryPassengers());
        assertFalse(stored.isManual());
    }

    @Test
    void anEventChangesTheParametersButNotTheOtherNumbers() {
        final PilotContracts.Contract contract = new PilotContracts().create(1, 3, 7, 100, params(1, true, true, true, false));

        // the manual start
        final PilotPayParams params = contract.getPayParams();
        params.setManual(true);
        contract.setPayParams(params);

        assertTrue(contract.getPayParams().isManual());
        assertEquals(1, contract.getPayParams().getExperienceLevel());
        assertTrue(contract.getPayParams().hasTypeCurrency());
        assertEquals(100, contract.getBaseFlightPay());
        assertEquals(7, contract.getPersonId());
    }

    @Test
    void theFlightPayFitsThreeBytesAndNotMore() {
        final PilotContracts.Contract contract = new PilotContracts().create(1, 3, 7, 100, anyParams());

        contract.setBaseFlightPay(PilotContracts.MAX_AMOUNT);
        assertEquals(16_777_215, contract.getBaseFlightPay());

        assertThrows(IllegalArgumentException.class, () -> contract.setBaseFlightPay(PilotContracts.MAX_AMOUNT + 1));
        assertThrows(IllegalArgumentException.class, () -> contract.setBaseFlightPay(-1));
        // the neighbours do not suffer
        assertEquals(7, contract.getPersonId());
        assertEquals(3, contract.getPayParams().getExperienceLevel());
    }

    @Test
    void theContractIsFoundByTheFlightMission() {
        final PilotContracts contracts = new PilotContracts();
        final PilotContracts.Contract first = contracts.create(10, 3, 7, 100, anyParams());
        final PilotContracts.Contract second = contracts.create(20, 3, 8, 200, anyParams());

        assertEquals(first.getId(), contracts.byFlightMissionId(10).orElseThrow().getId());
        assertEquals(second.getId(), contracts.byFlightMissionId(20).orElseThrow().getId());
        assertEquals(Optional.empty(), contracts.byFlightMissionId(30));
        assertEquals(1, contracts.filter(contracts.byPersonId(8)).count());
    }

    @Test
    void aFlightMissionHasOneContract() {
        final PilotContracts contracts = new PilotContracts();
        contracts.create(10, 3, 7, 100, anyParams());

        assertThrows(IllegalArgumentException.class, () -> contracts.create(10, 3, 7, 100, anyParams()));
    }

    @Test
    void theContractIsDeletedWithItsFlightMission() {
        final PilotContracts contracts = new PilotContracts();
        contracts.create(10, 3, 7, 100, anyParams());
        contracts.create(20, 3, 7, 200, anyParams());

        assertTrue(contracts.deleteByFlightMissionId(10));

        assertEquals(Optional.empty(), contracts.byFlightMissionId(10));
        assertTrue(contracts.byFlightMissionId(20).isPresent());
        assertEquals(1, contracts.all().count());
        assertFalse(contracts.deleteByFlightMissionId(10));
        // the flight mission can get a new contract, for instance after a rebooking
        contracts.create(10, 3, 7, 300, anyParams());
        assertEquals(300, contracts.byFlightMissionId(10).orElseThrow().getBaseFlightPay());
    }

    @Test
    void theNumbersAreChecked() {
        final PilotContracts contracts = new PilotContracts();

        assertThrows(IllegalArgumentException.class, () -> contracts.create(0, 3, 7, 100, anyParams()));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 0, 7, 100, anyParams()));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 65536, 7, 100, anyParams()));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 3, 0, 100, anyParams()));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 3, 7, -1, anyParams()));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 3, 7, PilotContracts.MAX_AMOUNT + 1, anyParams()));
        assertThrows(IllegalArgumentException.class, () -> contracts.create(1, 3, 7, 100, null));
        assertEquals(0, contracts.all().count());
    }

    @Test
    void aRecordIs32BytesAndTheContractsSurviveSaveAndLoad(@TempDir final Path dir) throws IOException {
        final PilotContracts contracts = new PilotContracts();
        final PilotContracts.Contract contract = contracts.create(10, 3, 7, 650, params(4, true, false, true, true));

        contracts.save(dir);
        assertEquals(32, Files.readAllBytes(dir.resolve("pilot-contracts")).length);

        final PilotContracts loaded = new PilotContracts();
        loaded.loadIfExists(dir);

        final PilotContracts.Contract restored = loaded.byFlightMissionId(10).orElseThrow();
        assertEquals(contract.getId(), restored.getId());
        assertEquals(3, restored.getOperatorId());
        assertEquals(7, restored.getPersonId());
        assertEquals(650, restored.getBaseFlightPay());
        final PilotPayParams params = restored.getPayParams();
        assertEquals(4, params.getExperienceLevel());
        assertTrue(params.hasTypeCurrency());
        assertFalse(params.hasNightCurrency());
        assertTrue(params.canCarryPassengers());
        assertTrue(params.isManual());
        // the index by the flight mission is rebuilt after the loading
        assertThrows(IllegalArgumentException.class, () -> loaded.create(10, 3, 7, 1, anyParams()));
    }

    @Test
    void theStorageIsPartOfTheWorld() {
        final net.simforge.airways2.world.World world = net.simforge.airways2.world.World.create(
                new net.simforge.airways2.world.InMemoryStorageStrategy(), net.simforge.airways2.world.Time.START_TIME_EPOCH_SECONDS);

        world.pilotContracts().create(5, 3, 1, 100, anyParams());

        assertTrue(world.pilotContracts().byFlightMissionId(5).isPresent());
    }
}
