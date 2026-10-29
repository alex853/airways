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
    void theCarriedPassengersAreToldOnlyForAFinishedFlight() {
        final FlightMissions.Mission mission = mission(world.getWorldTime() + 3600);
        final TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(mission);
        transportFlight.setPaxCarried(7);

        // the flight is not over: the carried passengers are not told
        assertNull(controller.getMyFlights(USER_ID).getFlights().get(0).getTransportFlight().getCarriedPax());

        mission.setStatus(FlightMissions.Status.Finished);

        assertEquals(7, controller.getMyFlights(USER_ID).getFlights().get(0).getTransportFlight().getCarriedPax());
    }

    @Test
    void aFlightWithoutTransportFlightHasNoTransportFlightDto() {
        mission(world.getWorldTime() + 3600);

        assertNull(controller.getMyFlights(USER_ID).getFlights().get(0).getTransportFlight());
    }
}
