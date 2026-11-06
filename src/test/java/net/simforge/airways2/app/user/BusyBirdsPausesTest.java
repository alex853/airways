package net.simforge.airways2.app.user;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The parameter "pauses" of the plans: the hours before the second leg, before the third leg and so on.
 */
class BusyBirdsPausesTest {
    @Test
    void thePausesAreTheHoursBeforeTheLegsFromTheSecondOne() {
        assertEquals(List.of(2.0, 1.0, 3.0), BusyBirdsController.pauses("2,1,3"));
        assertEquals(List.of(0.5, 24.0), BusyBirdsController.pauses(" 0.5 , 24 "));
    }

    @Test
    void withoutPausesTheListIsEmptyAndEveryLegGetsTheDefaultPause() {
        assertEquals(List.of(), BusyBirdsController.pauses(null));
        assertEquals(List.of(), BusyBirdsController.pauses(""));
    }

    @Test
    void aWrongPauseIsARefusal() {
        assertThrows(ResponseStatusException.class, () -> BusyBirdsController.pauses("2,abc"));
        assertThrows(ResponseStatusException.class, () -> BusyBirdsController.pauses("-1"));
        assertThrows(ResponseStatusException.class, () -> BusyBirdsController.pauses("49"));
    }
}
