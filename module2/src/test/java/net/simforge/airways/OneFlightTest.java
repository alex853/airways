/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.EngineBuilder;
import net.simforge.airways.engine.activity.ActivityInfo;
import net.simforge.airways.engine.entities.TaskEntity;
import net.simforge.airways.persistence.model.Airline;
import net.simforge.airways.persistence.model.EventLogEntry;
import net.simforge.airways.persistence.model.aircraft.AircraftType;
import net.simforge.airways.persistence.model.flight.Flight;
import net.simforge.airways.persistence.model.flight.TimetableRow;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.airways.processes.timetablerow.activity.ScheduleFlight;
import net.simforge.airways.util.FlightTimeline;
import net.simforge.airways.util.SimpleFlight;
import net.simforge.airways.util.TestRefData;
import net.simforge.commons.gckls2com.GC;
import net.simforge.commons.gckls2com.GCAirport;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.hibernate.SessionFactoryBuilder;
import net.simforge.commons.misc.Geo;
import net.simforge.commons.misc.JavaTime;
import net.simforge.commons.misc.Weekdays;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.*;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OneFlightTest {

    private static final LocalDateTime START_TIME = LocalDateTime.of(2018, 1, 1, 0, 0);

    private static SessionFactory sessionFactory;

    private TimetableRow timetableRow;
    private SimulatedTimeMachine timeMachine;
    private Engine engine;

    @BeforeClass
    public static void beforeClass() {
        sessionFactory = SessionFactoryBuilder
                .forDatabase("test")
                .createSchemaIfNeeded()
                .entities(new Class[]{
                        TaskEntity.class,

                        EventLogEntry.class,

                        AircraftType.class,
                        TimetableRow.class,
                        TransportFlight.class,
                        Flight.class,
                        Airport.class,
                        Airline.class})
                .build();
    }

    @AfterClass
    public static void afterClass() {
        sessionFactory.close();
        sessionFactory = null;
    }

    @Before
    public void before() throws IOException {
        timeMachine = new SimulatedTimeMachine(START_TIME);
        engine = EngineBuilder.create()
                .withTimeMachine(timeMachine)
                .withSessionFactory(sessionFactory)
                .build();

        Airport egll = loadAirport("EGLL");
        Airport egcc = loadAirport("EGCC");

        AircraftType a320type = TestRefData.getA320Data();

        // todo p2 add some aircrafts
        // todo p1 add some pilots

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

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, a320type);
            HibernateUtils.saveAndCommit(session, timetableRow);
        }
    }

    private Airport loadAirport(String icao) throws IOException {
        GCAirport gcAirport = GC.findAirport(icao);
        Airport airport = new Airport();
        airport.setIcao(gcAirport.getIcao());
        airport.setIata(gcAirport.getIata());
        airport.setName(gcAirport.getName());
        airport.setLatitude(gcAirport.getLat());
        airport.setLongitude(gcAirport.getLon());
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, airport);
        }
        return airport;
    }

    @After
    public void after() {
    }

    @Test
    public void testFlight() {
        engine.startActivity(ScheduleFlight.class, timetableRow);

        runEngine(1000);

        ActivityInfo status = engine.findActivity(ScheduleFlight.class, timetableRow);
        assertFalse(status.isFinished());
        // todo p2 activityInfo assertTrue(status.getLastActTime().isAfter(START_TIME));
    }

    @SuppressWarnings("SameParameterValue")
    private void runEngine(int minutesToRun) {
        for (int i = 0; i < minutesToRun; i++) {
            timeMachine.plusMinutes(1);
            engine.tick();
        }
    }
}
