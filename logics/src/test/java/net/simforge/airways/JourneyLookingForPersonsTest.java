/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.processengine.activity.ActivityInfo;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.model.journey.Journey;
import net.simforge.airways.model.Person;
import net.simforge.airways.processes.journey.activity.LookingForPersons;
import org.hibernate.Session;
import org.junit.Test;

import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class JourneyLookingForPersonsTest extends BaseEngineCaseTest {

    private static final int GROUP_SIZE = 5;

    private Journey journey;

    @Override
    protected void buildWorld() {
        TestWorld testWorld = new TestWorld(sessionFactory, timeMachine);
        testWorld.createGeo();

        journey = testWorld.createJourney(testWorld.getLondonCity(), testWorld.getManchesterCity(), GROUP_SIZE);
    }

    @Test
    public void testCase() {
        engine.startActivity(LookingForPersons.class, journey);

        runEngine(1);

        ActivityInfo status = engine.findActivity(LookingForPersons.class, journey);
        assertTrue(status.isDone());

        try (Session session = sessionFactory.openSession()) {
            List<Person> persons = JourneyOps.getPersons(session, journey);
            assertEquals(GROUP_SIZE, persons.size());
        }
    }
}
