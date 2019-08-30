/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.ops;

import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;

import java.io.IOException;
import java.io.InputStream;

public class PersonOps {
    public static Person create(Session session, City originCity) {
        BM.start("PersonOps.create");

        try {
            Person person = new Person();
            person.setSex(Math.random() > 0.5 ? "M" : "F");
            person.setName(randomName(person.getSex()));
            person.setSurname(randomSurname());
            person.setOriginCity(originCity);
            person.setPositionCity(originCity);
            person.setType(Person.Type.Ordinal);
            person.setStatus(Person.Status.ReadyToTravel);

            session.save(person);
            session.save(EventLog.make(person, "New person created", originCity));

            return person;
        } finally {
            BM.stop();
        }
    }

    private static Csv femaleNames;
    private static Csv maleNames;
    private static Csv surnames;

    private static synchronized String randomName(String sex) {
        Csv csv;

        if (sex.equals("F")) {
            if (femaleNames == null) {
                femaleNames = loadCsv("/female-names.csv");
            }
            csv = femaleNames;
        } else {
            if (maleNames == null) {
                maleNames = loadCsv("/male-names.csv");
            }
            csv = maleNames;
        }

        return getRandomString(csv);
    }

    private static synchronized String randomSurname() {
        if (surnames == null) {
            surnames = loadCsv("/surnames.csv");
        }

        return getRandomString(surnames);
    }

    private static String getRandomString(Csv csv) {
        int row = (int) (csv.rowCount() * Math.random());
        return csv.value(row, 0);
    }

    private static Csv loadCsv(String csvName) {
        InputStream resourceAsStream = PersonOps.class.getResourceAsStream(csvName);
        String content = null;
        try {
            content = IOHelper.readInputStream(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Csv.fromContent(content);
    }

/*    public static void releaseFromJourney(Session session, Person person) {
        BM.start("PersonOps.releaseFromJourney");
        try {
            person.setStatus(Person.Status.NoTravel);
            person.setHeartbeatDt(JavaTime.nowUtc().plusDays(7));
            City currentCity = person.getJourney().getCurrentCity();
            if (currentCity == null) {
                throw new IllegalStateException("fix it!");
            }
            person.setPositionCity(currentCity);
            person.setJourney(null);

            Util.update(session, person, "releasePerson");
            EventLog.saveLog(session, person, "Journey finished (or terminated)", currentCity);
        } finally {
            BM.stop();
        }
    }*/
}
