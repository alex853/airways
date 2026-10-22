package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.datamodel.PilotLogbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The duty of a flight is from Start flight to Finish flight: the start is kept in the person while the flight is in progress and goes to the logbook entry
 * at the finish.
 */
class PersonDutyTest {
    private World world;
    private FlightMissionControl control;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private FlightMissions.Mission mission;
    private Persons.Person person;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        control = world.flightMissionControl();
        airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        airportB = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", airportA);
        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        mission = world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, departure, departure + 2 * Time.ONE_HOUR);
        mission.setUserId(7);
        person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 7, Persons.LocationType.Airport, airportA.getId(), 0, "AK", 0);
    }

    private void minutesPass(final int minutes) {
        world.setWorldTime(world.getWorldTime() + minutes * Time.ONE_MINUTE);
    }

    @Test
    void theStartOfTheFlightIsTheStartOfTheDuty() {
        assertEquals(0, person.getDutyStartTime());

        final int startedAt = world.getWorldTime();
        assertTrue(control.start(mission).started());

        assertEquals(startedAt, person.getDutyStartTime());
    }

    @Test
    void theFinishWritesTheDutyToTheLogbookAndEndsIt() {
        final int startedAt = world.getWorldTime();
        assertTrue(control.start(mission).started());
        minutesPass(30);
        control.blocksOff(mission);
        minutesPass(10);
        control.takeoff(mission);
        minutesPass(60);
        control.landing(mission, airportB);
        minutesPass(10);
        control.blocksOn(mission);
        minutesPass(20);
        final int finishedAt = world.getWorldTime();
        control.finish(mission);

        final PilotLogbook.Entry entry = world.pilotLogbook().all().findFirst().orElseThrow();
        assertEquals(startedAt, entry.getDutyStartTime());
        assertEquals(finishedAt, entry.getDutyEndTime());
        // the preflight and the postflight are in the duty: it is longer than the time from blocks off to blocks on
        assertEquals(130, (entry.getDutyEndTime() - entry.getDutyStartTime()) / 60);
        assertEquals(0, person.getDutyStartTime());
    }

    @Test
    void aCancelledFlightEndsTheDuty() {
        assertTrue(control.start(mission).started());
        assertTrue(person.getDutyStartTime() > 0);

        world.personControl().onMissionCancelled(mission, airportA.getId());

        assertEquals(0, person.getDutyStartTime());
    }
}
