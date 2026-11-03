package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A flight of a user is moved in time (FlightMissionControl.reschedule) in every state of its transport flight before the departure, and the passengers stay with the flight.
 */
class FlightRescheduleTest {
    private static final int USER_ID = 1;

    private World world;
    private TransportFlightControl tfControl;
    private FlightMissionControl control;
    private Aircrafts.Aircraft aircraft;
    private Airports.Airport airportA;
    private Airports.Airport airportB;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        tfControl = world.transportFlightControl();
        control = world.flightMissionControl();

        airportA = world.airports().create(51.28, -0.76, "FRM", "EGLF", "Farnborough");
        airportB = world.airports().create(52.36, 13.5, "TOO", "EDDB", "Berlin");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", airportA);
    }

    // a flight of a user which departs in three hours and flies two hours
    private FlightMissions.Mission mission(final int departureInHours) {
        final int departure = world.getWorldTime() + departureInHours * Time.ONE_HOUR;
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, departure, departure + 2 * Time.ONE_HOUR);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        mission.setUserId(USER_ID);
        return mission;
    }

    private Journeys.Journey journey(final TransportFlights.Flight tf, final Journeys.Status status, final int heartbeat) {
        final Journeys.Journey journey = world.journeys().create(status, 1, 2, 2, CabinLayout.Service.Y);
        journey.setTransportFlight1Id(tf.getId());
        journey.setHeartbeatTime(heartbeat);
        return journey;
    }

    @Test
    void aFlightBeforeTheCheckinStartsTheCheckinAtTheNewTime() {
        final FlightMissions.Mission mission = mission(3);
        final TransportFlights.Flight tf = tfControl.createTransportFlight(mission);
        final Journeys.Journey waiting = journey(tf, Journeys.Status.WaitingForCheckIn, 0);
        final int oldCheckin = TransportFlightHelper.calcCheckinStartTime(mission);

        control.reschedule(mission, 120);

        final int newCheckin = TransportFlightHelper.calcCheckinStartTime(mission);
        assertEquals(oldCheckin + 2 * Time.ONE_HOUR, newCheckin);
        assertEquals(TransportFlights.Status.Scheduled, tf.getStatus());
        assertEquals(newCheckin, tf.getHeartbeatTime());
        assertTrue(waiting.getHeartbeatTime() >= newCheckin && waiting.getHeartbeatTime() <= TransportFlightHelper.calcCheckinEndTime(mission));
        // the old moment of the check-in comes and the check-in does not start
        world.setWorldTime(oldCheckin + Time.ONE_MINUTE);
        assertEquals(false, tfControl.ifCheckInTimeComes(tf));
    }

    @Test
    void aFlightInCheckinStaysInCheckinAndTheCheckedInStayCheckedIn() {
        final FlightMissions.Mission mission = mission(3);
        final TransportFlights.Flight tf = tfControl.createTransportFlight(mission);
        world.setWorldTime(TransportFlightHelper.calcCheckinStartTime(mission) + 10 * Time.ONE_MINUTE);
        tfControl.startCheckIn(tf);
        final Journeys.Journey checkedIn = journey(tf, Journeys.Status.WaitingForBoarding, 12345);
        final Journeys.Journey waiting = journey(tf, Journeys.Status.WaitingForCheckIn, 0);
        final int oldCheckinEnd = TransportFlightHelper.calcCheckinEndTime(mission);

        control.reschedule(mission, 60);

        assertEquals(TransportFlights.Status.CheckIn, tf.getStatus());
        assertEquals(Journeys.Status.WaitingForBoarding, checkedIn.getStatus());
        assertEquals(12345, checkedIn.getHeartbeatTime());
        assertTrue(waiting.getHeartbeatTime() >= world.getWorldTime());
        // the old end of the check-in comes and the check-in is still open
        world.setWorldTime(oldCheckinEnd + Time.ONE_MINUTE);
        assertEquals(false, tfControl.isCheckInFinishTimePassed(tf));
    }

    @Test
    void aFlightWhichWaitsForBoardingKeepsItsPassengersAndIsNotReopened() {
        assertThePassengersStay(TransportFlights.Status.WaitingForBoarding);
    }

    @Test
    void aFlightInBoardingKeepsItsPassengers() {
        assertThePassengersStay(TransportFlights.Status.Boarding);
    }

    @Test
    void aBoardedFlightWhichWaitsForDepartureKeepsItsPassengers() {
        assertThePassengersStay(TransportFlights.Status.WaitingForDeparture);
    }

    private void assertThePassengersStay(final TransportFlights.Status status) {
        final FlightMissions.Mission mission = mission(3);
        final TransportFlights.Flight tf = tfControl.createTransportFlight(mission);
        world.setWorldTime(TransportFlightHelper.calcCheckinStartTime(mission) + 10 * Time.ONE_MINUTE);
        tf.setStatus(status);
        mission.setStatus(FlightMissions.Status.Preflight);
        final Journeys.Status onBoardStatus = status == TransportFlights.Status.WaitingForDeparture ? Journeys.Status.OnBoard : Journeys.Status.WaitingForBoarding;
        final Journeys.Journey journey = journey(tf, onBoardStatus, 777);
        final int departure = mission.getPlannedDepartureWorldTime();

        control.reschedule(mission, 90);

        assertEquals(departure + 90 * Time.ONE_MINUTE, mission.getPlannedDepartureWorldTime());
        assertEquals(status, tf.getStatus());
        assertEquals(onBoardStatus, journey.getStatus());
        assertEquals(777, journey.getHeartbeatTime());
        assertEquals(tf.getId(), journey.getTransportFlight1Id());
    }

    @Test
    void aFlightWhichIsAlreadyMovingCanNotBeMoved() {
        final FlightMissions.Mission mission = mission(3);
        final TransportFlights.Flight tf = tfControl.createTransportFlight(mission);
        tf.setStatus(TransportFlights.Status.Departure);

        assertThrows(IllegalArgumentException.class, () -> control.reschedule(mission, 60));
        assertEquals(false, control.rescheduleChecks(mission).get("transport-flight-status-check"));
    }

    @Test
    void aFlightWhichIsNotAFlightOfAUserCanNotBeMoved() {
        final FlightMissions.Mission mission = mission(3);
        mission.setCharacterMode(FlightMissions.CharacterMode.NPC);
        mission.setUserId(0);

        assertThrows(IllegalArgumentException.class, () -> control.reschedule(mission, 60));
        assertEquals(false, control.rescheduleChecks(mission).get("user-flight-check"));
    }

    // the first flight departs in 3 hours and lands in 5, the second one departs in 10: the connection needs more than 4 hours between the landing and the departure
    private record Connection(FlightMissions.Mission mission1, TransportFlights.Flight tf1, FlightMissions.Mission mission2, TransportFlights.Flight tf2, Journeys.Journey journey) {
    }

    private Connection connection() {
        final FlightMissions.Mission mission1 = mission(3);
        final TransportFlights.Flight tf1 = tfControl.createTransportFlight(mission1, CabinLayout.Y(99));
        final FlightMissions.Mission mission2 = mission(10);
        final TransportFlights.Flight tf2 = tfControl.createTransportFlight(mission2, CabinLayout.Y(99));
        final Journeys.Journey journey = journey(tf1, Journeys.Status.WaitingForCheckIn, 0);
        world.journeyControl().bookStopoverFlightsJourneyNoChecks(journey, tf1, tf2);
        assertTrue(tfControl.getIncome(tf2) > 0);
        return new Connection(mission1, tf1, mission2, tf2, journey);
    }

    @Test
    void aJourneyKeepsItsSecondFlightWhenTheConnectionSurvivesTheMove() {
        final Connection c = connection();
        final int remained = c.tf2.getRemainedTickets().getTotal();
        final int income = tfControl.getIncome(c.tf2);

        // landing at 5:50, the connection is until 9:50, the second flight departs at 10:00
        control.reschedule(c.mission1, 50);

        assertEquals(c.tf2.getId(), c.journey.getTransportFlight2Id());
        assertEquals(remained, c.tf2.getRemainedTickets().getTotal());
        assertEquals(income, tfControl.getIncome(c.tf2));
    }

    @Test
    void aJourneyLosesItsSecondFlightWhenTheMovedFirstFlightLandsTooLate() {
        final Connection c = connection();
        final int remained = c.tf2.getRemainedTickets().getTotal();

        control.reschedule(c.mission1, 120);

        // the journey stays on the first flight, the second one is released: the seats and the income of it
        assertEquals(c.tf1.getId(), c.journey.getTransportFlight1Id());
        assertEquals(0, c.journey.getTransportFlight2Id());
        assertEquals(0, c.journey.getPrice2());
        assertTrue(c.journey.getPrice1() > 0);
        assertEquals(remained + c.journey.getGroupSize(), c.tf2.getRemainedTickets().getTotal());
        assertEquals(0, tfControl.getIncome(c.tf2));
        assertEquals(Journeys.Status.WaitingForCheckIn, c.journey.getStatus());
    }

    @Test
    void aJourneyLosesItsSecondFlightWhenTheSecondFlightIsMovedEarlierThanTheConnection() {
        final Connection c = connection();

        control.reschedule(c.mission2, -90);

        assertEquals(c.tf1.getId(), c.journey.getTransportFlight1Id());
        assertEquals(0, c.journey.getTransportFlight2Id());
        assertEquals(0, tfControl.getIncome(c.tf2));
    }

    @Test
    void theSecondFlightMovedLaterKeepsTheConnection() {
        final Connection c = connection();

        control.reschedule(c.mission2, 120);

        assertEquals(c.tf2.getId(), c.journey.getTransportFlight2Id());
    }

    @Test
    void theFlightCanBeMovedByTwoDaysAtMost() {
        final FlightMissions.Mission mission = mission(3);
        tfControl.createTransportFlight(mission);
        final int departure = mission.getPlannedDepartureWorldTime();

        control.reschedule(mission, 48 * 60);
        assertEquals(departure + 2 * Time.ONE_DAY, mission.getPlannedDepartureWorldTime());

        assertThrows(IllegalArgumentException.class, () -> control.reschedule(mission, 48 * 60 + 5));
    }

    @Test
    void theFlightCanBeMovedEarlierButNotCloserThanTheReserve() {
        final FlightMissions.Mission mission = mission(3);
        tfControl.createTransportFlight(mission);
        final int departure = mission.getPlannedDepartureWorldTime();

        control.reschedule(mission, -60);
        assertEquals(departure - Time.ONE_HOUR, mission.getPlannedDepartureWorldTime());

        assertThrows(IllegalArgumentException.class, () -> control.reschedule(mission, -150));
    }

    @Test
    void theFlightsOfThePilotWhichTheMovedFlightNowOverlapsAreTheResult() {
        final FlightMissions.Mission first = mission(3);
        tfControl.createTransportFlight(first);
        // the second flight departs in 6 hours: three hours after the arrival of the first one
        final FlightMissions.Mission second = mission(6);
        tfControl.createTransportFlight(second);
        final FlightMissions.Mission finished = mission(6);
        finished.setStatus(FlightMissions.Status.Finished);

        // 20 minutes later the arrival is at 5:20 and the second flight departs at 6:00, the gap of 30 minutes is kept
        assertEquals(List.of(), ids(control.reschedule(first, 20)));
        assertEquals(List.of(second.getId()), ids(control.reschedule(first, 120)));
        assertEquals(List.of(first.getId()), ids(control.rosterConflicts(second)));
    }

    private static List<Integer> ids(final List<FlightMissions.Mission> missions) {
        return missions.stream().map(FlightMissions.Mission::getId).toList();
    }
}
