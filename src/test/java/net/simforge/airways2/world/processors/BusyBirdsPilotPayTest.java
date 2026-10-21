package net.simforge.airways2.world.processors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The base pay of a BusyBirds pilot for a leg: the base hourly rate of the type multiplied by the planned duration. Money is integer.
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
    public void test__base_flight_pay_is_the_rate_multiplied_by_the_hours() {
        assertEquals(200, BusyBirdsPilotPay.baseFlightPay("C25C", HOUR));
        assertEquals(400, BusyBirdsPilotPay.baseFlightPay("C25C", 2 * HOUR));
        assertEquals(750, BusyBirdsPilotPay.baseFlightPay("GA7C", HOUR + HOUR / 2));
        assertEquals(500, BusyBirdsPilotPay.baseFlightPay("GLF6", HOUR));
    }

    @Test
    public void test__base_flight_pay_is_rounded_to_integer() {
        // 1 h 20 min on 200 per hour is 266.67
        assertEquals(267, BusyBirdsPilotPay.baseFlightPay("C25C", HOUR + 20 * 60));
        // 10 minutes on 200 per hour is 33.33
        assertEquals(33, BusyBirdsPilotPay.baseFlightPay("C25C", 10 * 60));
    }

    @Test
    public void test__zero_duration_gives_zero() {
        assertEquals(0, BusyBirdsPilotPay.baseFlightPay("GA7C", 0));
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
