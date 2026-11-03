package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.AircraftPerformanceData;
import net.simforge.airways2.world.computations.FlightMissionToTimeline;
import net.simforge.airways2.world.computations.FlightTimeline;
import net.simforge.airways2.world.computations.SimpleFlight;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The estimated landing and blocks on of a flight of a pilot in the air are counted from where the aircraft is (FlightMissionToTimeline.byMissionAndAircraftPosition); the AI
 * flights and the flights which have not taken off keep the estimates of the plan.
 */
class FlightMissionEstimateTest {
    private World world;
    private FlightMissionControl control;
    private Airports.Airport departure;
    private Airports.Airport destination;
    private Airports.Airport divertAirport;
    private Aircrafts.Aircraft aircraft;
    private FlightMissions.Mission mission;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        control = world.flightMissionControl();

        departure = world.airports().create(51.5, -0.1, "AAA", "AAAA", "Alpha");
        destination = world.airports().create(51.5, 8.0, "BBB", "BBBB", "Bravo");
        divertAirport = world.airports().create(51.5, 2.0, "CCC", "CCCC", "Charlie");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", departure);
        final int departureTime = world.getWorldTime() + Time.ONE_HOUR;
        mission = world.flightMissions().createDispatchedMission(aircraft, departure, destination, departureTime, departureTime + 2 * Time.ONE_HOUR);
        mission.setUserId(7);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
    }

    private void takeoff() {
        control.start(mission);
        control.setTrackingMode(mission, FlightMissions.TrackingMode.Manual);
        control.blocksOff(mission);
        control.takeoff(mission);
    }

    // the aircraft is half way to the destination at the cruise altitude
    private void cruiseHalfWay() {
        control.moveAircraft(mission, 51.5, 4.0, 90, 35000);
    }

    private LocalDateTime nowPlus(final Duration duration) {
        return Time.toLdt(world.getWorldTime()).plus(duration);
    }

    private Duration flightFromHere(final Airports.Airport to) {
        return SimpleFlight.fromPosition(aircraft.getLocationCoords(), aircraft.getLocationAltitude(), to.getCoords(), AircraftPerformanceData.getData("A320")).getTotalTime();
    }

    @Test
    void theLandingOfAFlightInTheAirIsCountedFromItsPosition() {
        takeoff();
        cruiseHalfWay();
        world.setWorldTime(world.getWorldTime() + 30 * Time.ONE_MINUTE);

        final FlightTimeline timeline = FlightMissionToTimeline.byMissionAndAircraftPosition(world, mission);

        assertEquals(nowPlus(flightFromHere(destination)), timeline.getLanding().getEstimatedTime());
        assertEquals(timeline.getLanding().getEstimatedTime().plusMinutes(FlightTimeline.LANDING_TO_BLOCKS_ON_MINUTES), timeline.getBlocksOn().getEstimatedTime());
        // the plan counts from the takeoff and does not know where the aircraft is
        assertNotEquals(FlightMissionToTimeline.byMission(mission).getLanding().getEstimatedTime(), timeline.getLanding().getEstimatedTime());
    }

    @Test
    void theEstimateMovesWhileTheAircraftFliesAndAMissedTimeGoesBackToTheWorldTime() {
        takeoff();
        cruiseHalfWay();
        final LocalDateTime atFirst = FlightMissionToTimeline.byMissionAndAircraftPosition(world, mission).getLanding().getEstimatedTime();

        // the aircraft stands in the same place for half an hour (it holds): the landing is half an hour later
        world.setWorldTime(world.getWorldTime() + 30 * Time.ONE_MINUTE);

        assertEquals(atFirst.plusMinutes(30), FlightMissionToTimeline.byMissionAndAircraftPosition(world, mission).getLanding().getEstimatedTime());
    }

    @Test
    void aFlightTrackedViaVatsimWithoutAUserIsCountedTheSameWay() {
        mission.setUserId(0);
        takeoff();
        cruiseHalfWay();

        assertEquals(nowPlus(flightFromHere(destination)), FlightMissionToTimeline.byMissionAndAircraftPosition(world, mission).getLanding().getEstimatedTime());
    }

    @Test
    void aDivertedFlightIsCountedToTheDivertAirport() {
        takeoff();
        cruiseHalfWay();
        mission.setDivertAirportId(divertAirport.getId());

        final LocalDateTime landing = FlightMissionToTimeline.byMissionAndAircraftPosition(world, mission).getLanding().getEstimatedTime();

        assertEquals(nowPlus(flightFromHere(divertAirport)), landing);
        assertNotEquals(nowPlus(flightFromHere(destination)), landing);
    }

    @Test
    void anAiFlightKeepsTheEstimatesOfThePlan() {
        mission.setCharacterMode(FlightMissions.CharacterMode.NPC);
        mission.setUserId(0);
        takeoff();
        cruiseHalfWay();

        assertEquals(FlightMissionToTimeline.byMission(mission).getLanding().getEstimatedTime(), FlightMissionToTimeline.byMissionAndAircraftPosition(world, mission).getLanding().getEstimatedTime());
    }

    @Test
    void aFlightWhichHasNotTakenOffKeepsTheEstimatesOfThePlan() {
        control.start(mission);
        control.setTrackingMode(mission, FlightMissions.TrackingMode.Manual);
        control.blocksOff(mission);

        assertEquals(FlightMissionToTimeline.byMission(mission).getLanding().getEstimatedTime(), FlightMissionToTimeline.byMissionAndAircraftPosition(world, mission).getLanding().getEstimatedTime());
    }

    @Test
    void aFlightWhichHasLandedIsCountedFromTheActualLanding() {
        takeoff();
        cruiseHalfWay();
        mission.setActualLandingWorldTime(world.getWorldTime() + 10 * Time.ONE_MINUTE);
        world.setWorldTime(world.getWorldTime() + 10 * Time.ONE_MINUTE);

        final FlightTimeline timeline = FlightMissionToTimeline.byMissionAndAircraftPosition(world, mission);

        assertEquals(Time.toLdt(mission.getActualLandingWorldTime()).plusMinutes(FlightTimeline.LANDING_TO_BLOCKS_ON_MINUTES), timeline.getBlocksOn().getEstimatedTime());
    }
}
