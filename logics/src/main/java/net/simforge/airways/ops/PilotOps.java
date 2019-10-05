/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.ops;

import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.Pilot;
import net.simforge.airways.persistence.model.flight.PilotAssignment;
import net.simforge.airways.persistence.model.geo.Airport;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.airways.persistence.model.geo.Country;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;

import java.util.List;

public class PilotOps {
    public static void addPilots(Session session, String countryName, String cityName, String airportIcao, int count) {
        Country country = CommonOps.countryByName(session, countryName);
        if (country == null) {
            throw new IllegalArgumentException("Could not find country '" + countryName + "'");
        }

        City city = CommonOps.cityByNameAndCountry(session, cityName, country);
        if (city == null) {
            throw new IllegalArgumentException("Could not find city '" + cityName + "'");
        }

        Airport airport = CommonOps.airportByIcao(session, airportIcao);

        //noinspection unchecked
        List<Pilot> pilots = session
                .createQuery("from Pilot pilot where pilot.person.originCity = :city")
                .setEntity("city", city)
                .list();

        if (pilots.size() >= count) {
            return;
        }

        int countToCreate = count - pilots.size();

        for (int i = 0; i < countToCreate; i++) {
            HibernateUtils.transaction(session, () -> {
                Person person = PersonOps.createOrdinalPerson(session, city);
                person.setType(Person.Type.Excluded);
                person.setLocationAirport(airport);
                person.setLocationCity(null);
                session.update(person);

                Pilot pilot = new Pilot();
                pilot.setPerson(person);
                pilot.setStatus(Pilot.Status.Idle);
                //pilot.setHeartbeatDt(JavaTime.nowUtc());
                session.save(pilot);

                EventLog.saveLog(session, pilot, "Pilot created", person);
            });
        }
    }

    public static PilotAssignment findInProgressAssignment(Session session, Pilot pilot) {
        BM.start("PilotOps.findInProgressAssignment");
        try {
            return (PilotAssignment) session // todo check uniqueness
                    .createQuery("from PilotAssignment as pa " +
                            "where pa.pilot = :pilot " +
                            "and pa.status = :inProgress")
                    .setEntity("pilot", pilot)
                    .setInteger("inProgress", PilotAssignment.Status.InProgress)
                    .setMaxResults(1)
                    .uniqueResult();

        } finally {
            BM.stop();
        }
    }

    public static List<PilotAssignment> loadUpcomingAssignments(Session session, Pilot pilot) {
        BM.start("PilotOps.loadPilotAssignments");
        try {

            //noinspection unchecked
            return session
                    .createQuery("select pa " +
                            "from PilotAssignment as pa " +
                            "inner join pa.flight as flight " +
                            "where pa.pilot = :pilot " +
                            "  and pa.status = :assigned " +
                            "order by flight.scheduledDepartureTime asc")
                    .setEntity("pilot", pilot)
                    .setInteger("assigned", PilotAssignment.Status.Assigned)
                    .list();

        } finally {
            BM.stop();
        }
    }

    public static List<Pilot> loadAllPilots(Session session) {
        BM.start("PilotOps.loadAllPilots");
        try {

            //noinspection unchecked
            return session
                    .createQuery("from Pilot")
                    .list();

        } finally {
            BM.stop();
        }
    }
}
