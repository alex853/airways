/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways;

import net.simforge.airways.ops.CommonOps;
import net.simforge.airways.persistence.Airways;
import net.simforge.airways.persistence.model.geo.City;
import net.simforge.airways.persistence.model.geo.Country;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

public class TestWorld {
    private SessionFactory sessionFactory;
    private Country uk;
    private City london;
    private City manchester;

    public TestWorld(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public void createGeo() {
        uk = createCountry("United kingdom", "GB");

        london = createCity("United kingdom", "London", 51, 0);
        manchester = createCity("United kingdom", "Manchester", 53, -2);
    }

    public Country getUk() {
        return uk;
    }

    public City getLondon() {
        return london;
    }

    public City getManchester() {
        return manchester;
    }

    @SuppressWarnings("SameParameterValue")
    private City createCity(String countryName, String cityName, double lat, double lon) {
        try (Session session = sessionFactory.openSession()) {
            City city = new City();
            city.setCountry(CommonOps.countryByName(session, countryName));
            city.setName(cityName);
            city.setPopulation(1000);
            city.setLatitude(lat);
            city.setLongitude(lon);
            city.setDataset(Airways.ACTIVE_DATASET);

            HibernateUtils.saveAndCommit(session, city);

            return city;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private Country createCountry(String name, String code) {
        try (Session session = sessionFactory.openSession()) {
            Country country = new Country();
            country.setName(name);
            country.setCode(code);

            HibernateUtils.saveAndCommit(session, country);

            return country;
        }
    }


}
