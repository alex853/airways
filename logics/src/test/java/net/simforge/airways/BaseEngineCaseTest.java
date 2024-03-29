package net.simforge.airways;

import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.ProcessEngineBuilder;
import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.entities.TaskEntity;
import net.simforge.airways.processengine.SimulatedTimeMachine;
import net.simforge.commons.hibernate.SessionFactoryBuilder;
import org.hibernate.SessionFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

public abstract class BaseEngineCaseTest {

    protected static SessionFactory sessionFactory;

    protected SimulatedTimeMachine timeMachine;
    protected ProcessEngine engine;
    protected ProcessEngineScheduling scheduling;

    @BeforeClass
    public static void beforeClass() {
        sessionFactory = SessionFactoryBuilder
                .forDatabase("test-h2")
                .createSchemaIfNeeded()
                .entities(Airways.entities)
                .entities(new Class[]{TaskEntity.class})
                .build();
    }

    @AfterClass
    public static void afterClass() {
        sessionFactory.close();
        sessionFactory = null;
    }

    @Before
    public void before() {
        timeMachine = new SimulatedTimeMachine(TestWorld.BEGINNING_OF_TIME);
        engine = ProcessEngineBuilder.create()
                .withTimeMachine(timeMachine)
                .withSessionFactory(sessionFactory)
                .build();
        scheduling = new ProcessEngineScheduling(sessionFactory, timeMachine);

        buildWorld();
    }

    @After
    public void after() {
    }

    protected abstract void buildWorld();

    @SuppressWarnings("SameParameterValue")
    protected void runEngine(int minutesToRun) {
        for (int i = 0; i < minutesToRun; i++) {
            timeMachine.plusMinutes(1); // todo AK this can be reworked using nothingToProcess

            // ten ticks for each minute
            for (int j = 0; j < 10; j++) {
                engine.tick();
            }
        }
    }

}
