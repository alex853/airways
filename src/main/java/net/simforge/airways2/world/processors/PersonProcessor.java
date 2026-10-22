package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Persons;

/**
 * The persons whose heartbeat has come. For now there is one event: a sleeping pilot wakes up by itself (the heartbeat was set to a random moment from 7.5 to
 * 10 hours after falling asleep, see PersonControl.startSleep). The falling asleep by itself is to come the same way.
 */
public class PersonProcessor {
    public static void process(final World world) {
        Processing.heartbeat(() -> world.persons().nextForHeartbeat(world.getWorldTime()),
                person -> processPerson(world, person));
    }

    private static void processPerson(final World world, final Persons.Person person) {
        person.setHeartbeatTime(0);

        if (person.getState() == Persons.State.Resting) {
            world.personControl().wakeUpByItself(person);
        }
    }
}
