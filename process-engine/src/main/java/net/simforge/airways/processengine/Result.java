/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processengine;

public class Result {
    private final Action action;
    private final When when;

    public enum Action {
        Resume,
        Done,
        Sleep,
        Nothing
    }

    public enum When {
        NextDay,
        NextHour,
        NextMinute,
        FewTimesPerDay,
        FewTimesPerHour
    }

    private Result(Action action, When when) {
        this.action = action;
        this.when = when;
    }

    public Action getAction() {
        return action;
    }

    public When getWhen() {
        return when;
    }

    public static Result resume(When when) {
        return new Result(Action.Resume, when);
    }

    /**
     * Activity goes to DONE status, expiry code will be skipped.
     */
    public static Result done() {
        return new Result(Action.Done, null);
    }

    /**
     * Activity remains in ACTIVE status, next run will be expiry time or no run if expiry time is not specified.
     */
    public static Result sleep() {
        return new Result(Action.Sleep, null);
    }

    public static Result nothing() {
        return new Result(Action.Nothing, null);
    }
}
