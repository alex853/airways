package net.simforge.airways2.app.beans;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredSaveRequestTest {
    private static final long MINUTE = 60_000L;

    @Test
    void theDefaultDelayIsFiveMinutes() {
        assertTrue(DeferredSaveRequest.DELAY_MILLIS == 5 * MINUTE);
    }

    @Test
    void nothingIsDueWithoutARequest() {
        final DeferredSaveRequest request = new DeferredSaveRequest();

        assertFalse(request.isPending());
        assertFalse(request.isDue(Long.MAX_VALUE));
    }

    @Test
    void theSaveIsDueTheDelayAfterTheRequest() {
        final DeferredSaveRequest request = new DeferredSaveRequest();
        final long now = 1_000_000L;

        assertTrue(request.request(now));

        assertTrue(request.isPending());
        assertFalse(request.isDue(now));
        assertFalse(request.isDue(now + 5 * MINUTE - 1));
        assertTrue(request.isDue(now + 5 * MINUTE));
    }

    @Test
    void theRequestsWhichComeWhileItWaitsDoNotMoveTheSave() {
        final DeferredSaveRequest request = new DeferredSaveRequest();
        final long now = 1_000_000L;
        assertTrue(request.request(now));

        // a city 30 seconds after the airport: the same save
        assertFalse(request.request(now + 30_000));

        assertTrue(request.isDue(now + 5 * MINUTE));
    }

    @Test
    void afterTheSaveANewRequestStartsANewWait() {
        final DeferredSaveRequest request = new DeferredSaveRequest();
        final long now = 1_000_000L;
        request.request(now);
        request.clear();

        assertFalse(request.isPending());
        assertFalse(request.isDue(now + 10 * MINUTE));
        assertTrue(request.request(now + 10 * MINUTE));
        assertFalse(request.isDue(now + 10 * MINUTE));
        assertTrue(request.isDue(now + 15 * MINUTE));
    }
}
