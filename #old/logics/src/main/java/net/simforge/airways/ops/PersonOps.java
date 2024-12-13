package net.simforge.airways.ops;

import net.simforge.airways.EventLog;
import net.simforge.airways.model.Person;
import net.simforge.airways.model.geo.City;
import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class PersonOps {
    private static final Logger log = LoggerFactory.getLogger(PersonOps.class);

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
            EventLog.info(session, log, person, "New person created", originCity);

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
        if (resourceAsStream == null) {
            log.error("Unable to load " + csvName + " CSV file");
            throw new IllegalStateException("Unable to load " + csvName + " CSV file");
        }

        String content = null;
        try {
            content = IOHelper.readInputStream(resourceAsStream);
        } catch (IOException e) {
            log.error("Unable to load data from " + csvName + " CSV file", e);
            throw new RuntimeException("Unable to load data from " + csvName + " CSV file", e);
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
                    .setInteger("ordinal", Person.Type.Ordinal.code())
                    .setEntity("locationCity", locationCity)
                    .list();

        } finally {
            BM.stop();
        }
    }
}
