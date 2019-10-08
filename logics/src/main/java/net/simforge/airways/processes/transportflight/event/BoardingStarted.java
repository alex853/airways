/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.transportflight.event;

import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processes.DurationConsts;
import net.simforge.airways.processes.transportflight.activity.Boarding;
import net.simforge.airways.processes.transportflight.activity.Checkin;
import net.simforge.commons.hibernate.HibernateUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.xml.bind.annotation.XmlInlineBinaryData;
import java.time.LocalDateTime;

/**
 * This event is sourced by Pilot. TransportFlight reacts and does its job - board passengers.
 */
@Subscribe(BoardingStarted.class)
public class BoardingStarted implements Event, Handler {
    private static Logger logger = LoggerFactory.getLogger(BoardingStarted.class);

    @Inject
    private TransportFlight transportFlight;
    @Inject
    private ProcessEngine engine;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        try (Session session = sessionFactory.openSession()) {
            HibernateUtils.transaction(session, () -> {

                transportFlight = session.load(TransportFlight.class, transportFlight.getId());

                transportFlight.setStatus(TransportFlight.Status.Boarding);
                session.update(transportFlight);

                engine.startActivity(Boarding.class, transportFlight);

                session.save(EventLog.make(transportFlight, "Boarding started"));
                logger.info(transportFlight + " - Boarding started");

            });
        }
    }
}
