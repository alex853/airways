package net.simforge.airways2.app.user;

import net.simforge.airways2.app.user.BusyBirdsController.SalaryDto;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PilotPay;
import net.simforge.airways2.world.computations.PilotPayParams;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.processors.BusyBirdsMissionControl;
import net.simforge.airways2.world.processors.BusyBirdsPilotPay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The pay of a leg for the page: the numbers and what they are made of, so the page can explain them.
 */
class BusyBirdsPlanDtoTest {
    private Airports.Airport from;
    private Airports.Airport to;

    @BeforeEach
    void beforeEach() {
        final World world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        from = world.airports().create(37.62, -122.38, "SFO", "KSFO", "San Francisco");
        to = world.airports().create(36.59, -115.67, "INS", "KINS", "Indian Springs");
    }

    private BusyBirdsMissionControl.Leg leg(final BusyBirdsMissionControl.Leg.Type type, final int durationSeconds, final int baseIncome, final PilotPayParams params) {
        final int baseFlightPay = BusyBirdsPilotPay.baseFlightPay("GA7C", durationSeconds);
        final BusyBirdsPilotPay.Salary salary = new BusyBirdsPilotPay.Salary(PilotPay.flightPay(baseFlightPay, params), PilotPay.incomeShare(baseIncome, params));
        return new BusyBirdsMissionControl.Leg(type, from, to, 400, 4, 0, durationSeconds, salary, BusyBirdsPilotPay.baseHourlyRate("GA7C"), baseIncome, params);
    }

    @Test
    void aRevenueLegHasTheHoursTheRateAndTheShare() {
        final PilotPayParams params = PilotPayParams.none();
        params.setNightCurrency(true);
        params.setExperienceLevel(0);
        // 51 minutes: 0.85 hours; no type currency and no pax: 50% of 500 is 250 an hour; the share is 0.5% with a quarter off
        final BusyBirdsMissionControl.Leg leg = leg(BusyBirdsMissionControl.Leg.Type.Revenue, 51 * 60, 10_445, params);

        final SalaryDto dto = SalaryDto.forLeg(leg);

        assertEquals(0.85, dto.getDurationHours(), 0.0001);
        assertEquals(250.0, dto.getHourlyRate(), 0.0001);
        assertEquals(10_445, dto.getBaseIncome());
        assertEquals(0.375, dto.getIncomeSharePercent(), 0.0001);
        // the numbers of the page are the numbers of the plan
        assertEquals(leg.getSalary().flightPay(), dto.getFlightPay());
        assertEquals(leg.getSalary().incomeShare(), dto.getIncomeShare());
        assertEquals(leg.getSalary().total(), dto.getTotal());
        // 500 * 0.85 = 425 base, 50% is 213 (212.5 half up); 10445 * 0.375% = 39
        assertEquals(213, dto.getFlightPay());
        assertEquals(39, dto.getIncomeShare());
    }

    @Test
    void aRepositionLegHasNoIncomePart() {
        final BusyBirdsMissionControl.Leg leg = leg(BusyBirdsMissionControl.Leg.Type.Reposition, 3600, 0, PilotPayParams.none());

        final SalaryDto dto = SalaryDto.forLeg(leg);

        assertNotNull(dto.getDurationHours());
        assertNotNull(dto.getHourlyRate());
        assertNull(dto.getBaseIncome());
        assertNull(dto.getIncomeSharePercent());
        assertEquals(0, dto.getIncomeShare());
    }

    @Test
    void theTotalOfAPlanHasNoDetails() {
        final SalaryDto dto = SalaryDto.from(new BusyBirdsPilotPay.Salary(100, 20));

        assertEquals(120, dto.getTotal());
        assertNull(dto.getDurationHours());
        assertNull(dto.getHourlyRate());
    }
}
