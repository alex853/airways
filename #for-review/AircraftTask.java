/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage1.tasks;

import net.simforge.airways.stage1.Util;
import net.simforge.airways.stage1.model.aircraft.Aircraft;
import net.simforge.commons.misc.JavaTime;
import net.simforge.commons.HeartbeatTask;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

public class AircraftTask extends HeartbeatTask<Aircraft> {
    private final SessionFactory sessionFactory;

    public AircraftTask(SessionFactory sessionFactory) {
        super("Aircraft", sessionFactory);
        this.sessionFactory = sessionFactory;
    }

    @Override
    protected Aircraft heartbeat(Aircraft aircraft) {
        try (Session session = sessionFactory.openSession()) {
            aircraft = session.get(Aircraft.class, aircraft.getId());

            switch (aircraft.getStatus()) {
                case Aircraft.Status.Idle:
                    idle(session, aircraft);
                    break;
                case Aircraft.Status.PreFlight:
                    preFlight(session, aircraft);
                    break;
                case Aircraft.Status.TaxiingOut:
                    taxiingOut(session, aircraft);
                    break;
                case Aircraft.Status.Flying:
                    flying(session, aircraft);
                    break;
                case Aircraft.Status.TaxiingIn:
                    taxiingIn(session, aircraft);
                    break;
                case Aircraft.Status.PostFlight:
                    postFlight(session, aircraft);
                    break;
                default:
                    throw new IllegalStateException("Unsupported aircraft status " + aircraft.getStatus());
            }

            return aircraft;
        }
    }

    private void idle(Session session, Aircraft aircraft) {
        aircraft.setHeartbeatDt(JavaTime.nowUtc().plusDays(1));
        Util.update(session, aircraft);
    }

    private void preFlight(Session session, Aircraft aircraft) {

    }

    private void taxiingOut(Session session, Aircraft aircraft) {

    }

    private void flying(Session session, Aircraft aircraft) {

    }

    private void taxiingIn(Session session, Aircraft aircraft) {

    }

    private void postFlight(Session session, Aircraft aircraft) {

    }
}
