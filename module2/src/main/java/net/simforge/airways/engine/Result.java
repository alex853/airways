/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

package net.simforge.airways.engine;

public class Result {
    private final NextRun nextRun;

    public enum NextRun {
        NotSpecified,
        NextDay,
        NextHour,
        NextMinute,
        FewTimesPerDay
    }

    private Result(NextRun nextRun) {
        this.nextRun = nextRun;
    }

    public NextRun getNextRun() {
        return nextRun;
    }

    public static Result ok() {
        return ok(NextRun.NotSpecified);
    }

    public static Result ok(NextRun nextRun) {
        return new Result(nextRun);
    }
}
