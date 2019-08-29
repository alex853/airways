/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

package net.simforge.airways;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.EngineBuilder;
import net.simforge.airways.engine.entities.TaskEntity;
import net.simforge.airways.engine.proto.ActivityStatus;
import net.simforge.airways.entities.AirlineEntity;
import net.simforge.airways.entities.aircraft.AircraftTypeEntity;
import net.simforge.airways.entities.flight.FlightEntity;
import net.simforge.airways.entities.flight.TimetableRowEntity;
import net.simforge.airways.entities.flight.TransportFlightEntity;
import net.simforge.airways.entities.geo.AirportEntity;
import net.simforge.airways.model.aircraft.AircraftType;
import net.simforge.airways.model.flight.TimetableRow;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.persistence.model.EventLogEntry;
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

public class Test1 {

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

                        AircraftTypeEntity.class,
                        TimetableRowEntity.class,
                        TransportFlightEntity.class,
                        FlightEntity.class,
                        AirportEntity.class,
                        AirlineEntity.class})
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

        SimpleFlight simpleFlight = SimpleFlight.forRoute(
                new Geo.Coords(egll.getLatitude(), egll.getLongitude()),
                new Geo.Coords(egcc.getLatitude(), egcc.getLongitude()),
                a320type);

        Duration flyingTime = simpleFlight.getTotalTime();
        FlightTimeline timeline = FlightTimeline.byFlyingTime(flyingTime);
        Duration flightDuration = timeline.getScheduledDuration(timeline.getBlocksOff(), timeline.getBlocksOn());

        timetableRow = new TimetableRowEntity();
        timetableRow.setFromAirport(egll);
        timetableRow.setToAirport(egcc);
        timetableRow.setDepartureTime("12:00");
        timetableRow.setDuration(JavaTime.toHhmm(flightDuration));
        timetableRow.setWeekdays(Weekdays.wholeWeek().toString());
        timetableRow.setAircraftType(a320type);

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, a320type);
            HibernateUtils.saveAndCommit(session, timetableRow);
        }
    }

    private Airport loadAirport(String icao) throws IOException {
        GCAirport gcAirport = GC.findAirport(icao);
        Airport airport = new AirportEntity();
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
    public void test1() {
        engine.startActivity(ScheduleFlight.class, timetableRow);

        runEngine(10);

        ActivityStatus status = engine.getActivityStatus(ScheduleFlight.class, timetableRow);
        assertFalse(status.isDone());
        assertTrue(status.getLastActTime().isAfter(START_TIME));
    }

    private void runEngine(int minutesToRun) {
        for (int i = 0; i < minutesToRun; i++) {
            timeMachine.plusMinutes(1);
            engine.tick();
        }
    }
}
