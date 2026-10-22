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
        // two hours of the credit for an hour of sleep, 2 h + 16 h, there is no maximum
        assertEquals(18 * 60, person.getAwakeCreditMinutes());
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

    // ---- the pilot wakes up by itself

    @Test
    void fallingAsleepSchedulesTheWakeUpFromSevenAndAHalfToTenHours() {
        world.personControl().startAwakeCredit(person);
        final int now = world.getWorldTime();

        world.personControl().startSleep(person);

        assertTrue(person.getHeartbeatTime() >= now + PilotRest.AUTO_WAKE_UP_MIN_MINUTES * Time.ONE_MINUTE, "too early " + (person.getHeartbeatTime() - now));
        assertTrue(person.getHeartbeatTime() <= now + PilotRest.AUTO_WAKE_UP_MAX_MINUTES * Time.ONE_MINUTE, "too late " + (person.getHeartbeatTime() - now));
        assertTrue(world.persons().checkHeartbeatIndex().isEmpty());
    }

    @Test
    void theWakeUpIsRandomNotAlwaysTheSameMoment() {
        final java.util.Set<Integer> moments = new java.util.HashSet<>();
        for (int i = 0; i < 30; i++) {
            final Persons.Person p = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 100 + i, Persons.LocationType.None, 0, 0, "AK", 0);
            world.personControl().startSleep(p);
            moments.add(p.getHeartbeatTime());
        }

        assertTrue(moments.size() > 1);
    }

    @Test
    void theProcessorWakesThePilotWhenTheTimeHasCome() {
        world.personControl().startAwakeCredit(person);
        hoursPass(14);
        world.personControl().startSleep(person);
        final int wakeUpAt = person.getHeartbeatTime();

        // a minute before: nothing happens
        world.setWorldTime(wakeUpAt - Time.ONE_MINUTE);
        PersonProcessor.process(world);
        assertEquals(Persons.State.Resting, person.getState());

        world.setWorldTime(wakeUpAt);
        PersonProcessor.process(world);

        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(0, person.getHeartbeatTime());
        // 2 h left when falling asleep and two for one for the sleep of 7.5 to 10 hours: from 17 to 22 hours
        assertTrue(person.getAwakeCreditMinutes() >= 2 * 60 + 2 * PilotRest.AUTO_WAKE_UP_MIN_MINUTES, "credit " + person.getAwakeCreditMinutes());
        assertTrue(person.getAwakeCreditMinutes() <= 2 * 60 + 2 * PilotRest.AUTO_WAKE_UP_MAX_MINUTES, "credit " + person.getAwakeCreditMinutes());
        assertTrue(world.persons().checkHeartbeatIndex().isEmpty());
    }

    @Test
    void aPilotWokenByTheUserIsNotWokenAgainByTheClock() {
        world.personControl().startAwakeCredit(person);
        world.personControl().startSleep(person);
        hoursPass(3);
        world.personControl().wakeUp(person);
        assertEquals(0, person.getHeartbeatTime());
        assertTrue(world.persons().checkHeartbeatIndex().isEmpty());

        hoursPass(10);
        // the pilot is free: the processor has nothing to do with it, and if it falls asleep again, it is a new sleep with a new time
        PersonProcessor.process(world);
        assertEquals(Persons.State.Idle, person.getState());
        world.personControl().startSleep(person);
        assertTrue(person.getHeartbeatTime() > world.getWorldTime());
    }

    @Test
    void aPilotWhoIsNotAsleepIsLeftAloneByAnOldHeartbeat() {
        person.setHeartbeatTime(world.getWorldTime() - 1);
        person.setState(Persons.State.OnDuty);

        PersonProcessor.process(world);

        assertEquals(Persons.State.OnDuty, person.getState());
        assertEquals(0, person.getHeartbeatTime());
    }

    @Test
    void aSleepOfNineAndAHalfHoursStillGivesTwoForOne() {
        world.personControl().startAwakeCredit(person);
        hoursPass(15);
        world.personControl().startSleep(person);
        assertEquals(60, person.getAwakeCreditMinutes());

        hoursPass(9);
        world.setWorldTime(world.getWorldTime() + 30 * Time.ONE_MINUTE);
        world.personControl().wakeUp(person);

        assertEquals(60 + 2 * (9 * 60 + 30), person.getAwakeCreditMinutes());
    }

    @Test
    void sleepingPilotsWithoutAWakeUpGetOneAtTheStart() {
        // fell asleep three hours ago, before the wake up was scheduled
        final int fellAsleepAt = world.getWorldTime() - 3 * HOUR;
        person.setState(Persons.State.Resting);
        person.setAwakeCredit(120, fellAsleepAt);
        assertEquals(0, person.getHeartbeatTime());

        world.personControl().scheduleWakeUpOfSleepingPersons();

        assertTrue(person.getHeartbeatTime() >= fellAsleepAt + PilotRest.AUTO_WAKE_UP_MIN_MINUTES * Time.ONE_MINUTE);
        assertTrue(person.getHeartbeatTime() <= fellAsleepAt + PilotRest.AUTO_WAKE_UP_MAX_MINUTES * Time.ONE_MINUTE);
        assertTrue(world.persons().checkHeartbeatIndex().isEmpty());
    }

    @Test
    void aPilotWhoHasSleptForTooLongWakesAtTheNextStepOfTheWorld() {
        final int now = world.getWorldTime();
        person.setState(Persons.State.Resting);
        person.setAwakeCredit(120, now - 20 * HOUR);

        world.personControl().scheduleWakeUpOfSleepingPersons();

        assertEquals(now, person.getHeartbeatTime());
        PersonProcessor.process(world);
        assertEquals(Persons.State.Idle, person.getState());
        // 20 hours of sleep give 40 hours of the credit
        assertEquals(120 + 2 * 20 * 60, person.getAwakeCreditMinutes());
    }

    @Test
    void personsWhoDoNotSleepOrAlreadyHaveAWakeUpAreNotTouchedAtTheStart() {
        final Persons.Person idle = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 101, Persons.LocationType.None, 0, 0, "AK", 0);
        final Persons.Person scheduled = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 102, Persons.LocationType.None, 0, 0, "AK", 0);
        world.personControl().startAwakeCredit(scheduled);
        world.personControl().startSleep(scheduled);
        final int scheduledHeartbeat = scheduled.getHeartbeatTime();

        world.personControl().scheduleWakeUpOfSleepingPersons();

        assertEquals(0, idle.getHeartbeatTime());
        assertEquals(scheduledHeartbeat, scheduled.getHeartbeatTime());
    }

    // ---- a pilot who falls asleep in debt sleeps longer

    private int sleepWithCredit(final int creditMinutesAtSleepStart) {
        world.personControl().startAwakeCredit(person);
        person.setAwakeCredit(creditMinutesAtSleepStart, world.getWorldTime());
        final int now = world.getWorldTime();
        world.personControl().startSleep(person);
        return (person.getHeartbeatTime() - now) / Time.ONE_MINUTE;
    }

    @Test
    void aDebtOfThreeHoursAddsAnHourToTheSleep() {
        final int minutes = sleepWithCredit(-180);

        assertTrue(minutes >= PilotRest.AUTO_WAKE_UP_MIN_MINUTES + 60 && minutes <= PilotRest.AUTO_WAKE_UP_MAX_MINUTES + 60, "minutes " + minutes);
    }

    @Test
    void aDebtOfAnHourAndAHalfAddsHalfAnHour() {
        final int minutes = sleepWithCredit(-90);

        assertTrue(minutes >= PilotRest.AUTO_WAKE_UP_MIN_MINUTES + 30 && minutes <= PilotRest.AUTO_WAKE_UP_MAX_MINUTES + 30, "minutes " + minutes);
    }

    @Test
    void aBiggerDebtDoesNotAddMoreThanAnHour() {
        final int minutes = sleepWithCredit(-600);

        assertTrue(minutes >= PilotRest.AUTO_WAKE_UP_MIN_MINUTES + 60 && minutes <= PilotRest.AUTO_WAKE_UP_MAX_MINUTES + 60, "minutes " + minutes);
    }

    @Test
    void aPilotWithCreditSleepsTheUsualTime() {
        final int minutes = sleepWithCredit(300);

        assertTrue(minutes >= PilotRest.AUTO_WAKE_UP_MIN_MINUTES && minutes <= PilotRest.AUTO_WAKE_UP_MAX_MINUTES, "minutes " + minutes);
    }

    @Test
    void aSleepingPilotInDebtWithoutAWakeUpGetsTheLongerSleepAtTheStart() {
        final int fellAsleepAt = world.getWorldTime() - 3 * HOUR;
        person.setState(Persons.State.Resting);
        person.setAwakeCredit(-180, fellAsleepAt);

        world.personControl().scheduleWakeUpOfSleepingPersons();

        assertTrue(person.getHeartbeatTime() >= fellAsleepAt + (PilotRest.AUTO_WAKE_UP_MIN_MINUTES + 60) * Time.ONE_MINUTE);
        assertTrue(person.getHeartbeatTime() <= fellAsleepAt + (PilotRest.AUTO_WAKE_UP_MAX_MINUTES + 60) * Time.ONE_MINUTE);
    }
}
