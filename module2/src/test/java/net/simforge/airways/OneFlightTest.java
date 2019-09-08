/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.engine.activity.ActivityInfo;
import net.simforge.airways.ops.AircraftOps;
import net.simforge.airways.ops.CommonOps;
import net.simforge.airways.ops.PilotOps;
import net.simforge.airways.persistence.model.Pilot;
import net.simforge.airways.persistence.model.aircraft.Aircraft;
import net.simforge.airways.persistence.model.aircraft.AircraftType;
import net.simforge.airways.persistence.model.flight.Flight;
import net.simforge.airways.persistence.model.flight.TimetableRow;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.airways.processes.timetablerow.activity.ScheduleFlight;
import net.simforge.airways.util.FlightTimeline;
import net.simforge.airways.util.SimpleFlight;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.misc.Geo;
import net.simforge.commons.misc.JavaTime;
import net.simforge.commons.misc.Weekdays;
import org.hibernate.Session;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class OneFlightTest extends BaseEngineCaseTest {

    private TimetableRow timetableRow;

    protected void buildWorld() {
        Airport egll = importAirportFromGC("EGLL");
        Airport egcc = importAirportFromGC("EGCC");

        AircraftType a320type = TestRefData.getA320Data();
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, a320type);
            a320type = CommonOps.aircraftTypeByIcao(session, "A320");
        }

        TestWorld testWorld = new TestWorld(sessionFactory);
        testWorld.createGeo();

        try (Session session = sessionFactory.openSession()) {
            AircraftOps.addAircrafts(session, "AB", "A320", "EGLL", "G-BA??", 1);

            PilotOps.addPilots(session, "United kingdom", "London", "EGLL", 1);
        }

        SimpleFlight simpleFlight = SimpleFlight.forRoute(
                new Geo.Coords(egll.getLatitude(), egll.getLongitude()),
                new Geo.Coords(egcc.getLatitude(), egcc.getLongitude()),
                a320type);

        Duration flyingTime = simpleFlight.getTotalTime();
        FlightTimeline timeline = FlightTimeline.byFlyingTime(flyingTime);
        Duration flightDuration = timeline.getScheduledDuration(timeline.getBlocksOff(), timeline.getBlocksOn());

        timetableRow = new TimetableRow();
        timetableRow.setNumber("AB101");
        timetableRow.setFromAirport(egll);
        timetableRow.setToAirport(egcc);
        timetableRow.setDepartureTime("12:00");
        timetableRow.setDuration(JavaTime.toHhmm(flightDuration));
        timetableRow.setWeekdays(Weekdays.wholeWeek().toString());
        timetableRow.setAircraftType(a320type);
        timetableRow.setTotalTickets(160);
        timetableRow.setHorizon(0);

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, timetableRow);
        }
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
            assertEquals("EGCC", pilot.getPerson().getPositionAirport().getIcao());

            Aircraft aircraft = session.load(Aircraft.class, 1);
            assertEquals(Aircraft.Status.Idle, aircraft.getStatus().intValue());
            assertEquals("EGCC", aircraft.getPositionAirport().getIcao());

            Flight flight = session.load(Flight.class, 1);
            assertEquals(Flight.Status.Finished, flight.getStatus().intValue());

            TransportFlight transportFlight = flight.getTransportFlight();
            // todo p3 uncomment that failing line assertEquals(TransportFlight.Status.Finished, transportFlight.getStatus());
        }
    }
}
