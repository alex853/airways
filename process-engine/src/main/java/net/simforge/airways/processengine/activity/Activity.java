/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processengine.activity;

import net.simforge.airways.processengine.Result;

public interface Activity {
    Result act();

    Result onExpiry();
}
