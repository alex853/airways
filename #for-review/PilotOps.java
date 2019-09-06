/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage2;

import net.simforge.airways.stage2.model.Pilot;
import net.simforge.airways.stage2.model.flight.PilotAssignment;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;

import java.util.List;

public class PilotOps {

    public static List<PilotAssignment> loadPilotAssignments(Session session, Pilot pilot) {
        BM.start("PilotTask.loadPilotAssignments");
        try {

            //noinspection JpaQlInspection,unchecked
            return session
                    .createQuery("select pa " +
                            "from PilotAssignment as pa " +
                            "inner join pa.flight as flight " +
                            "where pa.pilot = :pilot " +
                            "  and pa.status = :status " +
                            "order by flight.scheduledDepartureTime asc")
                    .setEntity("pilot", pilot)
                    .setInteger("status", PilotAssignment.Status.Assigned)
                    .list();

        } finally {
            BM.stop();
        }
    }

    public static PilotAssignment loadInProgressPilotAssignment(Session session, Pilot pilot) {
        BM.start("PilotTask.loadInProgressPilotAssignment");
        try {

            //noinspection JpaQlInspection
            return (PilotAssignment) session
                    .createQuery("select pa " +
                            "from PilotAssignment as pa " +
                            "inner join pa.flight as flight " +
                            "where pa.pilot = :pilot " +
                            "and pa.status = :status")
                    .setEntity("pilot", pilot)
                    .setInteger("status", PilotAssignment.Status.InProgress)
                    .setMaxResults(1)
                    .uniqueResult();

        } finally {
            BM.stop();
        }
    }

    public static List<Pilot> loadAllPilots(Session session) {
        BM.start("PilotOps.loadAllPilots");
        try {

            //noinspection JpaQlInspection,unchecked
            return session
                    .createQuery("select p from Pilot p")
                    .list();

        } finally {
            BM.stop();
        }
    }
}
