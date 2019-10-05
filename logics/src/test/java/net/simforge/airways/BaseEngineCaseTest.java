/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.EngineBuilder;
import net.simforge.airways.processengine.entities.TaskEntity;
import net.simforge.airways.persistence.Airways;
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
        engine = EngineBuilder.create()
                .withTimeMachine(timeMachine)
                .withSessionFactory(sessionFactory)
                .build();

        buildWorld();
    }

    @After
    public void after() {
    }

    protected abstract void buildWorld();

    @SuppressWarnings("SameParameterValue")
    protected void runEngine(int minutesToRun) {
        for (int i = 0; i < minutesToRun; i++) {
            timeMachine.plusMinutes(1);

            // ten ticks for each minute
            for (int j = 0; j < 10; j++) {
                engine.tick();
            }
        }
    }

}
