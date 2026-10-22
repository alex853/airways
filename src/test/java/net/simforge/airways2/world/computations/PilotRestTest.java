package net.simforge.airways2.world.computations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The awake credit: it falls while the pilot is awake, sleep fills it by two minutes for a minute, a short sleep gives nothing, the maximum is sixteen hours.
 */
class PilotRestTest {
    private static final int HOUR = 60;

    @Test
    void creditFallsOneToOneWhileAwake() {
        // fixed at 1000 s with 16 hours, three hours later
        assertEquals(13 * HOUR, PilotRest.creditNow(16 * HOUR, 1000, 1000 + 3 * 3600));
    }

    @Test
    void onlyWholeMinutesAreCounted() {
        assertEquals(960, PilotRest.creditNow(960, 0, 59));
        assertEquals(959, PilotRest.creditNow(960, 0, 60));
        assertEquals(959, PilotRest.creditNow(960, 0, 119));
    }

    @Test
    void creditGoesBelowZero() {
        // 1 hour left, 3 hours later: two hours overtired
        assertEquals(-2 * HOUR, PilotRest.creditNow(HOUR, 0, 3 * 3600));
    }

    @Test
    void eightHoursOfSleepGiveSixteenHours() {
        assertEquals(16 * HOUR, PilotRest.creditAfterSleep(0, 8 * HOUR));
    }

    @Test
    void sleepShorterThanTwoHoursGivesNothingTwoHoursGiveFour() {
        assertEquals(5 * HOUR, PilotRest.creditAfterSleep(5 * HOUR, HOUR));
        assertEquals(5 * HOUR, PilotRest.creditAfterSleep(5 * HOUR, 119));
        assertEquals(9 * HOUR, PilotRest.creditAfterSleep(5 * HOUR, 2 * HOUR));
    }

    @Test
    void sleepOfTwentyMinutesDoesNotMakeTheDayLonger() {
        assertEquals(100, PilotRest.creditAfterSleep(100, 20));
    }

    @Test
    void creditHasNoMaximumLongSleepStillGivesTwoForOne() {
        // 9.5 hours of sleep are 19 hours of the credit, on top of what the pilot had
        assertEquals(19 * HOUR, PilotRest.creditAfterSleep(0, 9 * HOUR + 30));
        assertEquals(30 * HOUR, PilotRest.creditAfterSleep(10 * HOUR, 10 * HOUR));
    }

    @Test
    void theWakeUpByItselfIsFromSevenAndAHalfToTenHours() {
        assertEquals(7 * HOUR + 30, PilotRest.AUTO_WAKE_UP_MIN_MINUTES);
        assertEquals(10 * HOUR, PilotRest.AUTO_WAKE_UP_MAX_MINUTES);
    }

    @Test
    void overtirednessIsRepaidBySleep() {
        // minus three hours and four hours of sleep: plus five hours
        assertEquals(5 * HOUR, PilotRest.creditAfterSleep(-3 * HOUR, 4 * HOUR));
    }

    @Test
    void theTwoBytesOfTheStorageAreNotExceeded() {
        // two months awake from a spent credit: it is cut at the two bytes, not wrapped around
        assertEquals(Short.MIN_VALUE, PilotRest.creditNow(0, 0, 60 * 60 * 24 * 60));
    }

    @Test
    void aDebtOfThreeHoursAddsAnHourOfSleep() {
        assertEquals(HOUR, PilotRest.extraSleepMinutes(-3 * HOUR));
    }

    @Test
    void aDebtOfAnHourAndAHalfAddsHalfAnHour() {
        assertEquals(30, PilotRest.extraSleepMinutes(-90));
    }

    @Test
    void theDebtAboveThreeHoursAddsNothingMore() {
        assertEquals(HOUR, PilotRest.extraSleepMinutes(-5 * HOUR));
        assertEquals(HOUR, PilotRest.extraSleepMinutes(-1000));
        assertEquals(HOUR, PilotRest.extraSleepMinutes(Short.MIN_VALUE));
    }

    @Test
    void noDebtAddsNothing() {
        assertEquals(0, PilotRest.extraSleepMinutes(0));
        assertEquals(0, PilotRest.extraSleepMinutes(1));
        assertEquals(0, PilotRest.extraSleepMinutes(16 * HOUR));
    }

    @Test
    void theExtraSleepIsAThirdOfTheDebtInMinutes() {
        assertEquals(1, PilotRest.extraSleepMinutes(-3));
        assertEquals(0, PilotRest.extraSleepMinutes(-2));
        assertEquals(20, PilotRest.extraSleepMinutes(-60));
    }
}
