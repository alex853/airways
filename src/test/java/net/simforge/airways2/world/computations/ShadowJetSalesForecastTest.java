package net.simforge.airways2.world.computations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShadowJetSalesForecastTest {
    private static final int HOUR = 3600;

    private static void assertLoad(final int from, final int to, final long secondsBeforeDeparture) {
        final ShadowJetSalesForecast.ExpectedLoad load = ShadowJetSalesForecast.expectedLoad(secondsBeforeDeparture);
        assertEquals(from, load.loadFromPercent(), "from, seconds before the departure " + secondsBeforeDeparture);
        assertEquals(to, load.loadToPercent(), "to, seconds before the departure " + secondsBeforeDeparture);
    }

    @Test
    void theSalesTeamPointsAreExact() {
        assertLoad(20, 25, HOUR);
        assertLoad(40, 50, 6 * HOUR);
        assertLoad(70, 80, 12 * HOUR);
        assertLoad(80, 100, 24 * HOUR);
    }

    @Test
    void nearerThanAnHourItIsTheFiguresOfAnHour() {
        assertLoad(20, 25, 30 * 60);
        assertLoad(20, 25, 0);
    }

    @Test
    void furtherThanADayItIsTheFiguresOfADay() {
        assertLoad(80, 100, 25 * HOUR);
        assertLoad(80, 100, 48 * HOUR);
    }

    @Test
    void betweenThePointsTheFiguresGoLinearly() {
        // half way from an hour to 6 hours: 3.5 hours, the least 20..40 is 30, the most 25..50 is 37.5 (rounded half up: 38)
        assertLoad(30, 38, 3 * HOUR + 30 * 60);
        // half way from 6 to 12 hours: 9 hours, 55 and 65
        assertLoad(55, 65, 9 * HOUR);
        // half way from 12 to 24 hours: 18 hours, 75 and 90
        assertLoad(75, 90, 18 * HOUR);
    }

    @Test
    void theLoadDoesNotFallWhenTheFlightIsBookedEarlier() {
        int from = 0;
        int to = 0;
        for (int minutes = 0; minutes <= 26 * 60; minutes += 5) {
            final ShadowJetSalesForecast.ExpectedLoad load = ShadowJetSalesForecast.expectedLoad(minutes * 60L);
            assertEquals(true, load.loadFromPercent() >= from, "from falls at " + minutes + " minutes");
            assertEquals(true, load.loadToPercent() >= to, "to falls at " + minutes + " minutes");
            assertEquals(true, load.loadFromPercent() <= load.loadToPercent(), "from is over to at " + minutes + " minutes");
            from = load.loadFromPercent();
            to = load.loadToPercent();
        }
    }
}
