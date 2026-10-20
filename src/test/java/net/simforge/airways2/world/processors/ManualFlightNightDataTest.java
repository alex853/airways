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

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The night data of a flight of a user (day or night of the takeoff and of the landing, the night time) are calculated by the flight mission control,
 * whoever gives the aircraft its positions: the simple flight of the world (a manual flight, here it is the processor of the flight missions which flies
 * it), a tracker (it calls moveAircraft). The flights which are not flights of a user are left as they are.
 */
class ManualFlightNightDataTest {
    private World world;
    private FlightMissionControl control;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private FlightMissions.Mission mission;
    private Persons.Person person;

    private static int utc(final int hour, final int minute) {
        return (int) LocalDateTime.of(2025, 6, 21, 0, 0).plusHours(hour).plusMinutes(minute).toEpochSecond(ZoneOffset.UTC);
    }

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), utc(12, 0));
        control = world.flightMissionControl();
        // London, midsummer: the night is from 20:51 to 03:13 UTC
        airportA = world.airports().create(51.5, -0.1, "AAA", "AAAA", "Alpha");
        airportB = world.airports().create(51.5, 0.4, "BBB", "BBBB", "Bravo");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", airportA);
        mission = world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, utc(22, 0), utc(23, 0));
        mission.setUserId(7);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 7,
                Persons.LocationType.Airport, airportA.getId(), 0, "AK", 0);
    }

    // The world goes ahead in ticks of 10 seconds and the processor of the flight missions works on every tick, as it does in the application.
    // trackerPositions: a tracker sets the positions (the flight is tracked), the processor does not fly the aircraft then.
    private void advanceTo(final int worldTime, final boolean trackerPositions) {
        while (world.getWorldTime() < worldTime) {
            world.setWorldTime(world.getWorldTime() + Time.TICK);
            if (trackerPositions) {
                control.moveAircraft(mission, 51.5, 0.15, 90, 0);
            }
            FlightMissionProcessor.process(world);
        }
    }

    private void fly(final FlightMissions.TrackingMode mode, final int offHour, final int offMinute, final int minutes) {
        final boolean tracked = mode == FlightMissions.TrackingMode.SimTracker;
        world.setWorldTime(utc(offHour, offMinute));
        assertTrue(control.start(mission).started());
        control.setTrackingMode(mission, mode);
        if (tracked) {
            control.switchToExternalCoordinatesMode(mission);
        }
        control.blocksOff(mission);
        advanceTo(utc(offHour, offMinute) + 10 * 60, tracked);
        control.takeoff(mission);
        advanceTo(utc(offHour, offMinute) + (minutes - 10) * 60, tracked);
        control.landing(mission, airportB);
        advanceTo(utc(offHour, offMinute) + minutes * 60, tracked);
        control.blocksOn(mission);
        control.finish(mission);
    }

    private PilotLogbook.Entry entry() {
        return world.pilotLogbook().filter(world.pilotLogbook().byPersonId(person.getId())).findFirst().orElseThrow();
    }

    @Test
    void aManualFlightAtNightHasNightTimeAndNightTakeoffAndLanding() {
        fly(FlightMissions.TrackingMode.Manual, 22, 0, 60);

        assertTrue(mission.isNightTakeoff());
        assertTrue(mission.isNightLanding());
        assertEquals(60.0f, mission.getNightMinutes(), 1.0f);

        assertEquals(1, entry().getNightTakeoffs());
        assertEquals(1, entry().getNightLandings());
        assertEquals(1.0f, entry().getNightTime(), 0.11f);
    }

    @Test
    void aManualFlightInTheDayHasNoNightData() {
        fly(FlightMissions.TrackingMode.Manual, 12, 0, 60);

        assertFalse(mission.isNightTakeoff());
        assertFalse(mission.isNightLanding());
        assertEquals(0.0f, mission.getNightMinutes(), 0.0f);
        assertEquals(1, entry().getDayTakeoffs());
        assertEquals(1, entry().getDayLandings());
        assertEquals(0.0f, entry().getNightTime(), 0.0f);
    }

    @Test
    void aManualFlightWhichGoesIntoTheNightCountsTheNightPart() {
        // blocks off 20:30, blocks on 22:30: the night from 20:51 is 99 minutes, the takeoff at 20:40 is day, the landing at 22:20 is night
        fly(FlightMissions.TrackingMode.Manual, 20, 30, 120);

        assertFalse(mission.isNightTakeoff());
        assertTrue(mission.isNightLanding());
        assertEquals(99.0f, mission.getNightMinutes(), 2.0f);
    }

    @Test
    void aFlightWhichATrackerFollowsGetsTheSameNightDataFromTheReportedPositions() {
        fly(FlightMissions.TrackingMode.SimTracker, 22, 0, 60);

        assertTrue(mission.isNightTakeoff());
        assertTrue(mission.isNightLanding());
        assertEquals(60.0f, mission.getNightMinutes(), 1.0f);
        assertEquals(1.0f, entry().getNightTime(), 0.11f);
    }

    @Test
    void aFlightWhichIsNotAFlightOfAUserHasNoNightData() {
        // no user, no logbook: the steps are done by the control directly (the processor would fly an NPC flight itself)
        mission.setUserId(0);
        world.setWorldTime(utc(22, 0));
        assertTrue(control.start(mission).started());
        control.blocksOff(mission);
        world.setWorldTime(utc(22, 10));
        control.takeoff(mission);
        world.setWorldTime(utc(22, 50));
        control.landing(mission, airportB);
        world.setWorldTime(utc(23, 0));
        control.blocksOn(mission);

        assertFalse(mission.isNightTakeoff());
        assertFalse(mission.isNightLanding());
        assertEquals(0.0f, mission.getNightMinutes(), 0.0f);
    }

    @Test
    void theNightTimeGoesOnFromTheValueOfTheFlightMissionAfterARestart() {
        // the control has no meter for the flight (the application was restarted), the flight mission has kept 30 minutes
        mission.setStatus(FlightMissions.Status.Flying);
        mission.setNightMinutes(30.0f);
        world.setWorldTime(utc(23, 0));

        control.countNightTime(mission, 51.5, 0.0);
        assertEquals(30.0f, mission.getNightMinutes(), 0.0f); // the first report only starts the counting

        world.setWorldTime(utc(23, 0) + 60);
        control.countNightTime(mission, 51.5, 0.0);
        assertEquals(31.0f, mission.getNightMinutes(), 0.01f);
    }

    @Test
    void thereIsNoNightTimeBeforeBlocksOffAndAfterBlocksOn() {
        world.setWorldTime(utc(23, 0));

        control.countNightTime(mission, 51.5, 0.0); // Dispatched

        assertEquals(0.0f, mission.getNightMinutes(), 0.0f);
    }
}
