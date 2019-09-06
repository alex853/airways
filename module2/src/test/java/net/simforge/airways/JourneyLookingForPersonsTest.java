/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.engine.activity.ActivityInfo;
import net.simforge.airways.ops.JourneyOps;
import net.simforge.airways.persistence.model.Journey;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.flow.City2CityFlow;
import net.simforge.airways.persistence.model.flow.CityFlow;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.airways.processes.journey.activity.LookingForPersons;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.junit.Test;

import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class JourneyLookingForPersonsTest extends BaseEngineCaseTest {

    public static final int GROUP_SIZE = 5;
    private Journey journey;

    @Override
    protected void buildWorld() {
        createCountry("United kingdom", "GB");
        City londonCity = createCity("United kingdom", "London", 51, 0);
        City manchesterCity = createCity("United kingdom", "Manchester", 53, -2);

        CityFlow londonCityFlow = createCityFlow(londonCity);
        CityFlow manchesterCityFlow = createCityFlow(manchesterCity);

        City2CityFlow flow = createC2CFlow(londonCityFlow, manchesterCityFlow, GROUP_SIZE);

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {
                journey = JourneyOps.create(session, flow);
            });
        }
    }

    @Test
    public void testCase() {
        engine.startActivity(LookingForPersons.class, journey);

        runEngine(1);

        ActivityInfo status = engine.findActivity(LookingForPersons.class, journey);
        assertTrue(status.isFinished());

        try (Session session = sessionFactory.openSession()) {
            List<Person> persons = JourneyOps.getPersons(session, journey);

            assertEquals(GROUP_SIZE, persons.size());
        }
    }
}
