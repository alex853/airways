package net.simforge.airways.engine.proto;

/**
 * Created by Alexey on 17.07.2018.
 */
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
