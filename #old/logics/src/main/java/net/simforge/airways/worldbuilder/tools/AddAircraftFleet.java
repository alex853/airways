/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.worldbuilder.tools;

import net.simforge.airways.ops.AircraftOps;
import net.simforge.airways.Airways;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

public class AddAircraftFleet {

    public static void main(String[] args) {
        try (SessionFactory sessionFactory = Airways.buildSessionFactory();
             Session session = sessionFactory.openSession()) {

//            AircraftOps.addAircrafts(session, "ZZ", "A320", "UUDD", "VP-A??", 30);
            AircraftOps.addAircrafts(session, "ZZ", "A320", "EGLL", "G-AA??", 4);
// todo find appropriate ICAOs for the hubs below           buildMidRangeHub(session, "ZZ", "Russia", "Moskva", 800, 1500, "A320", 20);
//            buildMidRangeHub(session, "ZZ", "United kingdom", "London", 100, 1500, "A320", 50);
//            buildMidRangeHub(session, "ZZ", "United states", "New york", 100, 2000, "A320", 50);
//            buildMidRangeHub(session, "ZZ", "United states", "Los angeles", 100, 2000, "A320", 50);
//            buildMidRangeHub(session, "ZZ", "Venezuela", "Caracas", 100, 2000, "A320", 50);
//            buildMidRangeHub(session, "ZZ", "Brazil", "Sao paulo", 100, 2000, "A320", 50);
//            buildMidRangeHub(session, "ZZ", "India", "Dilli", 100, 2000, "A320", 50);
//            buildMidRangeHub(session, "ZZ", "China", "Shanghai", 100, 2000, "A320", 50);
//            buildMidRangeHub(session, "ZZ", "Singapore", "Singapore", 100, 2000, "A320", 50);
//            buildMidRangeHub(session, "ZZ", "Australia", "Sydney", 100, 2000, "A320", 50);*/

            //B744
            AircraftOps.addAircrafts(session, "WW", "B744", "EGLL", "G-BN??", 1);
//            AircraftOps.addAircrafts(session, "WW", "B744", "YSSY", "VH-B??", 10);
//            AircraftOps.addAircrafts(session, "WW", "B744", "ZSPD", "X-CB??", 10);
        }
    }
}
