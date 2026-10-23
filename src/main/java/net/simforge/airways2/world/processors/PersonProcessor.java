package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Persons;

/**
 * The persons whose heartbeat has come: a sleeping pilot wakes up by itself (the heartbeat was set to a random moment from 7.5 to 10 hours after falling
 * asleep, see PersonControl.startSleep), a free pilot falls asleep by itself (the heartbeat was set when the pilot became free, see
 * PersonControl.scheduleAutoSleep).
 */
public class PersonProcessor {
    public static void process(final World world) {
        Processing.heartbeat(() -> world.persons().nextForHeartbeat(world.getWorldTime()),
                person -> processPerson(world, person));
    }

    private static void processPerson(final World world, final Persons.Person person) {
        person.setHeartbeatTime(0);

        switch (person.getState()) {
            case Resting -> world.personControl().wakeUpByItself(person);
            case Idle -> world.personControl().fallAsleepByItself(person);
            case Travelling -> world.personControl().arrive(person);
            default -> { }
        }
    }
}
