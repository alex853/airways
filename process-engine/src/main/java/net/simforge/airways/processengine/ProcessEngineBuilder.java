package net.simforge.airways.processengine;

import org.hibernate.SessionFactory;

public class ProcessEngineBuilder {
    private ProcessEngine engine;

    private ProcessEngineBuilder() {
        engine = new ProcessEngine();
    }

    public static ProcessEngineBuilder create() {
        return new ProcessEngineBuilder();
    }

    public ProcessEngineBuilder withTimeMachine(TimeMachine timeMachine) {
        checkEngine();

        engine.timeMachine = timeMachine;
        return this;
    }

    public ProcessEngineBuilder withSessionFactory(SessionFactory sessionFactory) {
        checkEngine();

        engine.sessionFactory = sessionFactory;
        return this;
    }

    public ProcessEngine build() {
        checkEngine();

        ProcessEngine engine = this.engine;
        this.engine = null;
        engine.scheduling = new ProcessEngineScheduling(engine.sessionFactory, engine.timeMachine);
        return engine;
    }

    private void checkEngine() {
        if (engine == null) {
            throw new IllegalStateException("ProcessEngine already built");
        }
    }
}
