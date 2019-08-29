package net.simforge.airways.engine.activity;

import net.simforge.airways.engine.Result;

public interface Activity {
    Result act();

    Result afterExpired();
}
