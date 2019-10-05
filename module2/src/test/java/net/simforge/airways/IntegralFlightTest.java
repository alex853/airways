/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.ops.AircraftOps;
import net.simforge.airways.ops.PersonOps;
import net.simforge.airways.ops.PilotOps;
import net.simforge.airways.persistence.model.Journey;
import net.simforge.airways.persistence.model.Pilot;
import net.simforge.airways.persistence.model.aircraft.Aircraft;
import net.simforge.airways.persistence.model.flight.TimetableRow;
import net.simforge.airways.processes.journey.activity.LookingForPersons;
import net.simforge.airways.processes.timetablerow.activity.ScheduleFlight;
import org.hibernate.Session;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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

            assertEquals(4, PersonOps.loadOrdinalPersonsByLocationCity(session, testWorld.getLondonCity()).size());
            assertEquals(6, PersonOps.loadOrdinalPersonsByLocationCity(session, testWorld.getManchesterCity()).size());

        }
    }

}
