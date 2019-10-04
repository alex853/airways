/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.engine.activity.ActivityInfo;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.persistence.model.Journey;
import net.simforge.airways.persistence.model.JourneyItinerary;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processes.journey.activity.LookingForPersons;
import net.simforge.airways.processes.journey.activity.LookingForTickets;
import org.hibernate.Session;
import org.junit.Test;

import java.util.List;

import static net.simforge.airways.TestWorld.BEGINNING_OF_TIME;
import static org.junit.Assert.*;

public class JourneyLookingForTicketsTest extends BaseEngineCaseTest {

    private static final int GROUP_SIZE = 5;

    private Journey journey;
    private TransportFlight ab101_today;

    protected void buildWorld() {
        TestWorld testWorld = new TestWorld(sessionFactory);
        testWorld.createGeo();

        journey = testWorld.createJourney(testWorld.getLondonCity(), testWorld.getManchesterCity(), GROUP_SIZE);

        testWorld.createAircraftTypes();

        ab101_today = testWorld.createTransportFlight(
                "AB101",
                testWorld.getEgllAirport(),
                testWorld.getEgccAirport(),
                testWorld.getA320Type(),
                BEGINNING_OF_TIME.plusHours(12),
                160);
    }

    @Test
    public void testCase() {
        engine.startActivity(LookingForPersons.class, journey);

        runEngine(10);

        ActivityInfo status = engine.findActivity(LookingForTickets.class, journey);
        assertTrue(status.isDone());

        try (Session session = sessionFactory.openSession()) {
            journey = session.load(Journey.class, journey.getId());
            assertEquals(Journey.Status.WaitingForFlight, journey.getStatus().intValue());

            List<JourneyItinerary> itineraryList = JourneyOps.getItineraries(session, journey);
            assertEquals(1, itineraryList.size());
            assertEquals(ab101_today.getId(), itineraryList.get(0).getFlight().getId());
        }
    }
}
