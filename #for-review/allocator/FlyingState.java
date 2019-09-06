/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage1.allocator;

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
