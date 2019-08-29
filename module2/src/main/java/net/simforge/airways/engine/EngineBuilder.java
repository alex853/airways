package net.simforge.airways.engine;

import net.simforge.airways.util.TimeMachine;
import org.hibernate.SessionFactory;

public class EngineBuilder {
    private Engine engine;

    private EngineBuilder() {
        engine = new Engine();
    }

    public static EngineBuilder create() {
        return new EngineBuilder();
    }

    public EngineBuilder withTimeMachine(TimeMachine timeMachine) {
        checkEngine();

        engine.timeMachine = timeMachine;
        return this;
    }

    public EngineBuilder withSessionFactory(SessionFactory sessionFactory) {
        checkEngine();

        engine.sessionFactory = sessionFactory;
        return this;
    }

    public Engine build() {
        checkEngine();

        Engine engine = this.engine;
        this.engine = null;
        return engine;
    }

    private void checkEngine() {
        if (engine == null) {
            throw new IllegalStateException("Engine already built");
        }
    }
}
