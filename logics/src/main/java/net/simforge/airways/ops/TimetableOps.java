/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.ops;

import net.simforge.airways.persistence.model.flight.TimetableRow;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.commons.legacy.BM;
import org.hibernate.Session;

import java.time.LocalDate;
import java.util.Collection;

public class TimetableOps {
    public static Collection<TransportFlight> loadTransportFlights(Session session, TimetableRow timetableRow, LocalDate fromDate, LocalDate toDate) {
        BM.start("TimetableOps.loadTransportFlights");
        try {

            //noinspection unchecked
            return session
                    .createQuery("select tf from TransportFlight tf where tf.timetableRow = :timetableRow and tf.dateOfFlight between :fromDate and :toDate")
                    .setEntity("timetableRow", timetableRow)
                    .setParameter("fromDate", fromDate)
                    .setParameter("toDate", toDate)
                    .list();

        } finally {
            BM.stop();
        }
    }
}
