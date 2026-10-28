package net.simforge.airways2.app.user;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The parameter "intervals" of the plans: the hours before every leg.
 */
class BusyBirdsLegGapsTest {
    @Test
    void theIntervalsAreTheHoursBeforeEveryLeg() {
        assertEquals(List.of(2.0, 1.0, 3.0), BusyBirdsController.legGaps("2,1,3", 1));
        assertEquals(List.of(0.5, 24.0), BusyBirdsController.legGaps(" 0.5 , 24 ", 1));
    }

    @Test
    void withoutIntervalsTheFirstLegIsInHalfAnHourAndTheOthersFollowTheOldTurnaroundTime() {
        assertEquals(List.of(0.5, 3.0, 3.0), BusyBirdsController.legGaps(null, 3));
        assertEquals(List.of(0.5, 3.0, 3.0), BusyBirdsController.legGaps("", 3));
    }

    @Test
    void aWrongIntervalIsARefusal() {
        assertThrows(ResponseStatusException.class, () -> BusyBirdsController.legGaps("2,abc", 1));
        assertThrows(ResponseStatusException.class, () -> BusyBirdsController.legGaps("-1", 1));
        assertThrows(ResponseStatusException.class, () -> BusyBirdsController.legGaps("49", 1));
    }
}
