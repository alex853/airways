package net.simforge.airways.processengine;

import net.simforge.commons.io.Marker;
import net.simforge.commons.misc.JavaTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public class SemiRealTimeMachine implements TimeMachine {

    private static final Logger log = LoggerFactory.getLogger(SemiRealTimeMachine.class);

    private final Marker marker = new Marker("semi-real-time-machine");
    private boolean isCatchingUp;
    private LocalDateTime now;
    private LocalDateTime markerDate;

    public SemiRealTimeMachine() {
        final LocalDateTime loaded = loadDate();
        final LocalDateTime now = JavaTime.nowUtc();
        if (loaded != null && JavaTime.hoursBetween(loaded, now) > 5 / 60.0) {
            isCatchingUp = true;
            this.now = loaded;
            this.markerDate = loaded;
            log.warn("CATCHING UP MODE - now is {}, marker {}", now, markerDate);
        } else {
            isCatchingUp = false;
            this.now = now;
            this.markerDate = now;
        }
    }

    @Override
    public LocalDateTime now() {
        if (!isCatchingUp) {
            now = JavaTime.nowUtc();
        }
        saveIfNeeded();
        return now;
    }

    @Override
    public void nothingToProcess() {
        if (isCatchingUp) {
            now = now.plusMinutes(1);
            log.warn("CATCHING UP MODE - now is {}, marker {}", now, markerDate);

            if (now.isAfter(JavaTime.nowUtc())) {
                now = JavaTime.nowUtc();
                isCatchingUp = false;
                log.warn("CATCHING UP MODE COMPLETED - now is {}, marker {}", now, markerDate);
            }
        }
        saveIfNeeded();
    }

    private void saveIfNeeded() {
        if (JavaTime.hoursBetween(markerDate, now) >= 10 / 60.0) {
            final Date date = Date.from(now.atZone(ZoneId.systemDefault()).toInstant());
            marker.setDate(date);
            markerDate = now;
        }
    }

    private LocalDateTime loadDate() {
        final Date date = marker.getDate();
        if (date == null) {
            return null;
        }
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
