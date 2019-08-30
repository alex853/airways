/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.engine.activity;

import net.simforge.airways.engine.Result;

public interface Activity {
    Result act();

    Result onExpiry();
}
