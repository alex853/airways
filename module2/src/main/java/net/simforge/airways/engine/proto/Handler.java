package net.simforge.airways.engine.proto;

/**
 * Created by Alexey on 17.07.2018.
 */
public interface Handler<T> {
    void process(T object);
}
