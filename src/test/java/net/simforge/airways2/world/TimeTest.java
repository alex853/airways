package net.simforge.airways2.world;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class TimeTest {
    @Test
    public void toLdt__throws_when_time_is_zero() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Time.toLdt(0));
    }

    @Test
    public void toLdt__works_fine_for_now() {
        final int now = Time.now();

        assertDoesNotThrow(() -> Time.toLdt(now));
    }

    @Test
    public void toLdtOrNull__when_time_is_zero__returns_null() {
        assertNull(Time.toLdtOrNull(0));
    }

    @Test
    public void toLdt__then_fromLdt__should_be_equal() {
        final int now = Time.now();
        final LocalDateTime ldt = Time.toLdt(now);
        final int now2 = Time.fromLdt(ldt);

        assertEquals(now, now2);
    }

}
