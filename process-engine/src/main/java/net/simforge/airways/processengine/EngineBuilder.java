/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processengine;

import net.simforge.airways.util.TimeMachine;
import org.hibernate.SessionFactory;

public class EngineBuilder {
    private ProcessEngine engine;

    private EngineBuilder() {
        engine = new ProcessEngine();
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

    public ProcessEngine build() {
        checkEngine();

        ProcessEngine engine = this.engine;
        this.engine = null;
        return engine;
    }

    private void checkEngine() {
        if (engine == null) {
            throw new IllegalStateException("ProcessEngine already built");
        }
    }
}
