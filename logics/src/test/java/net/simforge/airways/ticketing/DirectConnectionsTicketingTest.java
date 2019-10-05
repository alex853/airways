/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.ticketing;

import net.simforge.airways.TestWorld;
import net.simforge.airways.Airways;
import net.simforge.airways.persistence.model.journey.Journey;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processengine.SimulatedTimeMachine;
import net.simforge.airways.processengine.TimeMachine;
import net.simforge.commons.hibernate.SessionFactoryBuilder;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.*;

import java.util.List;

import static junit.framework.TestCase.*;
import static net.simforge.airways.TestWorld.BEGINNING_OF_TIME;

public class DirectConnectionsTicketingTest {

    private SessionFactory sessionFactory;
    private TimeMachine timeMachine;
    private TestWorld testWorld;
    private TransportFlight ab101_today;

    @Before
    public void before() {
        sessionFactory = SessionFactoryBuilder
                .forDatabase("test-h2")
                .createSchemaIfNeeded()
                .entities(Airways.entities)
                .build();

        buildWorld();
    }

    @After
    public void after() {
        sessionFactory.close();
        sessionFactory = null;
    }

    protected void buildWorld() {
        testWorld = new TestWorld(sessionFactory);
        testWorld.createGeo();
        testWorld.createAircraftTypes();

        ab101_today = testWorld.createTransportFlight(
                "AB101",
                testWorld.getEgllAirport(),
                testWorld.getEgccAirport(),
                testWorld.getA320Type(),
                BEGINNING_OF_TIME.plusHours(12),
                160);

        timeMachine = new SimulatedTimeMachine(BEGINNING_OF_TIME);
    }

    @Test
    public void test_ok() {
        Journey london2manchester = testWorld.createJourney(testWorld.getLondonCity(), testWorld.getManchesterCity(), 5);

        try (Session session = sessionFactory.openSession()) {
            List<TransportFlight> flights = DirectConnectionsTicketing.search(TicketingRequest.get(london2manchester, session, timeMachine));
            assertNotNull(flights);
            assertEquals(1, flights.size());
            assertEquals(ab101_today.getId(), flights.get(0).getId());
        }
    }

    @Test
    public void test_noRoute() {
        Journey london2dublin = testWorld.createJourney(testWorld.getLondonCity(), testWorld.getDublinCity(), 5);

        try (Session session = sessionFactory.openSession()) {
            List<TransportFlight> flights = DirectConnectionsTicketing.search(TicketingRequest.get(london2dublin, session, timeMachine));
            assertNotNull(flights);
            assertEquals(0, flights.size());
        }
    }
}
