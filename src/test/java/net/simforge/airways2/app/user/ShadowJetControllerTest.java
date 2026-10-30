package net.simforge.airways2.app.user;

import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.WorldAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShadowJetControllerTest {
    private World world;
    private ShadowJetController controller;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);

        final WorldRunnerBean worldBean = mock(WorldRunnerBean.class);
        when(worldBean.read(any())).thenAnswer(invocation -> ((WorldAccess.Action<?>) invocation.getArgument(0)).invoke(world));

        controller = new ShadowJetController();
        ReflectionTestUtils.setField(controller, "worldBean", worldBean);
    }

    @Test
    public void aircraftTypesAreTheTypesWithPerformanceDataSortedByIcao() {
        world.aircraftTypes().create("B738", "738");
        world.aircraftTypes().create("ZZZZ", null); // no performance data: the time of the arrival cannot be calculated
        world.aircraftTypes().create("A320", "320");

        final List<ShadowJetController.AircraftTypeDto> types = controller.getAircraftTypes();

        assertEquals(List.of("A320", "B738"), types.stream().map(ShadowJetController.AircraftTypeDto::getIcao).toList());
    }

    private void airportsAndType() {
        world.aircraftTypes().create("A320", "320");
        world.airports().create(51.47, -0.45, "LHR", "EGLL", "Heathrow");
        world.airports().create(51.15, -0.19, "LGW", "EGKK", "Gatwick");
        world.airports().create(40.64, -73.78, "JFK", "KJFK", "New York");
    }

    private static final int USER_ID = 7;
    private int aircraftCounter;

    // a flight of the user which is planned: dispatched, the times in the world seconds
    private FlightMissions.Mission plannedFlight(final String from, final String to, final int departure, final int hours) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(world.aircraftTypes().byIcao("A320").orElseThrow(), "SJ-A" + (++aircraftCounter), world.airports().byIcao(from).orElseThrow());
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(
                aircraft, world.airports().byIcao(from).orElseThrow(), world.airports().byIcao(to).orElseThrow(), departure, departure + hours * 3600);
        mission.setUserId(USER_ID);
        return mission;
    }

    // a departure on a whole minute, in the future of the world, in UTC milliseconds
    private long departureAfterHours(final int hours) {
        return (world.getWorldTime() / 60 * 60 + hours * 3600L) * 1000L;
    }

    @Test
    public void planHasTheArrivalWhichIsTheDepartureAndTheDurationAndGrowsWithTheDistance() {
        airportsAndType();
        final long departure = departureAfterHours(1);

        final ShadowJetController.FlightPlanDto shortFlight = controller.planFlight(USER_ID, "A320", "EGLL", "EGKK", departure);
        final ShadowJetController.FlightPlanDto longFlight = controller.planFlight(USER_ID, "A320", "EGLL", "KJFK", departure);

        // the taxi before the takeoff and after the landing are in it: 10 + 10 minutes at least
        assertTrue(shortFlight.getDurationSeconds() > 20 * 60);
        assertEquals(departure + shortFlight.getDurationSeconds() * 1000L, shortFlight.getArrivalTime());
        assertTrue(longFlight.getDurationSeconds() > shortFlight.getDurationSeconds() + 3 * 3600);
        assertTrue(longFlight.getDistance() > 2900 && longFlight.getDistance() < 3100, "distance in nm: " + longFlight.getDistance());
    }

    @Test
    public void planDurationIsTheDurationOfTheDispatchedMission() {
        airportsAndType();
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(world.aircraftTypes().byIcao("A320").orElseThrow(), "SJ-BBB", world.airports().byIcao("EGLL").orElseThrow());

        // a mission keeps the minutes, so the departure is on a whole minute
        final int departure = world.getWorldTime() / 60 * 60 + 3600;
        final FlightMissions.Mission mission = FlightMissionHelper.scheduleDispatchedMission(
                world, aircraft, world.airports().byIcao("EGLL").orElseThrow(), world.airports().byIcao("KJFK").orElseThrow(), departure);

        assertEquals(mission.getPlannedArrivalWorldTime() - mission.getPlannedDepartureWorldTime(),
                controller.planFlight(USER_ID, "A320", "EGLL", "KJFK", departure * 1000L).getDurationSeconds());
    }

    @Test
    public void planHasTheExpectedLoadByTheTimeBeforeTheDeparture() {
        airportsAndType();

        final ShadowJetController.ExpectedLoadDto inHalfAnHour = controller.planFlight(USER_ID, "A320", "EGLL", "EGKK", departureAfterHours(0) + 30 * 60_000L).getExpectedLoad();
        assertEquals(20, inHalfAnHour.getLoadFromPercent());
        assertEquals(25, inHalfAnHour.getLoadToPercent());
        // the approximate income: the seats of the A320 (J12 / Y144 is 156) * 22.5% * the economy price on the distance
        final double distance = net.simforge.commons.misc.Geo.distance(world.airports().byIcao("EGLL").orElseThrow().getCoords(), world.airports().byIcao("EGKK").orElseThrow().getCoords());
        final long expectedIncome = Math.round(156 * 0.225 * net.simforge.airways2.world.computations.ShadowJetPricing.price(distance, 1, net.simforge.airways2.tools.CabinLayout.Service.Y));
        assertEquals(expectedIncome, (long) inHalfAnHour.getIncome());

        // three hours before the departure (a bit less, the departure is on a whole minute): the figures between the hour and the six hours
        final ShadowJetController.ExpectedLoadDto inThreeHours = controller.planFlight(USER_ID, "A320", "EGLL", "EGKK", departureAfterHours(3)).getExpectedLoad();
        assertEquals(28, inThreeHours.getLoadFromPercent());
        assertEquals(35, inThreeHours.getLoadToPercent());
        // twelve hours: the points of the sales team are 70 to 80
        final ShadowJetController.ExpectedLoadDto inTwelveHours = controller.planFlight(USER_ID, "A320", "EGLL", "EGKK", departureAfterHours(12)).getExpectedLoad();
        assertEquals(70, inTwelveHours.getLoadFromPercent());
        assertEquals(80, inTwelveHours.getLoadToPercent());
        // further than a day it is the figures of a day
        final ShadowJetController.ExpectedLoadDto inAndAHalfDays = controller.planFlight(USER_ID, "A320", "EGLL", "EGKK", departureAfterHours(36)).getExpectedLoad();
        assertEquals(80, inAndAHalfDays.getLoadFromPercent());
        assertEquals(100, inAndAHalfDays.getLoadToPercent());
    }

    @Test
    public void theSlotIsFreeWhenThePilotHasNoFlights() {
        airportsAndType();

        final ShadowJetController.SlotDto slot = controller.planFlight(USER_ID, "A320", "EGLL", "EGKK", departureAfterHours(1)).getSlot();

        assertTrue(slot.isFree());
        assertNull(slot.getConflict());
    }

    @Test
    public void aFlightWhichCrossesAPlannedFlightOfThePilotTakesTheSlot() {
        airportsAndType();
        final int busyFrom = world.getWorldTime() / 60 * 60 + 5 * 3600;
        final FlightMissions.Mission busy = plannedFlight("EGLL", "EGKK", busyFrom, 2);

        // starts an hour before the end of the busy flight
        final ShadowJetController.SlotDto slot = controller.planFlight(USER_ID, "A320", "EGLL", "EGKK", departureAfterHours(6)).getSlot();

        assertFalse(slot.isFree());
        assertFalse(slot.isTooSoon());
        assertEquals("EGLL", slot.getConflict().getFrom());
        assertEquals("EGKK", slot.getConflict().getTo());
        assertEquals(busyFrom * 1000L, slot.getConflict().getDepartureTime());
        assertEquals((busyFrom + 2 * 3600) * 1000L, slot.getConflict().getArrivalTime());
        assertEquals("Dispatched", slot.getConflict().getStatus());
        assertEquals(net.simforge.airways2.app.tools.Id.encode(busy.getId()), slot.getConflict().getFlightId());
    }

    @Test
    public void thereAreAtLeastThirtyMinutesAfterAPlannedFlight() {
        airportsAndType();
        final int busyFrom = world.getWorldTime() / 60 * 60 + 5 * 3600;
        final int busyTo = busyFrom + 2 * 3600;
        plannedFlight("EGLL", "EGKK", busyFrom, 2);

        // 29 minutes after the arrival of the busy flight: too close; exactly 30 minutes: good
        assertFalse(controller.planFlight(USER_ID, "A320", "EGLL", "EGKK", (busyTo + 29 * 60) * 1000L).getSlot().isFree());
        assertTrue(controller.planFlight(USER_ID, "A320", "EGLL", "EGKK", (busyTo + 30 * 60) * 1000L).getSlot().isFree());
    }

    @Test
    public void thereAreAtLeastThirtyMinutesBeforeAPlannedFlight() {
        airportsAndType();
        final int busyFrom = world.getWorldTime() / 60 * 60 + 10 * 3600;
        plannedFlight("EGLL", "EGKK", busyFrom, 2);
        final long duration = controller.planFlight(USER_ID, "A320", "EGLL", "EGKK", departureAfterHours(1)).getDurationSeconds() * 1000L;

        // the new flight must arrive not later than 30 minutes before the busy flight departs
        final long lastGoodDeparture = busyFrom * 1000L - 30 * 60_000L - duration;
        assertTrue(controller.planFlight(USER_ID, "A320", "EGLL", "EGKK", lastGoodDeparture).getSlot().isFree());
        assertFalse(controller.planFlight(USER_ID, "A320", "EGLL", "EGKK", lastGoodDeparture + 60_000L).getSlot().isFree());
    }

    @Test
    public void aFlightFarFromThePlannedOnesIsFree() {
        airportsAndType();
        plannedFlight("EGLL", "EGKK", world.getWorldTime() / 60 * 60 + 10 * 3600, 2);

        assertTrue(controller.planFlight(USER_ID, "A320", "EGLL", "EGKK", departureAfterHours(1)).getSlot().isFree());
    }

    @Test
    public void aDepartureEarlierThanThirtyMinutesFromNowIsTooSoon() {
        airportsAndType();
        final long now = world.getWorldTime() * 1000L;

        final ShadowJetController.SlotDto soon = controller.planFlight(USER_ID, "A320", "EGLL", "EGKK", now + 29 * 60_000L).getSlot();
        assertFalse(soon.isFree());
        assertTrue(soon.isTooSoon());
        assertNull(soon.getConflict());
        assertEquals(now + 30 * 60_000L, soon.getEarliestDepartureTime());

        assertTrue(controller.planFlight(USER_ID, "A320", "EGLL", "EGKK", now + 30 * 60_000L).getSlot().isFree());
    }

    @Test
    public void theFinishedAndTheCancelledFlightsAndTheFlightsOfOthersDoNotTakeTheSlot() {
        airportsAndType();
        final int from = world.getWorldTime() / 60 * 60 + 5 * 3600;
        plannedFlight("EGLL", "EGKK", from, 2).setStatus(FlightMissions.Status.Finished);
        plannedFlight("EGLL", "EGKK", from, 2).setStatus(FlightMissions.Status.Cancelled);
        plannedFlight("EGLL", "EGKK", from, 2).setUserId(USER_ID + 1);

        assertTrue(controller.planFlight(USER_ID, "A320", "EGLL", "EGKK", from * 1000L).getSlot().isFree());
    }

    @Test
    public void planOfUnknownThingsIsNotFound() {
        airportsAndType();
        final long departure = departureAfterHours(1);

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> controller.planFlight(USER_ID, "ZZZZ", "EGLL", "EGKK", departure)).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> controller.planFlight(USER_ID, "A320", "XXXX", "EGKK", departure)).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> controller.planFlight(USER_ID, "A320", "EGLL", "XXXX", departure)).getStatus());
    }

    @Test
    public void planBetweenTheSameAirportsIsABadRequest() {
        airportsAndType();

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> controller.planFlight(USER_ID, "A320", "EGLL", "EGLL", departureAfterHours(1))).getStatus());
    }

    @Test
    public void noTypesNoList() {
        assertEquals(0, controller.getAircraftTypes().size());
    }
}
