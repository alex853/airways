package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The alternate of a flight: the pilot chooses it before blocks off among the nearest airports to the destination.
 */
class FlightMissionAlternateTest {
    private World world;
    private FlightMissionControl control;
    private Airports.Airport departure;
    private Airports.Airport destination;
    private FlightMissions.Mission mission;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        control = world.flightMissionControl();

        departure = world.airports().create(51.28, -0.76, "FRM", "EGLF", "Farnborough");
        destination = world.airports().create(50.03, 8.57, "FRA", "EDDF", "Frankfurt");
        // twelve airports along the meridian, each 0.1 degree further from the destination than the previous one
        for (int i = 1; i <= 12; i++) {
            world.airports().create(50.03 + i * 0.1, 8.57, "A" + i, "XA" + String.format("%02d", i), "Airport " + i);
        }
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", departure);
        final int departureTime = world.getWorldTime() + Time.ONE_HOUR;
        mission = world.flightMissions().createDispatchedMission(aircraft, departure, destination, departureTime, departureTime + 2 * Time.ONE_HOUR);
    }

    @Test
    void noAlternateByDefault() {
        assertEquals(0, mission.getAlternateAirportId());
    }

    @Test
    void theCandidatesAreTheTenNearestToTheDestinationNearestFirst() {
        final List<FlightMissionControl.AlternateCandidate> candidates = control.alternateCandidates(mission);

        assertEquals(FlightMissionControl.ALTERNATE_CANDIDATES_COUNT, candidates.size());
        assertEquals("XA01", candidates.get(0).airport().getIcao());
        assertEquals("XA10", candidates.get(9).airport().getIcao());
        for (int i = 1; i < candidates.size(); i++) {
            assertTrue(candidates.get(i - 1).distanceNm() <= candidates.get(i).distanceNm());
        }
        // 0.1 degree of latitude is 6 nautical miles
        assertEquals(6.0, candidates.get(0).distanceNm(), 0.5);
    }

    @Test
    void theDestinationAndTheDepartureAreNotCandidates() {
        // the departure is far, but with the destination excluded the list has to hold the 10 nearest anyway: make the departure the nearest one to be sure it is skipped
        final Airports.Airport nearDeparture = world.airports().create(50.04, 8.57, "NRD", "XNRD", "Next to the destination");
        final FlightMissions.Mission fromNear = world.flightMissions().createDispatchedMission(
                world.aircrafts().byId(mission.getAircraftId()).orElseThrow(), nearDeparture, destination,
                mission.getPlannedDepartureWorldTime(), mission.getPlannedArrivalWorldTime());

        final List<String> icaos = control.alternateCandidates(fromNear).stream().map(c -> c.airport().getIcao()).toList();

        assertTrue(!icaos.contains("EDDF") && !icaos.contains("XNRD"), icaos.toString());
    }

    @Test
    void theAlternateIsSetAndCleared() {
        final Airports.Airport alternate = world.airports().byIcao("XA03").orElseThrow();

        control.setAlternate(mission, alternate);
        assertEquals(alternate.getId(), mission.getAlternateAirportId());

        // it can be changed
        final Airports.Airport other = world.airports().byIcao("XA05").orElseThrow();
        control.setAlternate(mission, other);
        assertEquals(other.getId(), mission.getAlternateAirportId());

        control.setAlternate(mission, null);
        assertEquals(0, mission.getAlternateAirportId());
    }

    @Test
    void anAirportWhichIsNotAmongTheNearestIsRefused() {
        final Airports.Airport far = world.airports().byIcao("XA12").orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> control.setAlternate(mission, far));
        assertEquals(0, mission.getAlternateAirportId());
    }

    @Test
    void theDestinationAndTheDepartureAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> control.setAlternate(mission, destination));
        assertThrows(IllegalArgumentException.class, () -> control.setAlternate(mission, departure));
    }

    @Test
    void afterBlocksOffTheAlternateCannotBeChanged() {
        control.setAlternate(mission, world.airports().byIcao("XA01").orElseThrow());
        mission.setStatus(FlightMissions.Status.Departure);

        assertThrows(IllegalArgumentException.class, () -> control.setAlternate(mission, world.airports().byIcao("XA02").orElseThrow()));
        assertThrows(IllegalArgumentException.class, () -> control.setAlternate(mission, null));
        assertEquals(world.airports().byIcao("XA01").orElseThrow().getId(), mission.getAlternateAirportId());
    }

    @Test
    void thePreflightStillAllowsIt() {
        mission.setStatus(FlightMissions.Status.Preflight);

        control.setAlternate(mission, world.airports().byIcao("XA02").orElseThrow());

        assertEquals(world.airports().byIcao("XA02").orElseThrow().getId(), mission.getAlternateAirportId());
    }
}
