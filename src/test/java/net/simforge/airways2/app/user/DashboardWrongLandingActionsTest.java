package net.simforge.airways2.app.user;

import net.simforge.airways2.pilottracker.SimTracker;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * After a landing at an airport which is not the destination the passengers do not get off by the ordinary button: the pilot is offered to continue to the destination,
 * to continue later (not available yet) or to cancel the transportation.
 */
class DashboardWrongLandingActionsTest {
    private World world;
    private FlightMissions.Mission mission;
    private TransportFlights.Flight tf;
    private Airports.Airport destination;
    private Airports.Airport other;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final Airports.Airport a = world.airports().create(51.28, -0.76, "FRM", "EGLF", "Farnborough");
        destination = world.airports().create(52.36, 13.5, "TOO", "EDDB", "Berlin");
        other = world.airports().create(50.0, 8.5, "FRA", "EDDF", "Frankfurt");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", a);
        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        mission = world.flightMissions().createDispatchedMission(aircraft, a, destination, departure, departure + 2 * Time.ONE_HOUR);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        mission.setStatus(FlightMissions.Status.Postflight);
        tf = world.transportFlightControl().createTransportFlight(mission);
        tf.setStatus(TransportFlights.Status.WaitingForDeboarding);
    }

    private List<SimTracker.UserAction> actions() {
        return FlightDashboardController.buildActions(world, mission, SimTracker.UserStatus.none(), false, 0);
    }

    private List<String> names() {
        return actions().stream().map(SimTracker.UserAction::getName).toList();
    }

    @Test
    void afterALandingAtTheDestinationTheOrdinaryButtonIsOffered() {
        mission.setActualLandingAirportId(destination.getId());

        assertEquals(List.of("start-deboarding"), names());
    }

    @Test
    void afterALandingElsewhereThreeActionsReplaceTheOrdinaryButton() {
        mission.setActualLandingAirportId(other.getId());

        assertEquals(List.of("continue-to-destination", "continue-later", "cancel-transport"), names());
    }

    @Test
    void continuingLaterIsOfferedButNotAvailable() {
        mission.setActualLandingAirportId(other.getId());

        final SimTracker.UserAction later = actions().stream().filter(a -> a.getName().equals("continue-later")).findFirst().orElseThrow();

        assertFalse(later.isAllowed());
        assertTrue(actions().stream().filter(a -> !a.getName().equals("continue-later")).allMatch(SimTracker.UserAction::isAllowed));
    }

    @Test
    void afterTheDeboardingHasStartedNothingIsOfferedForTheDecision() {
        mission.setActualLandingAirportId(other.getId());
        tf.setStatus(TransportFlights.Status.Deboarding);

        assertFalse(names().contains("continue-to-destination"));
        assertFalse(names().contains("cancel-transport"));
    }
}
