/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.worldbuilder.tools;

import net.simforge.airways.ops.PilotOps;
import net.simforge.airways.Airways;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

public class AddPilots {

    public static void main(String[] args) {
        try (SessionFactory sessionFactory = Airways.buildSessionFactory();
             Session session = sessionFactory.openSession()) {

            PilotOps.addNPCPilots(session, "United kingdom", "London", "EGLL", 10);
//            PilotOps.addPilots(session, "Australia", "Sydney", "YSSY", 20);
//            PilotOps.addPilots(session, "China", "Shanghai", "ZSPD", 20);
        }
    }

}
