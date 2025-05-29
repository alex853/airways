package net.simforge.airways2.world;

import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.commons.misc.Geo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static net.simforge.airways2.world.datamodel.EventsToProcess.Type.PilotOnDuty;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SingleFlightTest {
    private World world;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private FlightMissions.Mission mission;

    @BeforeEach
    public void beforeEach() {
        final int startTime = 1700000000;

        world = World.create(new InMemoryStorageStrategy(), startTime);

        airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        airportB = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");

        final AircraftTypes.AircraftType aircraftType = world.aircraftTypes().create("TTTT", "TTT");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(aircraftType, "A-BCDE", airportA);

        final int departureTime = startTime + 2*Time.ONE_HOUR;
        final int arrivalTime = departureTime + 2*Time.ONE_HOUR;
        mission = world.flightMissions()
                .createDispatchedMission(
                        aircraft,
                        airportA,
                        airportB,
                        departureTime,
                        arrivalTime);
        world.eventsToProcess().sendEvent(
                PilotOnDuty,
                mission.getId(),
                departureTime);
    }

    @Test
    public void test__automatic_flight() {
        runWorldForNHours(6);

        final FlightMissions.Mission resultedMission = world.flightMissions().byId(mission.getId()).orElseThrow();
        assertEquals(FlightMissions.Status.Finished, resultedMission.getStatus());

        final Aircrafts.Aircraft resultedAircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        assertEquals(Aircrafts.OperationalStatus.Idle, resultedAircraft.getOperationalStatus());
        assertEquals(Aircrafts.LocationStatus.ParkedAtAirport, resultedAircraft.getLocationStatus());
        assertEquals(airportB.getId(), resultedAircraft.getLocationAirportId());
    }

    @Test
    public void test__manual_flight__should_not_start_by_its_own() {
        mission.setModePc(true);

        runWorldForNHours(6);

        final FlightMissions.Mission resultedMission = world.flightMissions().byId(mission.getId()).orElseThrow();
        assertEquals(FlightMissions.Status.Dispatched, resultedMission.getStatus());

        final Aircrafts.Aircraft resultedAircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        assertEquals(Aircrafts.OperationalStatus.Idle, resultedAircraft.getOperationalStatus());
        assertEquals(Aircrafts.LocationStatus.ParkedAtAirport, resultedAircraft.getLocationStatus());
        assertEquals(airportA.getId(), resultedAircraft.getLocationAirportId());
    }

    @Test
    public void test__manual_flight__airplane_should_move_to_destination_coords() {
        mission.setModePc(true);

        runWorldForNHours(1);
        world.flightMissionControl().startOrCancel(mission);
        runWorldForNHours(1);
        world.flightMissionControl().blocksOn(mission);
        runWorldForNHours(1);
        world.flightMissionControl().takeoff(mission);
        runWorldForNHours(3);

        final FlightMissions.Mission resultedMission = world.flightMissions().byId(mission.getId()).orElseThrow();
        assertEquals(FlightMissions.Status.Flying, resultedMission.getStatus());

        final Aircrafts.Aircraft resultedAircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        assertEquals(Aircrafts.OperationalStatus.Active, resultedAircraft.getOperationalStatus());
        assertEquals(Aircrafts.LocationStatus.Flying, resultedAircraft.getLocationStatus());
        assertEquals(0.0f, Geo.distance(airportB.getCoords(), resultedAircraft.getLocationCoords()), 1f);
    }

    private void runWorldForNHours(final int hours) {
        final int worldInitialTime = world.getWorldTime();
        final int finishTime = worldInitialTime + hours*Time.ONE_HOUR;
        while (world.getWorldTime() < finishTime) {
            world.process(world.getWorldTime() + 10);
        }
    }
}
