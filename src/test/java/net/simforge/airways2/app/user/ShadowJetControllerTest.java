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

    @Test
    public void flightDurationIsFromBlocksOffToBlocksOnAndGrowsWithTheDistance() {
        airportsAndType();

        final ShadowJetController.FlightDurationDto shortFlight = controller.calculateFlightDuration("A320", "EGLL", "EGKK");
        final ShadowJetController.FlightDurationDto longFlight = controller.calculateFlightDuration("A320", "EGLL", "KJFK");

        // the taxi before the takeoff and after the landing are in it: 10 + 10 minutes at least
        assertTrue(shortFlight.getDurationSeconds() > 20 * 60);
        assertTrue(longFlight.getDurationSeconds() > shortFlight.getDurationSeconds() + 3 * 3600);
        assertTrue(longFlight.getDistance() > 2900 && longFlight.getDistance() < 3100, "distance in nm: " + longFlight.getDistance());
    }

    @Test
    public void flightDurationIsTheDurationOfTheDispatchedMission() {
        airportsAndType();
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(world.aircraftTypes().byIcao("A320").orElseThrow(), "SJ-AAA", world.airports().byIcao("EGLL").orElseThrow());

        // a mission keeps the minutes, so the departure is on a whole minute
        final int departure = world.getWorldTime() / 60 * 60 + 3600;
        final FlightMissions.Mission mission = FlightMissionHelper.scheduleDispatchedMission(
                world, aircraft, world.airports().byIcao("EGLL").orElseThrow(), world.airports().byIcao("KJFK").orElseThrow(), departure);

        assertEquals(mission.getPlannedArrivalWorldTime() - mission.getPlannedDepartureWorldTime(),
                controller.calculateFlightDuration("A320", "EGLL", "KJFK").getDurationSeconds());
    }

    @Test
    public void flightDurationOfUnknownThingsIsNotFound() {
        airportsAndType();

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> controller.calculateFlightDuration("ZZZZ", "EGLL", "EGKK")).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> controller.calculateFlightDuration("A320", "XXXX", "EGKK")).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> controller.calculateFlightDuration("A320", "EGLL", "XXXX")).getStatus());
    }

    @Test
    public void flightDurationBetweenTheSameAirportsIsABadRequest() {
        airportsAndType();

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> controller.calculateFlightDuration("A320", "EGLL", "EGLL")).getStatus());
    }

    @Test
    public void noTypesNoList() {
        assertEquals(0, controller.getAircraftTypes().size());
    }
}
