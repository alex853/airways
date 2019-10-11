/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.flight.handler;

import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.flight.Flight;
import net.simforge.airways.processes.flight.activity.AllocateFlight;
import net.simforge.airways.processes.flight.event.Planned;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * It initiates allocation process.
 */
@Subscribe(Planned.class)
public class OnPlanned implements Handler {
    private static Logger logger = LoggerFactory.getLogger(OnPlanned.class);

    @Inject
    private Flight flight;
    @Inject
    private ProcessEngine engine;
    @Inject
    private SessionFactory sessionFactory;

    public void process() {
        engine.startActivity(AllocateFlight.class, flight, flight.getScheduledDepartureTime().minusHours(2));

        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.saveAndCommit(session, EventLog.make(flight, "Flight Allocation initiated"));
        }
        logger.info(flight + " - Flight Allocation initiated");
    }
}