package net.simforge.airways2.app.pblc;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Not more than one reading of the log of G7 an hour from one address. A refused request does not move the hour: the next one is allowed an hour after the last
 * allowed one. The addresses which are over are forgotten, the map does not grow.
 */
class G7ReadLimiter {
    static final Duration PERIOD = Duration.ofHours(1);

    private final Map<String, Instant> lastAllowed = new HashMap<>();

    /**
     * Zero if the request is allowed (and it is counted), otherwise the number of seconds to wait.
     */
    synchronized long acquire(final String ip, final Instant now) {
        lastAllowed.values().removeIf(last -> !last.plus(PERIOD).isAfter(now));

        final Instant last = lastAllowed.get(ip);
        if (last != null) {
            return Math.max(1, Duration.between(now, last.plus(PERIOD)).toSeconds());
        }
        lastAllowed.put(ip, now);
        return 0;
    }
}
