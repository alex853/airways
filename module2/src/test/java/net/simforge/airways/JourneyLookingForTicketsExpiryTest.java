/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.engine.activity.ActivityInfo;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.persistence.model.journey.Journey;
import net.simforge.airways.persistence.model.journey.JourneyItinerary;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.processes.journey.activity.LookingForPersons;
import net.simforge.airways.processes.journey.activity.LookingForTickets;
import org.hibernate.Session;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class JourneyLookingForTicketsExpiryTest extends BaseEngineCaseTest {

    private static final int GROUP_SIZE = 5;

    private Journey journey;

    protected void buildWorld() {
        TestWorld testWorld = new TestWorld(sessionFactory);
        testWorld.createGeo();

        journey = testWorld.createJourney(testWorld.getLondonCity(), testWorld.getManchesterCity(), GROUP_SIZE);

        testWorld.createAircraftTypes();

        /* no flight created */
    }

    @Test
    public void testCase() {
        engine.startActivity(LookingForPersons.class, journey);

        runEngine(1440*7 + 10);

        ActivityInfo status = engine.findActivity(LookingForTickets.class, journey);
        assertTrue(status.isExpired());

        try (Session session = sessionFactory.openSession()) {
            journey = session.load(Journey.class, journey.getId());
            assertEquals(Journey.Status.CouldNotFindTickets, journey.getStatus().intValue());

            List<Person> persons = JourneyOps.getPersons(session, journey);
            assertEquals(0, persons.size());

            List<JourneyItinerary> itineraryList = JourneyOps.getItineraries(session, journey);
            assertEquals(0, itineraryList.size());
        }
    }
}
