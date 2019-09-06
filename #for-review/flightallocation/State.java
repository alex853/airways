/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage2.flightallocation;

public interface State {
    boolean isCompatibleWith(State state);
}
