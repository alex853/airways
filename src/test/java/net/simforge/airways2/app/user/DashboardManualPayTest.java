package net.simforge.airways2.app.user;

import net.simforge.airways2.app.user.FlightDashboardController.ManualPayDto;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PilotPayParams;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the dashboard says before a manual start: the pay by the contract and the pay if the flight is manual.
 */
class DashboardManualPayTest {
    private World world;
    private FlightMissions.Mission mission;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final Airports.Airport from = world.airports().create(36.59, -115.67, "INS", "KINS", "Indian Springs");
        final Airports.Airport to = world.airports().create(37.36, -121.93, "SJC", "KSJC", "San Jose");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(world.aircraftTypes().create("GA7C", "GL7"), "BB-GLA", from);
        final int departure = world.getWorldTime() + 3600;
        mission = world.flightMissions().createDispatchedMission(aircraft, from, to, departure, departure + 4260);
        world.aircraftOperators().create("BB", "BBD", "busyBirds");
    }

    private void contract(final int baseFlightPay, final int baseIncome, final int level, final boolean typeCurrency) {
        final PilotPayParams params = PilotPayParams.none();
        params.setNightCurrency(true);
        params.setCanCarryPassengers(true);
        params.setTypeCurrency(typeCurrency);
        params.setExperienceLevel(level);
        world.pilotContracts().create(mission.getId(), 1, 1, baseFlightPay, baseIncome, params);
    }

    @Test
    void aFlightWithoutAContractHasNothingToShow() {
        final ManualPayDto dto = FlightDashboardController.buildManualPay(world, mission);

        assertFalse(dto.isHasContract());
        assertNull(dto.getByContract());
        assertNull(dto.getIfManual());
    }

    @Test
    void theContractPromisesMoreThanTheManualFlightPays() {
        // no type currency: 75% of the flight pay and 75% of the share (level 0 is 0.5%); manual: 8% of both, the floor is 10%
        contract(592, 10_445, 0, false);

        final ManualPayDto dto = FlightDashboardController.buildManualPay(world, mission);

        assertTrue(dto.isHasContract());
        assertEquals(444, dto.getByContract().getFlightPay());
        assertEquals(39, dto.getByContract().getIncomeShare());
        assertEquals(483, dto.getByContract().getTotal());
        assertEquals(59, dto.getIfManual().getFlightPay());
        assertEquals(5, dto.getIfManual().getIncomeShare());
        assertEquals(64, dto.getIfManual().getTotal());
    }

    @Test
    void aPilotWithEveryCurrencyLosesTwoThirdsByTheManualFlight() {
        contract(400, 20_000, 3, true);

        final ManualPayDto dto = FlightDashboardController.buildManualPay(world, mission);

        assertEquals(1000, dto.getByContract().getTotal());
        // 33% of the flight pay, 33% of the share
        assertEquals(132, dto.getIfManual().getFlightPay());
        assertEquals(198, dto.getIfManual().getIncomeShare());
        assertEquals(330, dto.getIfManual().getTotal());
    }

    @Test
    void askingDoesNotChangeTheContract() {
        contract(400, 20_000, 3, true);

        FlightDashboardController.buildManualPay(world, mission);

        assertFalse(world.pilotContracts().byFlightMissionId(mission.getId()).orElseThrow().getPayParams().isManual());
    }
}
