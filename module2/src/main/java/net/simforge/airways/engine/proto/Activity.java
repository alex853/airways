package net.simforge.airways.engine.proto;

/**
 * Created by Alexey on 17.07.2018.
 */
public interface Activity<T> {
    ActivityResult act(T object);
}
