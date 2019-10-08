/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.ops.AircraftOps;
import net.simforge.airways.ops.PersonOps;
import net.simforge.airways.ops.PilotOps;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.journey.Journey;
import net.simforge.airways.persistence.model.Pilot;
import net.simforge.airways.persistence.model.aircraft.Aircraft;
import net.simforge.airways.persistence.model.flight.TimetableRow;
import net.simforge.airways.processes.journey.activity.LookingForPersons;
import net.simforge.airways.processes.timetablerow.activity.ScheduleFlight;
import org.hibernate.Session;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class IntegralFlightTest extends BaseEngineCaseTest {
    private TestWorld testWorld;
    private Journey journey1;
    private Journey journey2;
    private Journey journey3;
    private TimetableRow timetableRow;

    @Override
    protected void buildWorld() {
        testWorld = new TestWorld(sessionFactory);
        testWorld.createGeo();

        for (int i = 0; i < 10; i++) {
            testWorld.createPerson(testWorld.getLondonCity());
        }

        journey1 = testWorld.createJourney(testWorld.getLondonCity(), testWorld.getManchesterCity(), 1);
        journey2 = testWorld.createJourney(testWorld.getLondonCity(), testWorld.getManchesterCity(), 2);
        journey3 = testWorld.createJourney(testWorld.getLondonCity(), testWorld.getManchesterCity(), 3);

        testWorld.createAircraftTypes();

        try (Session session = sessionFactory.openSession()) {
            AircraftOps.addAircrafts(session, "AB", "A320", "EGLL", "G-BA??", 1);

            PilotOps.addPilots(session, "United kingdom", "London", "EGLL", 1);
        }

        timetableRow = testWorld.createTimetableRow("AB101", testWorld.getEgllAirport(), testWorld.getEgccAirport(), "12:00", testWorld.getA320Type());
    }

    @Test
    public void testCase() {
        engine.startActivity(ScheduleFlight.class, timetableRow);

        engine.startActivity(LookingForPersons.class, journey1);
        engine.startActivity(LookingForPersons.class, journey2);
        engine.startActivity(LookingForPersons.class, journey3);

        runEngine(1000);

        try (Session session = sessionFactory.openSession()) {

            Aircraft aircraft = AircraftOps.loadByRegNo(session,"G-BAAA");
            assertEquals("EGCC", aircraft.getLocationAirport().getIcao());
            assertEquals(Aircraft.Status.Idle, aircraft.getStatus().intValue());

            Pilot pilot = PilotOps.loadAllPilots(session).get(0);
            assertEquals("EGCC", pilot.getPerson().getLocationAirport().getIcao());
            assertEquals(Pilot.Status.Idle, pilot.getStatus().intValue());

            Assert.assertEquals(4, PersonOps.loadOrdinalPersonsByLocationCity(session, testWorld.getLondonCity()).size());

            List<Person> peopleOfManchester = PersonOps.loadOrdinalPersonsByLocationCity(session, testWorld.getManchesterCity());
            assertEquals(6, peopleOfManchester.size());
            for (Person person : peopleOfManchester) {
                assertEquals(Person.Status.Idle, person.getStatus().intValue());
                assertNull(person.getJourney());
            }

            journey1 = session.load(Journey.class, journey1.getId());
            journey2 = session.load(Journey.class, journey2.getId());
            journey3 = session.load(Journey.class, journey3.getId());
            assertEquals(Journey.Status.Finished, journey1.getStatus().intValue());
            assertEquals(Journey.Status.Finished, journey2.getStatus().intValue());
            assertEquals(Journey.Status.Finished, journey3.getStatus().intValue());
            assertNull(journey1.getItinerary());
            assertNull(journey2.getItinerary());
            assertNull(journey3.getItinerary());

        }
    }
}
