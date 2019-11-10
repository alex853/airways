/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.processengine.activity.ActivityInfo;
import net.simforge.airways.ops.AircraftOps;
import net.simforge.airways.ops.PilotOps;
import net.simforge.airways.model.Pilot;
import net.simforge.airways.model.aircraft.Aircraft;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.model.flight.TimetableRow;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.processes.timetablerow.activity.ScheduleFlight;
import org.hibernate.Session;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class OneFlightTest extends BaseEngineCaseTest {

    private TimetableRow timetableRow;

    protected void buildWorld() {
        TestWorld testWorld = new TestWorld(sessionFactory);
        testWorld.createGeo();

        Airport egll = testWorld.getEgllAirport();
        Airport egcc = testWorld.getEgccAirport();

        testWorld.createAircraftTypes();
        testWorld.createAirlines();

        try (Session session = sessionFactory.openSession()) {
            AircraftOps.addAircrafts(session, "AB", "A320", "EGLL", "G-BA??", 1);

            PilotOps.addPilots(session, "United kingdom", "London", "EGLL", 1);
            //todo PilotOps.addNPCPilots(session, "United kingdom", "London", "EGLL", 1);
        }

        timetableRow = testWorld.createTimetableRow(testWorld.getAbAirline(),"AB101", egll, egcc, "12:00", testWorld.getA320Type());
    }

    @Test
    public void testCase() {
        engine.startActivity(ScheduleFlight.class, timetableRow);

        runEngine(1000);

        ActivityInfo status = engine.findActivity(ScheduleFlight.class, timetableRow);
        assertFalse(status.isFinished());

        try (Session session = sessionFactory.openSession()) {
            Pilot pilot = session.load(Pilot.class, 1);
            assertEquals(Pilot.Status.Idle, pilot.getStatus().intValue());
            //todo assertEquals(Pilot.Status.Idle, pilot.getStatus());
            assertEquals("EGCC", pilot.getPerson().getLocationAirport().getIcao());

            Aircraft aircraft = session.load(Aircraft.class, 1);
            assertEquals(Aircraft.Status.Idle, aircraft.getStatus().intValue());
            assertEquals("EGCC", aircraft.getLocationAirport().getIcao());

            Flight flight = session.load(Flight.class, 1);
            assertEquals(Flight.Status.Finished, flight.getStatus().intValue());

            TransportFlight transportFlight = flight.getTransportFlight();
            assertEquals(TransportFlight.Status.Finished, transportFlight.getStatus());
        }
    }
}
