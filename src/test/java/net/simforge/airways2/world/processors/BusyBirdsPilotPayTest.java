package net.simforge.airways2.world.processors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The pay of a BusyBirds pilot for a leg: the base hourly rate of the type multiplied by the planned duration, plus 3% of the income
 * of the mission on the revenue leg. Money is integer.
 */
class BusyBirdsPilotPayTest {
    private static final int HOUR = 3600;

    @Test
    public void test__base_hourly_rates_of_the_busybirds_types() {
        assertEquals(200, BusyBirdsPilotPay.baseHourlyRate("C25C"));
        assertEquals(500, BusyBirdsPilotPay.baseHourlyRate("GA7C"));
        assertEquals(500, BusyBirdsPilotPay.baseHourlyRate("GLF6"));
    }

    @Test
    public void test__unknown_type_gets_the_cheapest_rate() {
        assertEquals(200, BusyBirdsPilotPay.baseHourlyRate("A320"));
    }

    @Test
    public void test__income_share_is_3_percent() {
        assertEquals(3, BusyBirdsPilotPay.INCOME_SHARE_PERCENT);
    }

    @Test
    public void test__flight_pay_is_the_rate_multiplied_by_the_hours() {
        assertEquals(200, BusyBirdsPilotPay.calcLegSalary("C25C", HOUR, false, 0).flightPay());
        assertEquals(400, BusyBirdsPilotPay.calcLegSalary("C25C", 2 * HOUR, false, 0).flightPay());
        assertEquals(750, BusyBirdsPilotPay.calcLegSalary("GA7C", HOUR + HOUR / 2, false, 0).flightPay());
        assertEquals(500, BusyBirdsPilotPay.calcLegSalary("GLF6", HOUR, false, 0).flightPay());
    }

    @Test
    public void test__flight_pay_is_rounded_to_integer() {
        // 1 h 20 min on 200 per hour is 266.67
        assertEquals(267, BusyBirdsPilotPay.calcLegSalary("C25C", HOUR + 20 * 60, false, 0).flightPay());
        // 10 minutes on 200 per hour is 33.33
        assertEquals(33, BusyBirdsPilotPay.calcLegSalary("C25C", 10 * 60, false, 0).flightPay());
    }

    @Test
    public void test__reposition_leg_has_no_income_share_even_if_the_mission_has_an_income() {
        final BusyBirdsPilotPay.Salary salary = BusyBirdsPilotPay.calcLegSalary("C25C", HOUR, false, 100_000);

        assertEquals(0, salary.incomeShare());
        assertEquals(200, salary.total());
    }

    @Test
    public void test__revenue_leg_gets_3_percent_of_the_income() {
        final BusyBirdsPilotPay.Salary salary = BusyBirdsPilotPay.calcLegSalary("C25C", 2 * HOUR, true, 100_000);

        assertEquals(400, salary.flightPay());
        assertEquals(3000, salary.incomeShare());
        assertEquals(3400, salary.total());
    }

    @Test
    public void test__income_share_is_rounded_to_integer() {
        assertEquals(2, BusyBirdsPilotPay.calcLegSalary("C25C", HOUR, true, 50).incomeShare()); // 1.5, half up
        assertEquals(1, BusyBirdsPilotPay.calcLegSalary("C25C", HOUR, true, 49).incomeShare()); // 1.47
        assertEquals(0, BusyBirdsPilotPay.calcLegSalary("C25C", HOUR, true, 10).incomeShare()); // 0.3
    }

    @Test
    public void test__zero_duration_and_zero_income_give_zero() {
        assertEquals(BusyBirdsPilotPay.Salary.ZERO, BusyBirdsPilotPay.calcLegSalary("GA7C", 0, true, 0));
    }

    @Test
    public void test__salary_sums_up_by_parts() {
        final BusyBirdsPilotPay.Salary sum = new BusyBirdsPilotPay.Salary(200, 0)
                .plus(new BusyBirdsPilotPay.Salary(400, 3000))
                .plus(new BusyBirdsPilotPay.Salary(400, 0));

        assertEquals(1000, sum.flightPay());
        assertEquals(3000, sum.incomeShare());
        assertEquals(4000, sum.total());
    }
}
