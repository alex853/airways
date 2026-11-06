package net.simforge.airways2.app.user;

import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.WorldAccess;
import net.simforge.airways2.world.datamodel.AircraftOperators;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The tape of the flights of the user (GET /flight-dashboard/my-flights): the operator of the aircraft and the carried passengers of a finished flight, which the
 * page of the roster shows.
 */
class DashboardMyFlightsTest {
    private static final int USER_ID = 7;

    private World world;
    private FlightDashboardController controller;
    private Airports.Airport oakland;
    private Airports.Airport sanFrancisco;
    private Aircrafts.Aircraft aircraft;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        oakland = world.airports().create(37.72, -122.22, "OAK", "KOAK", "Oakland");
        sanFrancisco = world.airports().create(37.62, -122.38, "SFO", "KSFO", "San Francisco");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", oakland);

        final WorldRunnerBean worldBean = mock(WorldRunnerBean.class);
        when(worldBean.read(any())).thenAnswer(invocation -> ((WorldAccess.Action<?>) invocation.getArgument(0)).invoke(world));
        controller = new FlightDashboardController();
        ReflectionTestUtils.setField(controller, "worldBean", worldBean);
    }

    private FlightMissions.Mission mission(final int departureTime) {
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(aircraft, oakland, sanFrancisco, departureTime, departureTime + 3600);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        mission.setUserId(USER_ID);
        return mission;
    }

    @Test
    void theOperatorOfTheAircraftIsInTheFlight() {
        final AircraftOperators.AircraftOperator operator = world.aircraftOperators().create("BB", "BBD", "BusyBirds");
        aircraft.setAircraftOperatorId(operator.getId());
        mission(world.getWorldTime() + 3600);

        assertEquals("BusyBirds", controller.getMyFlights(USER_ID).getFlights().get(0).getAircraft().getOperatorName());
    }

    @Test
    void anAircraftWithoutOperatorHasNoOperatorName() {
        mission(world.getWorldTime() + 3600);

        assertNull(controller.getMyFlights(USER_ID).getFlights().get(0).getAircraft().getOperatorName());
    }

    @Test
    void theCarriedPassengersAreToldAfterTheLandingOnly() {
        final FlightMissions.Mission mission = mission(world.getWorldTime() + 3600);
        final TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(mission);
        transportFlight.setPaxCarried(7);

        // the flight is not over and has not landed: the carried passengers are not told, the roster shows the ones on board
        assertNull(controller.getMyFlights(USER_ID).getFlights().get(0).getTransportFlight().getCarriedPax());
        mission.setStatus(FlightMissions.Status.Flying);
        assertNull(controller.getMyFlights(USER_ID).getFlights().get(0).getTransportFlight().getCarriedPax());

        // after the landing the passengers on board go down in the deboarding, so the carried ones are told
        mission.setStatus(FlightMissions.Status.Postflight);
        assertEquals(7, controller.getMyFlights(USER_ID).getFlights().get(0).getTransportFlight().getCarriedPax());

        mission.setStatus(FlightMissions.Status.Finished);

        assertEquals(7, controller.getMyFlights(USER_ID).getFlights().get(0).getTransportFlight().getCarriedPax());
    }

    @Test
    void theIncomeOfTheTransportFlightIsInTheFlightAndZeroWhenThereIsNone() {
        final FlightMissions.Mission mission = mission(world.getWorldTime() + 3600);
        final TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(mission);

        // the flights which were before the income (the production) have no record: 0
        assertEquals(0, controller.getMyFlights(USER_ID).getFlights().get(0).getTransportFlight().getIncome());

        world.transportFlightExtras().create(transportFlight.getId()).setIncome(45_000);

        assertEquals(45_000, controller.getMyFlights(USER_ID).getFlights().get(0).getTransportFlight().getIncome());
    }

    @Test
    void theActualTimesAreToldWhenTheyHaveHappened() {
        final FlightMissions.Mission mission = mission(world.getWorldTime() + 3600);

        assertNull(controller.getMyFlights(USER_ID).getFlights().get(0).getFlight().getActualDeparture());
        assertNull(controller.getMyFlights(USER_ID).getFlights().get(0).getFlight().getActualArrival());

        mission.setActualDepartureWorldTime(world.getWorldTime() + 3480);
        assertEquals((world.getWorldTime() + 3480) * 1000L, controller.getMyFlights(USER_ID).getFlights().get(0).getFlight().getActualDeparture());
        assertNull(controller.getMyFlights(USER_ID).getFlights().get(0).getFlight().getActualArrival());

        mission.setActualArrivalWorldTime(world.getWorldTime() + 7200);
        assertEquals((world.getWorldTime() + 7200) * 1000L, controller.getMyFlights(USER_ID).getFlights().get(0).getFlight().getActualArrival());
    }

    @Test
    void theAirportOfTheLandingIsToldWhenTheFlightHasLandedNotAtItsDestination() {
        final Airports.Airport oroville = world.airports().create(39.49, -121.62, "OVE", "KOVE", "Oroville");
        final FlightMissions.Mission mission = mission(world.getWorldTime() + 3600);

        assertNull(controller.getMyFlights(USER_ID).getFlights().get(0).getFlight().getLandedIcao());

        mission.setActualLandingAirportId(sanFrancisco.getId());
        assertNull(controller.getMyFlights(USER_ID).getFlights().get(0).getFlight().getLandedIcao());

        mission.setActualLandingAirportId(oroville.getId());
        assertEquals("KOVE", controller.getMyFlights(USER_ID).getFlights().get(0).getFlight().getLandedIcao());
    }

    @Test
    void aFlightWhichCanBeMovedHasTheTimesOfTheMoveAndAFlightWhichHasLeftHasNone() {
        final FlightMissions.Mission mission = mission(world.getWorldTime() + 3 * 3600);

        final FlightDashboardController.RescheduleDto reschedule = controller.getMyFlights(USER_ID).getFlights().get(0).getReschedule();
        assertEquals(15, reschedule.getStepMinutes());
        assertEquals(0, reschedule.getFrom() % (15 * 60_000L));
        assertEquals(0, reschedule.getTo() % (15 * 60_000L));

        mission.setActualDepartureWorldTime(world.getWorldTime() + 3480);
        assertNull(controller.getMyFlights(USER_ID).getFlights().get(0).getReschedule());
    }

    @Test
    void theChoicesOfTheMoveTellTheNeighboursAndWhatEachTimeDoesToTheFlightAfter() {
        final int base = world.getWorldTime() + 3 * 3600;
        mission(base);
        // the second flight of the user leaves 100 minutes after the first one lands
        final FlightMissions.Mission second = mission(base + 3600 + 100 * 60);

        final java.util.List<FlightDashboardController.MyFlightsDto> flights = controller.getMyFlights(USER_ID).getFlights();
        final FlightDashboardController.RescheduleDto first = flights.get(0).getReschedule();
        final FlightDashboardController.RescheduleDto last = flights.get(1).getReschedule();

        // the first flight: nothing before it, the second one after it (planned departure), the options are with what they do to the second one
        assertNull(first.getBefore());
        assertEquals("KOAK - KSFO", first.getAfter().getRoute());
        assertEquals(second.getPlannedDepartureWorldTime() * 1000L, first.getAfter().getTime());
        assertEquals(true, first.getOptions().stream().allMatch(o -> o.getNextDep() != null));
        // the last flight: the first one before it, nothing after it, no conflicts in its options
        assertEquals("KOAK - KSFO", last.getBefore().getRoute());
        assertNull(last.getAfter());
        assertEquals(true, last.getOptions().stream().allMatch(o -> o.getNextDep() == null && o.getShortfallMinutes() == 0));
    }

    @Test
    void aFlightWithoutTransportFlightHasNoTransportFlightDto() {
        mission(world.getWorldTime() + 3600);

        assertNull(controller.getMyFlights(USER_ID).getFlights().get(0).getTransportFlight());
    }

    // ---- the warning that the pilot is not expected where a flight departs

    private FlightMissions.Mission missionBetween(final Airports.Airport from, final Airports.Airport to, final int departureTime) {
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(aircraft, from, to, departureTime, departureTime + 3600);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        mission.setUserId(USER_ID);
        return mission;
    }

    private FlightDashboardController.MyFlightsDto flightOf(final FlightMissions.Mission mission) {
        return controller.getMyFlights(USER_ID).getFlights().stream()
                .filter(f -> f.getFlight().getId().equals(net.simforge.airways2.app.tools.Id.encode(mission.getId()))).findFirst().orElseThrow();
    }

    @Test
    void aFlightWhichDepartsWhereTheFlightBeforeLandsHasNoPlaceProblem() {
        final int now = world.getWorldTime();
        missionBetween(oakland, sanFrancisco, now + 3600);
        final FlightMissions.Mission second = missionBetween(sanFrancisco, oakland, now + 6 * 3600);

        assertNull(flightOf(second).getPlaceProblem());
    }

    @Test
    void aFlightWhichDepartsFromAnotherAirportThanTheFlightBeforeLandsHasThePlaceProblem() {
        final int now = world.getWorldTime();
        missionBetween(oakland, sanFrancisco, now + 3600);
        final FlightMissions.Mission second = missionBetween(oakland, sanFrancisco, now + 6 * 3600);

        final net.simforge.airways2.app.dto.PlaceProblemDto problem = flightOf(second).getPlaceProblem();
        assertEquals("KSFO", problem.getExpected());
        assertEquals("KOAK", problem.getDeparture());
        assertEquals("KOAK", problem.getAfterFrom());
        assertEquals("KSFO", problem.getAfterTo());
    }

    @Test
    void theLandingElsewhereMovesTheExpectedPlaceOfThePilot() {
        final int now = world.getWorldTime();
        final Airports.Airport sanJose = world.airports().create(37.36, -121.93, "SJC", "KSJC", "San Jose");
        final FlightMissions.Mission first = missionBetween(oakland, sanFrancisco, now + 3600);
        first.setActualLandingAirportId(sanJose.getId());
        final FlightMissions.Mission fromSanJose = missionBetween(sanJose, oakland, now + 6 * 3600);

        // the flight landed at San Jose (not at its destination): the next flight from San Jose is fine
        assertNull(flightOf(fromSanJose).getPlaceProblem());
    }

    @Test
    void aFlightFromTheDestinationWhichWasNotReachedHasThePlaceProblem() {
        final int now = world.getWorldTime();
        final Airports.Airport sanJose = world.airports().create(37.36, -121.93, "SJC", "KSJC", "San Jose");
        final FlightMissions.Mission first = missionBetween(oakland, sanFrancisco, now + 3600);
        first.setActualLandingAirportId(sanJose.getId());
        final FlightMissions.Mission fromSanFrancisco = missionBetween(sanFrancisco, oakland, now + 6 * 3600);

        // the pilot is at San Jose, where the flight has landed, not at San Francisco where it was going
        assertEquals("KSJC", flightOf(fromSanFrancisco).getPlaceProblem().getExpected());
    }

    @Test
    void beforeTheFirstFlightThePilotIsExpectedWhereItStandsNowIfItStandsAtAnAirport() {
        world.persons().create(net.simforge.airways2.world.datamodel.Persons.Type.Pilot, net.simforge.airways2.world.datamodel.Persons.State.Idle, USER_ID,
                net.simforge.airways2.world.datamodel.Persons.LocationType.Airport, sanFrancisco.getId(), 0, "AK", 0);
        final FlightMissions.Mission first = missionBetween(oakland, sanFrancisco, world.getWorldTime() + 3600);

        final net.simforge.airways2.app.dto.PlaceProblemDto problem = flightOf(first).getPlaceProblem();

        assertEquals("KSFO", problem.getExpected());
        assertNull(problem.getAfterFrom());
        assertNull(problem.getAfterTo());
    }

    @Test
    void beforeTheFirstFlightThePilotInACityOrOnTheWayHasNoPlaceProblem() {
        world.persons().create(net.simforge.airways2.world.datamodel.Persons.Type.Pilot, net.simforge.airways2.world.datamodel.Persons.State.Idle, USER_ID,
                net.simforge.airways2.world.datamodel.Persons.LocationType.None, 0, 0, "AK", 0);
        final FlightMissions.Mission first = missionBetween(oakland, sanFrancisco, world.getWorldTime() + 3600);

        assertNull(flightOf(first).getPlaceProblem());
    }

    @Test
    void afterAFinishedFlightThePilotIsExpectedWhereItStandsNowNotWhereTheFlightLanded() {
        world.persons().create(net.simforge.airways2.world.datamodel.Persons.Type.Pilot, net.simforge.airways2.world.datamodel.Persons.State.Idle, USER_ID,
                net.simforge.airways2.world.datamodel.Persons.LocationType.Hotel, 0, 0, "AK", 0);
        final int now = world.getWorldTime();
        final FlightMissions.Mission finished = missionBetween(oakland, sanFrancisco, now + 1800);
        finished.setStatus(FlightMissions.Status.Finished);
        final FlightMissions.Mission next = missionBetween(sanFrancisco, oakland, now + 3600);

        // the pilot has moved away after the finished flight (warp, a trip): not at San Francisco, not at an airport at all
        final net.simforge.airways2.app.dto.PlaceProblemDto problem = flightOf(next).getPlaceProblem();
        assertNull(problem.getExpected());
        assertEquals("a hotel", problem.getPilotAt());
        assertEquals("KSFO", problem.getDeparture());
        assertNull(problem.getAfterFrom());
    }

    @Test
    void afterAFinishedFlightThePilotAtTheDepartureAirportHasNoPlaceProblem() {
        world.persons().create(net.simforge.airways2.world.datamodel.Persons.Type.Pilot, net.simforge.airways2.world.datamodel.Persons.State.Idle, USER_ID,
                net.simforge.airways2.world.datamodel.Persons.LocationType.Airport, sanFrancisco.getId(), 0, "AK", 0);
        final int now = world.getWorldTime();
        missionBetween(oakland, sanFrancisco, now + 1800).setStatus(FlightMissions.Status.Finished);
        final FlightMissions.Mission next = missionBetween(sanFrancisco, oakland, now + 3600);

        assertNull(flightOf(next).getPlaceProblem());
    }

    @Test
    void aFlightWhichHasStartedAndACancelledFlightBeforeDoNotMakeAPlaceProblem() {
        final int now = world.getWorldTime();
        final FlightMissions.Mission cancelled = missionBetween(oakland, sanFrancisco, now + 3600);
        cancelled.setStatus(FlightMissions.Status.Cancelled);
        final FlightMissions.Mission second = missionBetween(oakland, sanFrancisco, now + 6 * 3600);
        // the cancelled flight before does not count, so the pilot has no flight before: the place is not known
        assertNull(flightOf(second).getPlaceProblem());

        final FlightMissions.Mission third = missionBetween(oakland, sanFrancisco, now + 12 * 3600);
        // the flight before (second) lands at San Francisco, the third departs from Oakland: the problem, until the third has started
        assertEquals("KSFO", flightOf(third).getPlaceProblem().getExpected());
        third.setStatus(FlightMissions.Status.Preflight);
        assertNull(flightOf(third).getPlaceProblem());
    }
}
