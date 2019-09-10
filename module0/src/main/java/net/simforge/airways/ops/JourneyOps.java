/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.ops;

import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.Journey;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.flow.City2CityFlow;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.JavaTime;
import org.hibernate.Session;

import java.util.List;

public class JourneyOps {
    public static Journey create(Session session, City2CityFlow flow) {
        BM.start("JourneyOps.createOrdinalPerson");
        try {
            // todo AK select direction using query: from Person where status is FREE and location = :flow.toCity
            // todo AK if it says that there are enough persons in TO City then the it will be 'returning' journey

            Journey journey = new Journey();
            journey.setGroupSize(flow.getNextGroupSize());
            journey.setCurrentCity(flow.getFromFlow().getCity());
            journey.setFromCity(flow.getFromFlow().getCity());
            journey.setToCity(flow.getToFlow().getCity());
            journey.setC2cFlow(flow);
            journey.setStatus(Journey.Status.LookingForPersons);
//            journey.setHeartbeatDt(JavaTime.nowUtc());
            journey.setExpirationDt(JavaTime.nowUtc().plusDays(7));

            session.save(journey);
            EventLog.saveLog(session, journey, String.format("New journey is created, group contains %s person(s)", journey.getGroupSize()), flow.getFromFlow().getCity(), flow);

            return journey;
        } finally {
            BM.stop();
        }
    }

    public static List<Person> getPersons(Session session, Journey journey) {
        BM.start("JourneyOps.getPersons");
        try {
            //noinspection unchecked
            return session
                    .createQuery("from Person where journey = :journey")
                    .setEntity("journey", journey)
                    .list();
        } finally {
            BM.stop();
        }
    }

    public static boolean isExpired(Journey journey) {
        return journey.getExpirationDt().isBefore(JavaTime.nowUtc());
    }
}
