/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.engine.event;

@Deprecated
public class EventInfo {
    public static EventInfo currentEvent() { // thread local
        throw new UnsupportedOperationException("EventInfo.currentEvent");
    }
}
