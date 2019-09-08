/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.engine.Engine;
import net.simforge.airways.engine.EngineBuilder;
import net.simforge.airways.engine.entities.TaskEntity;
import net.simforge.airways.persistence.Airways;
import net.simforge.airways.persistence.model.flow.City2CityFlow;
import net.simforge.airways.persistence.model.flow.CityFlow;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.airways.util.SimulatedTimeMachine;
import net.simforge.commons.gckls2com.GC;
import net.simforge.commons.gckls2com.GCAirport;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.hibernate.SessionFactoryBuilder;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.IOException;
import java.time.LocalDateTime;

public abstract class BaseEngineCaseTest {
    protected static final LocalDateTime START_TIME = LocalDateTime.of(2018, 1, 1, 0, 0);

    protected static SessionFactory sessionFactory;

    protected SimulatedTimeMachine timeMachine;
    protected Engine engine;

    @BeforeClass
    public static void beforeClass() {
        sessionFactory = SessionFactoryBuilder
                .forDatabase("test")
                .createSchemaIfNeeded()
                .entities(Airways.entities)
                .entities(new Class[]{TaskEntity.class})
                .build();
    }

    @AfterClass
    public static void afterClass() {
        sessionFactory.close();
        sessionFactory = null;
    }

    @Before
    public void before() {
        timeMachine = new SimulatedTimeMachine(START_TIME);
        engine = EngineBuilder.create()
                .withTimeMachine(timeMachine)
                .withSessionFactory(sessionFactory)
                .build();

        buildWorld();
    }

    @After
    public void after() {
    }

    protected abstract void buildWorld();

    @SuppressWarnings("SameParameterValue")
    protected void runEngine(int minutesToRun) {
        for (int i = 0; i < minutesToRun; i++) {
            timeMachine.plusMinutes(1);

            // ten ticks for each minute
            for (int j = 0; j < 10; j++) {
                engine.tick();
            }
        }
    }
    protected CityFlow createCityFlow(City city) {
        try (Session session = sessionFactory.openSession()) {
            CityFlow cityFlow = new CityFlow();
            cityFlow.setCity(city);

            HibernateUtils.saveAndCommit(session, cityFlow);

            return cityFlow;
        }
    }

    protected City2CityFlow createC2CFlow(CityFlow cityFlow1, CityFlow cityFlow2, int nextGroupSize) {
        try (Session session = sessionFactory.openSession()) {
            City2CityFlow flow = new City2CityFlow();
            flow.setFromFlow(cityFlow1);
            flow.setToFlow(cityFlow2);
            flow.setNextGroupSize(nextGroupSize);

            HibernateUtils.saveAndCommit(session, flow);

            return flow;
        }
    }

    protected Airport importAirportFromGC(String icao) {
        GCAirport gcAirport;
        try {
            gcAirport = GC.findAirport(icao);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

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
}
