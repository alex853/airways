package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The auto finish: the setting of the pilot is copied to the flight when it starts, and the flight of the pilot which has come to its end (the aircraft is at the
 * gate, the passengers have got off or there are none) is finished by itself; without the setting it waits for the button as before.
 */
class AutoFinishTest {
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
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 7, Persons.LocationType.Airport, airportA.getId(), 0, "AK", 0);
    }

    // the flight is at the gate of the destination: the passengers (if there are any) have to get off now
    private void flyUntilBlocksOn() {
        assertTrue(control.start(mission).started());
        control.blocksOff(mission);
        control.takeoff(mission);
        control.landing(mission, airportB);
        control.blocksOn(mission);
        assertEquals(FlightMissions.Status.Postflight, mission.getStatus());
    }

    private void processorRuns() {
        world.setWorldTime(world.getWorldTime() + 10 * Time.TICK);
        FlightMissionProcessor.process(world);
    }

    // ---- the bits

    @Test
    void theSettingOfThePilotIsOffByDefaultAndIsIndependentOfTheWarpDriveAndTheInitials() {
        assertFalse(person.isAutoFinish());

        person.setAutoFinish(true);
        assertTrue(person.isAutoFinish());
        assertFalse(person.isWarpDrive());
        assertEquals("AK", person.getInitials());

        person.setWarpDrive(true);
        person.setInitials("zZ");
        assertTrue(person.isAutoFinish());

        person.setAutoFinish(false);
        assertFalse(person.isAutoFinish());
        assertTrue(person.isWarpDrive());
        assertEquals("zZ", person.getInitials());
    }

    @Test
    void theBitOfTheFlightIsIndependentOfTheStatusAndTheCharacterMode() {
        assertFalse(mission.isAutoFinish());

        mission.setAutoFinish(true);
        mission.setStatus(FlightMissions.Status.Flying);
        mission.setCharacterMode(FlightMissions.CharacterMode.NPC);

        assertTrue(mission.isAutoFinish());
        assertEquals(FlightMissions.Status.Flying, mission.getStatus());
        assertEquals(FlightMissions.CharacterMode.NPC, mission.getCharacterMode());

        mission.setAutoFinish(false);
        assertEquals(FlightMissions.Status.Flying, mission.getStatus());
        assertEquals(FlightMissions.CharacterMode.NPC, mission.getCharacterMode());
    }

    // ---- the copy at the start

    @Test
    void theSettingIsCopiedToTheFlightWhenItStarts() {
        person.setAutoFinish(true);

        assertTrue(control.start(mission).started());

        assertTrue(mission.isAutoFinish());
    }

    @Test
    void aPilotWithoutTheSettingStartsAFlightWithoutIt() {
        assertTrue(control.start(mission).started());

        assertFalse(mission.isAutoFinish());
    }

    @Test
    void aChangeOfTheSettingInTheMiddleOfTheFlightDoesNotChangeTheFlight() {
        person.setAutoFinish(true);
        assertTrue(control.start(mission).started());

        person.setAutoFinish(false);

        assertTrue(mission.isAutoFinish());
    }

    // ---- the finish

    @Test
    void aFlightWithTheAutoFinishIsFinishedByItselfAtTheEndWhenThereAreNoPassengers() {
        person.setAutoFinish(true);
        flyUntilBlocksOn();

        processorRuns();

        assertEquals(FlightMissions.Status.Finished, mission.getStatus());
        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(airportB.getId(), person.getLocationId());
    }

    @Test
    void aFlightWithoutTheAutoFinishWaitsForTheButton() {
        flyUntilBlocksOn();

        processorRuns();
        processorRuns();

        assertEquals(FlightMissions.Status.Postflight, mission.getStatus());
        assertEquals(Persons.State.OnDuty, person.getState());
    }

    @Test
    void theFlightIsNotFinishedBeforeThePassengersHaveGotOff() {
        person.setAutoFinish(true);
        final TransportFlights.Flight tf = world.transportFlightControl().createTransportFlight(mission);
        flyUntilBlocksOn();

        // the passengers are waiting for the deboarding, which the pilot has not started
        tf.setStatus(TransportFlights.Status.WaitingForDeboarding);
        processorRuns();
        assertEquals(FlightMissions.Status.Postflight, mission.getStatus());

        tf.setStatus(TransportFlights.Status.Deboarding);
        processorRuns();
        assertEquals(FlightMissions.Status.Postflight, mission.getStatus());

        // the deboarding is over: the flight is finished by itself
        tf.setStatus(TransportFlights.Status.Finished);
        processorRuns();
        assertEquals(FlightMissions.Status.Finished, mission.getStatus());
        assertEquals(Persons.State.Idle, person.getState());
    }

    @Test
    void theFlightIsNotFinishedBeforeItIsAtTheGate() {
        person.setAutoFinish(true);
        assertTrue(control.start(mission).started());
        control.blocksOff(mission);
        control.takeoff(mission);
        control.landing(mission, airportB);
        // landed, but not at the gate yet (Arrival): the button of Blocks on is still ahead

        processorRuns();

        assertEquals(FlightMissions.Status.Arrival, mission.getStatus());
    }
}
