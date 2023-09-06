package net.simforge.airways.worldbuilder;

import net.simforge.airways.Airways;
import net.simforge.airways.model.Airline;
import net.simforge.airways.model.flight.TimetableRow;
import net.simforge.airways.model.geo.Airport;
import net.simforge.airways.ops.CommonOps;
import net.simforge.airways.ops.GeoOps;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.List;

public class World23s3 {
    public static void main(String[] args) {
        // stopping scheduling of EGSS flights

        try (SessionFactory sessionFactory = Airways.buildSessionFactory();
             Session session = sessionFactory.openSession()) {

            final Airport egss = GeoOps.loadAirportByIcao(session, "EGSS");
            final Airline ww = CommonOps.airlineByIata(session, "WW");

            //noinspection unchecked
            final List<TimetableRow> rows = session.createQuery("from TimetableRow " +
                    "where airline = :airline " +
                    "  and status = :active " +
                    "  and (fromAirport = :airport " +
                    "       or toAirport = :airport)")
                    .setParameter("airline", ww)
                    .setParameter("active", TimetableRow.Status.Active.code())
                    .setParameter("airport", egss)
                    .list();

            rows.forEach(row -> {
                row.setStatus(TimetableRow.Status.Stopped);
                HibernateUtils.updateAndCommit(session, row);
            });
        }
    }
}
