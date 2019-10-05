/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.ops;

import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.Person;
import net.simforge.airways.persistence.model.flight.PilotAssignment;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class PersonOps {
    public static Person createOrdinalPerson(Session session, City originCity) {
        BM.start("PersonOps.createOrdinalPerson");

        try {
            Person person = new Person();
            person.setSex(Math.random() > 0.5 ? "M" : "F");
            person.setName(randomName(person.getSex()));
            person.setSurname(randomSurname());
            person.setOriginCity(originCity);
            person.setLocationCity(originCity);
            person.setType(Person.Type.Ordinal);
            person.setStatus(Person.Status.Idle);

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

    public static List<Person> loadOrdinalPersonsByLocationCity(Session session, City locationCity) {
        BM.start("PersonOps.loadOrdinalPersonsByLocationCity");
        try {

            //noinspection unchecked
            return session
                    .createQuery("from Person " +
                            "where type = :ordinal " +
                            " and locationCity = :locationCity")
                    .setInteger("ordinal", Person.Type.Ordinal)
                    .setEntity("locationCity", locationCity)
                    .list();

        } finally {
            BM.stop();
        }
    }
}
