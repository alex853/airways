/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.pilot.event;

import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.model.Pilot;
import net.simforge.airways.processes.pilot.activity.PilotOnDuty;
import org.hibernate.SessionFactory;

import javax.inject.Inject;

@Subscribe(PilotCheckin.class)
public class PilotCheckin implements Event, Handler {
    @Inject
    private Pilot pilot;
    @Inject
    private ProcessEngine engine;
    @Inject
    private SessionFactory sessionFactory;

    @Override
    public void process() {
        // todo checks

        engine.startActivity(PilotOnDuty.class, pilot);
    }
}
