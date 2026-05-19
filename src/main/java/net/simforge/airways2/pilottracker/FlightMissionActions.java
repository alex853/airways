package net.simforge.airways2.pilottracker;

import net.simforge.airways2.app.WorldAccess;
import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.processors.ShadowJetLogic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

class FlightMissionActions {
    private static final Logger log = LoggerFactory.getLogger(FlightMissionActions.class);

    static FlightMissions.Mission mission_blocksOff(WorldAccess worldAccess, String source, int flightMissionId) {
        return worldAccess.modifySync(world -> {
            final Optional<FlightMissions.Mission> mission1 = world.flightMissions().byId(flightMissionId);
            if (mission1.isEmpty()) {
                log.error("erroneous case, f/m not found, in mission_blocksOff <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event(source + " - erroneous case - mission_blocksOff - fm not found");
                return null;
            }
            final FlightMissions.Mission mission = mission1.get();

            if (mission.getStatus() == FlightMissions.Status.Preflight) {
                world.flightMissionControl().blocksOff(mission);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            FlightStats.event(source + " - blocksOff");

            return mission;
        });
    }

    static FlightMissions.Mission mission_takeoff(WorldAccess worldAccess, String source, int flightMissionId) {
        return worldAccess.modifySync(world -> {
            final Optional<FlightMissions.Mission> mission1 = world.flightMissions().byId(flightMissionId);
            if (mission1.isEmpty()) {
                log.error("erroneous case, f/m not found, in mission_takeoff <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event(source + " - erroneous case - mission_takeoff - fm not found");
                return null;
            }
            final FlightMissions.Mission mission = mission1.get();

            if (mission.getStatus() == FlightMissions.Status.Departure) {
                world.flightMissionControl().takeoff(mission);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            FlightStats.event(source + " - takeoff");

            return mission;
        });
    }

    static FlightMissions.Mission mission_landing(WorldAccess worldAccess, String source, int flightMissionId, String landingAirportIcao) {
        return worldAccess.modifySync(world -> {
            final Optional<FlightMissions.Mission> mission1 = world.flightMissions().byId(flightMissionId);
            if (mission1.isEmpty()) {
                log.error("erroneous case, f/m not found, in mission_landing <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event(source + " - erroneous case - mission_landing - fm not found");
                return null;
            }
            final FlightMissions.Mission mission = mission1.get();

            final Airports.Airport landingAirport = world.airports().byIcao(landingAirportIcao).orElseThrow();

            if (mission.getStatus() == FlightMissions.Status.Flying) {
                world.flightMissionControl().landing(mission, landingAirport);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            FlightStats.event(source + " - landing");

            return mission;
        });
    }

    static FlightMissions.Mission mission_blocksOn(WorldAccess worldAccess, String source, int flightMissionId) {
        return worldAccess.modifySync(world -> {
            final Optional<FlightMissions.Mission> mission1 = world.flightMissions().byId(flightMissionId);
            if (mission1.isEmpty()) {
                log.error("erroneous case, f/m not found, in mission_blocksOn <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                FlightStats.event(source + " - erroneous case - mission_blocksOn - fm not found");
                return null;
            }
            final FlightMissions.Mission mission = mission1.get();

            if (mission.getStatus() == FlightMissions.Status.Arrival) {
                world.flightMissionControl().blocksOn(mission);

                ShadowJetLogic.deboardTransportFlightIfExists(world, mission);
            } else {
                throw new IllegalStateException("unexpected mission status " + mission.getStatus());
            }

            FlightStats.event(source + " - blocksOn");

            return mission;
        });
    }
}
