/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.engine.proto;

@Deprecated
public class ActivityResult {
    public static final ActivityResult REPEAT_DAILY = null;
    public static final ActivityResult REPEAT_SHORTLY_NONMAJOR_ERROR = null;

    public static ActivityResult done() {
        return null;
    }

    public static ActivityResult repeat() {
        return null;
    }

    public static ActivityResult repeatShortly() {
        return null;
    }
}
