package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The divert of a flight in flight: the airport is chosen among the departure, the declared alternate and the nearest airports to the aircraft, and the aircraft of a manual
 * flight flies there step by step.
 */
class FlightMissionDivertTest {
    private World world;
    private FlightMissionControl control;
    private Airports.Airport departure;
    private Airports.Airport destination;
    private Airports.Airport farAlternate;
    private FlightMissions.Mission mission;
    private Aircrafts.Aircraft aircraft;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        control = world.flightMissionControl();

        departure = world.airports().create(51.5, -0.1, "AAA", "AAAA", "Alpha");
        destination = world.airports().create(51.5, 4.0, "BBB", "BBBB", "Bravo");
        farAlternate = world.airports().create(48.0, 11.0, "ZZZ", "ZZZZ", "Zulu");
        // six airports around the departure, each 0.1 degree of longitude (about 4 nautical miles) further than the previous one
        for (int i = 1; i <= 6; i++) {
            world.airports().create(51.5, -0.1 + i * 0.1, "N" + i, "XN0" + i, "Near " + i);
        }
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", departure);
        final int departureTime = world.getWorldTime() + Time.ONE_HOUR;
        mission = world.flightMissions().createDispatchedMission(aircraft, departure, destination, departureTime, departureTime + Time.ONE_HOUR);
        mission.setUserId(7);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 7, Persons.LocationType.Airport, departure.getId(), 0, "AK", 0);
    }

    private void takeoffManually() {
        assertTrue(control.start(mission).started());
        control.setTrackingMode(mission, FlightMissions.TrackingMode.Manual);
        control.blocksOff(mission);
        control.takeoff(mission);
    }

    // the world goes ahead in ticks as in the application, the processor of the flight missions flies the aircraft
    private void advanceMinutes(final int minutes) {
        final int until = world.getWorldTime() + minutes * 60;
        while (world.getWorldTime() < until) {
            world.setWorldTime(world.getWorldTime() + Time.TICK);
            FlightMissionProcessor.process(world);
        }
    }

    @Test
    void theDivertAndTheAlternateShareTheFieldButDoNotMix() {
        control.setAlternate(mission, world.airports().byIcao("XN03").orElseThrow());
        assertEquals(world.airports().byIcao("XN03").orElseThrow().getId(), mission.getAlternateAirportId());
        assertFalse(mission.isDivertDeclared());
        assertEquals(destination.getId(), mission.getTargetAirportId());

        mission.setDivertAirportId(farAlternate.getId());

        assertTrue(mission.isDivertDeclared());
        assertEquals(farAlternate.getId(), mission.getDivertAirportId());
        assertEquals(0, mission.getAlternateAirportId());
        assertEquals(farAlternate.getId(), mission.getTargetAirportId());

        // taken back, the planned alternate is lost and the field does not read as one
        mission.setDivertAirportId(0);

        assertFalse(mission.isDivertDeclared());
        assertEquals(0, mission.getDivertAirportId());
        assertEquals(0, mission.getAlternateAirportId());
        assertEquals(destination.getId(), mission.getTargetAirportId());
    }

    @Test
    void aManualFlightFliesToTheTarget() {
        takeoffManually();

        assertEquals(FlightMissions.CoordinatesSource.FlyToTarget, mission.getCoordinatesSource());
    }

    @Test
    void theCandidatesAreTheDepartureTheDeclaredAlternateAndTheNearestOnes() {
        control.setAlternate(mission, farAlternate);
        takeoffManually();

        final List<FlightMissionControl.DivertCandidate> candidates = control.divertCandidates(mission);

        // the aircraft is at the departure: it is the nearest, then four of the six near airports, then the alternate far away; the destination is not offered
        assertEquals(List.of("AAAA", "XN01", "XN02", "XN03", "XN04", "ZZZZ"), candidates.stream().map(c -> c.airport().getIcao()).toList());
        assertEquals(FlightMissionControl.DivertKind.Departure, candidates.get(0).kind());
        assertEquals(FlightMissionControl.DivertKind.CloseOption, candidates.get(1).kind());
        assertEquals(FlightMissionControl.DivertKind.DeclaredAlternate, candidates.get(5).kind());
        for (int i = 1; i < candidates.size(); i++) {
            assertTrue(candidates.get(i - 1).distanceNm() <= candidates.get(i).distanceNm());
        }
    }

    @Test
    void theDeclaredAlternateKeepsItsKindWhenItIsAmongTheNearest() {
        control.setAlternate(mission, world.airports().byIcao("XN02").orElseThrow());
        takeoffManually();

        final List<FlightMissionControl.DivertCandidate> candidates = control.divertCandidates(mission);

        assertEquals(5, candidates.size());
        assertEquals(FlightMissionControl.DivertKind.DeclaredAlternate, candidates.stream().filter(c -> c.airport().getIcao().equals("XN02")).findFirst().orElseThrow().kind());
    }

    @Test
    void theDivertIsDeclaredAndTakenBack() {
        takeoffManually();
        final Airports.Airport near = world.airports().byIcao("XN02").orElseThrow();

        control.divert(mission, near);

        assertTrue(mission.isDivertDeclared());
        assertEquals(near.getId(), mission.getDivertAirportId());
        assertEquals(near.getId(), mission.getTargetAirportId());
        // the destination of the plan stays
        assertEquals(destination.getId(), mission.getDestinationAirportId());

        control.divert(mission, null);

        assertFalse(mission.isDivertDeclared());
        assertEquals(destination.getId(), mission.getTargetAirportId());
    }

    @Test
    void theDivertIsRefusedOutsideOfTheFlight() {
        assertThrows(IllegalArgumentException.class, () -> control.divert(mission, world.airports().byIcao("XN02").orElseThrow()));
    }

    @Test
    void anAirportWhichIsNotOfferedIsRefused() {
        takeoffManually();

        assertThrows(IllegalArgumentException.class, () -> control.divert(mission, farAlternate));
        assertThrows(IllegalArgumentException.class, () -> control.divert(mission, destination));
        assertThrows(IllegalArgumentException.class, () -> control.divert(mission, world.airports().byIcao("XN06").orElseThrow()));
    }

    @Test
    void theAircraftFliesToTheDestinationAndWaitsOverIt() {
        takeoffManually();

        advanceMinutes(10);
        final double after10 = aircraft.getLocationLongitude();
        assertTrue(after10 > -0.1 && after10 < destination.getLongitude());
        assertTrue(FlightMissionHelper.calcSecondsToTarget(world, mission) > 0);

        advanceMinutes(60);
        assertEquals(destination.getLongitude(), aircraft.getLocationLongitude(), 0.001);
        assertEquals(destination.getLatitude(), aircraft.getLocationLatitude(), 0.001);
        assertEquals(0, FlightMissionHelper.calcSecondsToTarget(world, mission));
    }

    @Test
    void afterTheDivertTheAircraftFliesToTheNewTargetFromWhereItIs() {
        takeoffManually();
        advanceMinutes(5);
        // a new airport is next to the aircraft, among the nearest
        final double longitudeBefore = aircraft.getLocationLongitude();
        final Airports.Airport diversion = world.airports().create(51.5, longitudeBefore + 0.3, "DVT", "XDVT", "Divert");
        control.divert(mission, diversion);

        // the position does not jump when the target changes
        advanceMinutes(0);
        assertEquals(longitudeBefore, aircraft.getLocationLongitude(), 0.001);

        // the divert is allowed to land when the aircraft is over it
        assertTrue(FlightMissionHelper.calcEarliestAllowedLandingTime(world, mission) > world.getWorldTime());
        advanceMinutes(30);
        assertEquals(diversion.getLongitude(), aircraft.getLocationLongitude(), 0.001);
        assertEquals(diversion.getLatitude(), aircraft.getLocationLatitude(), 0.001);
        assertTrue(FlightMissionHelper.calcEarliestAllowedLandingTime(world, mission) <= world.getWorldTime());
    }

    @Test
    void aFlightWhichStartedOnTheSimpleFlightMovesToTheTargetOnTheDivert() {
        takeoffManually();
        mission.setCoordinatesSource(FlightMissions.CoordinatesSource.AutomaticSimpleFlight);
        advanceMinutes(5);

        control.divert(mission, world.airports().byIcao("XN02").orElseThrow());

        assertEquals(FlightMissions.CoordinatesSource.FlyToTarget, mission.getCoordinatesSource());
    }
}
