/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.engine.activity.ActivityInfo;
import net.simforge.airways.persistence.model.Journey;
import net.simforge.airways.processes.journey.activity.LookingForPersons;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;

public class JourneyLookingForTicketsTest extends BaseEngineCaseTest {

    private static final int GROUP_SIZE = 5;

    private Journey journey;

    protected void buildWorld() {
        TestWorld testWorld = new TestWorld(sessionFactory);
        testWorld.createGeo();

        journey = testWorld.createJourney(testWorld.getLondonCity(), testWorld.getManchesterCity(), GROUP_SIZE);

        // todo p2 create transport flight
    }

    @Test
    public void testCase() {
        engine.startActivity(LookingForPersons.class, journey);

        runEngine(1000);

        ActivityInfo status = engine.findActivity(LookingForPersons.class, journey);
        assertTrue(status.isFinished());

        // todo p1 finish test & write asserts
    }
}
