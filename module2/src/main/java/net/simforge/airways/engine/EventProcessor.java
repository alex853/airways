/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

/*
 * Airways project (C) Alexey Kornev, 2015-2018
 */

package net.simforge.airways.engine;

import net.simforge.airways.engine.entities.TaskEntity;

class EventProcessor extends Processor {
    protected EventProcessor(TaskEntity task, InjectionContext baseInjectionContext) {
        super(task, baseInjectionContext);
    }

    @Override
    ProcessingResult process() {
        return null;
    }
}
