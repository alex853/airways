package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.world.Time;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The way the storages keep a date and the times of that date: the date is the number of days from 2024-12-31 (two bytes, about 179 years,
 * 0 means no date), a time is 12 bits of minutes from the midnight (UTC) of that date with the offset 1000 (0 means no time). The window of
 * a time is from minus 16 hours 40 minutes to plus 49 hours 59 minutes around the midnight of the date, the seconds are dropped.
 * <p>
 * Two times of the date are kept in one 24-bit field, as two sections of 12 bits.
 */
final class DateMinutes {
    static final LocalDate DAY_BEFORE_FIRST_DAY = LocalDate.of(2024, 12, 31);

    static final int TIME_BITS = 12;
    private static final int OFFSET = 1000;
    private static final int MAX_DAYS = 65535;

    private DateMinutes() {
    }

    /**
     * @return days from 2024-12-31, 0 for no date
     */
    static int toDays(final LocalDate date) {
        if (date == null) {
            return 0;
        }
        final long days = ChronoUnit.DAYS.between(DAY_BEFORE_FIRST_DAY, date);
        checkArgument(1 <= days && days <= MAX_DAYS, "the date is out of the range of two bytes of days: " + date);
        return (int) days;
    }

    static LocalDate fromDays(final int days) {
        if (days == 0) {
            return null;
        }
        return DAY_BEFORE_FIRST_DAY.plusDays(days);
    }

    /**
     * @return the value of 12 bits (minutes from the midnight of the date plus 1000), 0 if there is no date or no time
     */
    static int toStored(final LocalDate date, final int worldTime) {
        if (date == null || worldTime == 0) {
            return 0;
        }
        final int minutes = ((int) Duration.between(date.atStartOfDay(), Time.toLdt(worldTime)).getSeconds()) / 60;
        checkArgument(-OFFSET < minutes && minutes < 3000, "the time is out of the window of the date: date " + date + ", minutes " + minutes);
        return minutes + OFFSET;
    }

    /**
     * @return world time (seconds), 0 if there is no date or no time
     */
    static int fromStored(final LocalDate date, final int stored) {
        if (date == null || stored == 0) {
            return 0;
        }
        final int minutes = stored - OFFSET;
        checkArgument(-OFFSET < minutes && minutes < 3000);
        return Time.fromLdt(date.atStartOfDay().plusMinutes(minutes));
    }
}
