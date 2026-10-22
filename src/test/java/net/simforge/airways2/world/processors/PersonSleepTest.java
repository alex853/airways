package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PilotRest;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A pilot goes to sleep and wakes up: the awake credit is fixed at the moment of falling asleep and filled at waking up.
 */
class PersonSleepTest {
    private static final int HOUR = 3600;

    private World world;
    private Persons.Person person;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        world.setWorldTime(world.getWorldTime() + 100 * HOUR);
        person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1, Persons.LocationType.None, 0, 0, "AK", 0);
    }

    private void hoursPass(final int hours) {
        world.setWorldTime(world.getWorldTime() + hours * HOUR);
    }

    @Test
    void aNewPilotHasNoCreditStartedAndStartsWithSixteenHours() {
        assertTrue(world.personControl().awakeCreditIsNotStarted(person));

        world.personControl().startAwakeCredit(person);

        assertEquals(PilotRest.INITIAL_CREDIT_MINUTES, person.getAwakeCreditMinutes());
        assertEquals(world.getWorldTime(), person.getCreditTime());
    }

    @Test
    void sleepFixesTheCreditAndWakingUpFillsIt() {
        world.personControl().startAwakeCredit(person);
        hoursPass(14);

        world.personControl().startSleep(person);

        assertEquals(Persons.State.Resting, person.getState());
        assertEquals(2 * 60, person.getAwakeCreditMinutes());

        hoursPass(8);
        world.personControl().wakeUp(person);

        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(16 * 60, person.getAwakeCreditMinutes());
        assertEquals(world.getWorldTime(), person.getCreditTime());
    }

    @Test
    void shortSleepGivesNothing() {
        world.personControl().startAwakeCredit(person);
        hoursPass(10);
        world.personControl().startSleep(person);
        final int credit = person.getAwakeCreditMinutes();

        hoursPass(1);
        world.personControl().wakeUp(person);

        assertEquals(credit, person.getAwakeCreditMinutes());
    }

    @Test
    void overtiredPilotRepaysTheMinusBySleep() {
        world.personControl().startAwakeCredit(person);
        hoursPass(19);
        world.personControl().startSleep(person);
        assertEquals(-3 * 60, person.getAwakeCreditMinutes());

        hoursPass(4);
        world.personControl().wakeUp(person);

        assertEquals(5 * 60, person.getAwakeCreditMinutes());
    }

    @Test
    void onlyAnIdlePilotCanGoToSleep() {
        person.setState(Persons.State.OnDuty);
        assertThrows(IllegalStateException.class, () -> world.personControl().startSleep(person));

        person.setState(Persons.State.Travelling);
        assertThrows(IllegalStateException.class, () -> world.personControl().startSleep(person));
    }

    @Test
    void onlyASleepingPilotCanWakeUp() {
        assertThrows(IllegalStateException.class, () -> world.personControl().wakeUp(person));
    }
}
