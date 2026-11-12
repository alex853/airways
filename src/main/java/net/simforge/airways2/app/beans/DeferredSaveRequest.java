package net.simforge.airways2.app.beans;

/**
 * A request to save the world a while later, not at once: the changes which come one after another (an airport, then a city 30 seconds later) are saved
 * by one save. The save is due the delay after the FIRST request; the requests which come while it waits do not move it. The time is given by the caller
 * (milliseconds), so the logic is testable.
 */
public class DeferredSaveRequest {
    public static final long DELAY_MILLIS = 5 * 60 * 1000L;

    private final long delayMillis;
    // zero is 'no request'
    private volatile long dueAtMillis;

    public DeferredSaveRequest() {
        this(DELAY_MILLIS);
    }

    DeferredSaveRequest(final long delayMillis) {
        this.delayMillis = delayMillis;
    }

    /** @return true if the request is new, false if the save was requested already and is waiting */
    public synchronized boolean request(final long nowMillis) {
        if (dueAtMillis != 0) {
            return false;
        }
        dueAtMillis = nowMillis + delayMillis;
        return true;
    }

    public boolean isDue(final long nowMillis) {
        final long due = dueAtMillis;
        return due != 0 && nowMillis >= due;
    }

    public boolean isPending() {
        return dueAtMillis != 0;
    }

    /** The world is saved (by this request or by any other save): the request is done. */
    public synchronized void clear() {
        dueAtMillis = 0;
    }
}
