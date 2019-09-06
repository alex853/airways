/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage2.flightallocation;

public class FlyingState implements State {
    @Override
    public boolean isCompatibleWith(State state) {
        return false;
    }

    @Override
    public String toString() {
        return "FlyingState{}";
    }
}
