package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PilotPayParams;
import net.simforge.airways2.world.datamodel.AircraftOperators;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pilot has landed at a wrong airport and cannot fly on now: the passengers get off, the same aircraft takes them on later in a new flight, and the other flights of the pilot
 * which have not left go after it (RosterCascade).
 */
class ContinueLaterTest {
    private static final int INCOME = 10_000;
    private static final int MINUTE = Time.ONE_MINUTE;

    private World world;
    private FlightMissionControl control;
    private Airports.Airport departure;
    private Airports.Airport destination;
    private Airports.Airport diversion;
    private Aircrafts.Aircraft aircraft;
    private Persons.Person person;
    private FlightMissions.Mission mission;
    private TransportFlights.Flight transportFlight;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        control = world.flightMissionControl();

        final AircraftOperators.AircraftOperator operator = world.aircraftOperators().create("BB", "BBD", "busyBirds");
        departure = world.airports().create(51.5, -0.1, "AAA", "AAAA", "Alpha");
        destination = world.airports().create(51.5, 4.0, "BBB", "BBBB", "Bravo");
        diversion = world.airports().create(51.8, 2.0, "DDD", "DDDD", "Delta");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", departure);
        aircraft.setAircraftOperatorId(operator.getId());
        final int departureTime = world.getWorldTime() + Time.ONE_HOUR;
        mission = world.flightMissions().createDispatchedMission(aircraft, departure, destination, departureTime, departureTime + Time.ONE_HOUR);
        mission.setUserId(7);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 7, Persons.LocationType.Airport, departure.getId(), 0, "AK", 0);
        world.pilotContracts().create(mission.getId(), operator.getId(), person.getId(), 2_000, PilotPayParams.none());

        transportFlight = world.transportFlightControl().createTransportFlight(mission);
        world.transportFlightControl().addIncome(transportFlight, INCOME);
    }

    private void flyTo(final FlightMissions.Mission m, final Airports.Airport landingAirport) {
        assertTrue(control.start(m).started());
        control.setTrackingMode(m, FlightMissions.TrackingMode.Manual);
        control.blocksOff(m);
        control.takeoff(m);
        control.landing(m, landingAirport);
        control.blocksOn(m);
    }

    // a flight of the pilot which has not left, in whole minutes from now
    private FlightMissions.Mission pending(final int fromNowMinutes, final int durationMinutes) {
        final int dep = world.getWorldTime() + fromNowMinutes * MINUTE;
        final FlightMissions.Mission m = world.flightMissions().createDispatchedMission(aircraft, destination, departure, dep, dep + durationMinutes * MINUTE);
        m.setUserId(7);
        m.setCharacterMode(FlightMissions.CharacterMode.PC);
        world.transportFlightControl().createTransportFlight(m);
        return m;
    }

    private int later(final int minutes) {
        return world.getWorldTime() + minutes * MINUTE;
    }

    @Test
    void theNewFlightIsADispatchedContinuationOfTheSamePilotAndAircraftWithATransportFlightOfItsOwn() {
        flyTo(mission, diversion);

        final FlightMissions.Mission next = control.continueLater(mission, later(300));

        assertEquals(FlightMissions.Status.Dispatched, next.getStatus());
        assertEquals(aircraft.getId(), next.getAircraftId());
        assertEquals(diversion.getId(), next.getDepartureAirportId());
        assertEquals(destination.getId(), next.getDestinationAirportId());
        assertEquals(7, next.getUserId());
        assertEquals(later(300), next.getPlannedDepartureWorldTime());
        assertTrue(next.isContinuation());
        assertTrue(world.pilotContracts().byFlightMissionId(next.getId()).isPresent());

        final TransportFlights.Flight nextFlight = world.transportFlights().byFlightMissionId(next.getId()).orElseThrow();
        assertNotEquals(transportFlight.getId(), nextFlight.getId());
        // the passengers of the first flight get off here
        assertEquals(TransportFlights.Status.Deboarding, transportFlight.getStatus());
        assertEquals(FlightMissions.Status.Postflight, mission.getStatus());
    }

    @Test
    void theJourneysAreOnTheNewFlightAsTheSecondFlightAndItsIncomeIsThePriceOfTheirTrip() {
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.OnBoard, 1, 2, 2, CabinLayout.Service.Y);
        journey.setTransportFlight1Id(transportFlight.getId());
        journey.setBookedCabinService(CabinLayout.Service.Y);
        journey.setPrice1(INCOME);
        flyTo(mission, diversion);

        final FlightMissions.Mission next = control.continueLater(mission, later(300));

        final TransportFlights.Flight nextFlight = world.transportFlights().byFlightMissionId(next.getId()).orElseThrow();
        assertEquals(transportFlight.getId(), journey.getTransportFlight1Id());
        assertEquals(nextFlight.getId(), journey.getTransportFlight2Id());
        assertEquals(INCOME, journey.getPrice2());
        assertEquals(INCOME, world.transportFlightControl().getIncome(nextFlight));
        assertEquals(2, nextFlight.getTotalTickets().getTotal() - nextFlight.getRemainedTickets().getTotal());
        assertEquals(Journeys.Status.WaitingForDeboarding, journey.getStatus());
        // the first flight did not deliver them: it is not paid
        assertFalse(world.transportFlightControl().isDelivered(transportFlight));
    }

    @Test
    void aJourneyWhichHadASecondFlightLosesItAndGetsTheNewOne() {
        final FlightMissions.Mission other = pending(60 * 24, 60);
        final TransportFlights.Flight otherFlight = world.transportFlights().byFlightMissionId(other.getId()).orElseThrow();
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.OnBoard, 1, 2, 2, CabinLayout.Service.Y);
        journey.setTransportFlight1Id(transportFlight.getId());
        journey.setBookedCabinService(CabinLayout.Service.Y);
        journey.setPrice1(4_000);
        world.transportFlightControl().obtainFlightTickets(otherFlight, 2, CabinLayout.Service.Y);
        journey.setTransportFlight2Id(otherFlight.getId());
        journey.setPrice2(3_000);
        world.transportFlightControl().addIncome(otherFlight, 3_000);
        flyTo(mission, diversion);

        final FlightMissions.Mission next = control.continueLater(mission, later(300));

        final TransportFlights.Flight nextFlight = world.transportFlights().byFlightMissionId(next.getId()).orElseThrow();
        assertEquals(nextFlight.getId(), journey.getTransportFlight2Id());
        assertEquals(4_000, journey.getPrice2());
        assertEquals(0, world.transportFlightControl().getIncome(otherFlight));
        assertEquals(otherFlight.getTotalTickets().getTotal(), otherFlight.getRemainedTickets().getTotal());
    }

    @Test
    void theFlightsOfThePilotWhichHaveNotLeftGoAfterTheNewOneWithTheLeastGaps() {
        final FlightMissions.Mission second = pending(120, 60);
        final FlightMissions.Mission third = pending(210, 60);
        // two days later: the new flight is over by then, it is not touched
        final FlightMissions.Mission farAway = pending(48 * 60, 60);
        final int farAwayDeparture = farAway.getPlannedDepartureWorldTime();
        flyTo(mission, diversion);

        final FlightMissions.Mission next = control.continueLater(mission, later(300));

        final int gap = ShadowJetBooking.MIN_GAP_SECONDS;
        assertTrue(second.getPlannedDepartureWorldTime() >= next.getPlannedArrivalWorldTime() + gap);
        assertEquals(0, second.getPlannedDepartureWorldTime() % RosterCascade.STEP);
        assertTrue(second.getPlannedDepartureWorldTime() < next.getPlannedArrivalWorldTime() + gap + RosterCascade.STEP);
        assertTrue(third.getPlannedDepartureWorldTime() >= second.getPlannedArrivalWorldTime() + gap);
        assertTrue(third.getPlannedDepartureWorldTime() < second.getPlannedArrivalWorldTime() + gap + RosterCascade.STEP);
        assertEquals(farAwayDeparture, farAway.getPlannedDepartureWorldTime());
        // the flights keep their duration
        assertEquals(60 * MINUTE, second.getPlannedArrivalWorldTime() - second.getPlannedDepartureWorldTime());
    }

    @Test
    void aFlightWhichIsLaterThanTheCascadeNeedsStaysWhereItIs() {
        final FlightMissions.Mission late = pending(24 * 60, 60);
        final int lateDeparture = late.getPlannedDepartureWorldTime();
        flyTo(mission, diversion);

        final RosterCascade.Plan plan = control.continueLaterCascade(mission, later(300));

        assertTrue(plan.isPossible());
        assertTrue(plan.moves().isEmpty());
        assertEquals(lateDeparture, late.getPlannedDepartureWorldTime());
    }

    @Test
    void theCascadeTellsTheConnectionsWhichAreLost() {
        final FlightMissions.Mission second = pending(120, 60);
        final TransportFlights.Flight secondFlight = world.transportFlights().byFlightMissionId(second.getId()).orElseThrow();
        // a journey which comes by the first flight and goes on by the second one (the moved flight): after the move there is no time for the connection
        final FlightMissions.Mission before = pending(10, 30);
        final TransportFlights.Flight beforeFlight = world.transportFlights().byFlightMissionId(before.getId()).orElseThrow();
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.WaitingForCheckIn, 1, 2, 3, CabinLayout.Service.Y);
        journey.setTransportFlight1Id(beforeFlight.getId());
        journey.setTransportFlight2Id(secondFlight.getId());
        flyTo(mission, diversion);

        final RosterCascade.Plan plan = control.continueLaterCascade(mission, later(300));

        final RosterCascade.Move moved = plan.moves().stream().filter(m -> m.mission().getId() == second.getId()).findFirst().orElseThrow();
        // the flight before it is moved too, the connection is kept when both of them are moved by the same shift
        assertTrue(moved.connectionsLost() == 0 || moved.connectionsLost() == 1);
        assertEquals(moved.connectionsLost() * 3, moved.connectionPaxLost());
    }

    @Test
    void theFlightIsRefusedWhenTheFlightsAfterItCannotBeMovedFarEnough() {
        pending(60, 60);
        flyTo(mission, diversion);
        final int farFromNow = FlightMissionControl.RESCHEDULE_MAX_SHIFT_MINUTES - 5;

        final RosterCascade.Plan plan = control.continueLaterCascade(mission, later(farFromNow));

        assertFalse(plan.isPossible());
        assertEquals(1, plan.problems().size());
        assertThrows(IllegalArgumentException.class, () -> control.continueLater(mission, later(farFromNow)));
        // nothing was changed
        assertEquals(FlightMissions.Status.Postflight, mission.getStatus());
        assertEquals(TransportFlights.Status.WaitingForDeboarding, transportFlight.getStatus());
    }

    @Test
    void theDepartureIsNotEarlierThanThreeHoursFromNow() {
        flyTo(mission, diversion);

        assertThrows(IllegalArgumentException.class, () -> control.continueLater(mission, later(179)));
        control.continueLater(mission, later(180));
    }

    @Test
    void theDepartureIsInWholeMinutesAndNotFartherThanTheLimit() {
        flyTo(mission, diversion);

        assertThrows(IllegalArgumentException.class, () -> control.continueLater(mission, later(300) + 30));
        assertThrows(IllegalArgumentException.class, () -> control.continueLater(mission, later(FlightMissionControl.RESCHEDULE_MAX_SHIFT_MINUTES + 1)));
    }

    @Test
    void itIsRefusedWhenTheFlightHasLandedAtTheDestination() {
        flyTo(mission, destination);

        assertThrows(IllegalArgumentException.class, () -> control.continueLater(mission, later(300)));
    }

    @Test
    void theCascadeDoesNotTouchTheMissionsWhichAreExcluded() {
        final FlightMissions.Mission second = pending(120, 60);
        flyTo(mission, diversion);
        final int arrival = control.continueLaterArrival(mission, later(300));

        final RosterCascade.Plan plan = RosterCascade.plan(world, 7, arrival, Set.of(mission.getId(), second.getId()));

        assertTrue(plan.moves().isEmpty());
    }
}
