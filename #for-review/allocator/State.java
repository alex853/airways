/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage1.allocator;

public interface State {
    boolean isCompatibleWith(State state);
}
