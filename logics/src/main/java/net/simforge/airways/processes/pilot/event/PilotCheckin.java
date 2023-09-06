package net.simforge.airways.processes.pilot.event;

import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.event.Event;
import net.simforge.airways.processengine.event.Handler;
import net.simforge.airways.processengine.event.Subscribe;
import net.simforge.airways.model.Pilot;
import net.simforge.airways.processes.pilot.activity.PilotOnDuty;

import javax.inject.Inject;

@Subscribe(PilotCheckin.class)
public class PilotCheckin implements Event, Handler {
    @Inject
    private Pilot pilot;
    @Inject
    private ProcessEngineScheduling scheduling;

    @Override
    public void process() {
        // todo checks

        scheduling.startActivity(PilotOnDuty.class, pilot);
    }
}
