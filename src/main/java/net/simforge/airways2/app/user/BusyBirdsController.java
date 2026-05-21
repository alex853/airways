package net.simforge.airways2.app.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.world.processors.BusyBirdsMissionControl;
import net.simforge.airways2.world.processors.BusyBirdsMissionGenerator;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Geo;
import net.simforge.commons.misc.JavaTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@RestController
@RequestMapping("/busy-birds")
@CrossOrigin
public class BusyBirdsController {
    // todo ak2 migrate ids to sqids

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(BusyBirdsController.class);

    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/mission/to-book")
    public List<MissionDto> getMissionsToBook(@RequestAttribute("userId") int userId) {
        return worldBean.read(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            return world.busyBirdsMissionControl().getMissionsToBook().stream()
                    .map(m -> {
                        Journeys.Journey j = m.getJourney();

                        Cities.City fromCity = world.cities().byId(j.getFromCityId()).orElseThrow();
                        Cities.City toCity = world.cities().byId(j.getToCityId()).orElseThrow();

                        return new MissionDto(
                                j.getId(),
                                fromCity.getName(),
                                toCity.getName(),
                                j.getGroupSize(),
                                m.getDistance(),
                                m.getPay(),
                                m.getValidTill().toString());
                    }).toList();
        });
    }

    @GetMapping("/aircraft/available")
    public List<AircraftDto> getAvailableAircraft(@RequestAttribute("userId") int userId) {
        return worldBean.read(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            return world.aircrafts()
                    .byAircraftOperatorIdAndIdleAndParkedAtAirport(World25.BusyBirdsOperatorId)
                    .map(a -> new AircraftDto(
                            a.getId(),
                            world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                            a.getRegNo(),
                            a.getLocationAirportId(),
                            world.airports().byId(a.getLocationAirportId()).orElseThrow().getIcao()))
                    .toList();
        });
    }

    @GetMapping("/mission/build-plan")
    public BuildPlanResponse buildPlan(@RequestAttribute("userId") int userId,
                                       @RequestParam(name = "missionId") final int missionId,
                                       @RequestParam(name = "aircraftId") final int aircraftId) {
        return worldBean.read(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(aircraftId).orElseThrow();
            final Journeys.Journey journey = world.journeys().byId(missionId).orElseThrow();

            final BusyBirdsMissionControl.MissionPlan plan = world.busyBirdsMissionControl().buildPlan(journey, aircraft);

            return planToDto(plan);
        });
    }

    @GetMapping("/mission/build-plans")
    public BuildPlansResponse buildPlans(@RequestAttribute("userId") int userId,
                                         @RequestParam(name = "missionId") int missionId, // todo ak0 time step param
                                         @RequestParam(name = "aircraftId") int aircraftId) { // todo ak0 no ferry flight to base
        return worldBean.read(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            Aircrafts.Aircraft aircraft = world.aircrafts().byId(aircraftId).orElseThrow();
            Journeys.Journey journey = world.journeys().byId(missionId).orElseThrow();

            List<BusyBirdsMissionControl.MissionPlan> plans = world.busyBirdsMissionControl().buildPlans(journey, aircraft);

            return new BuildPlansResponse(plans.stream().map(BusyBirdsController::planToDto).toList());
        });
    }

    private static BuildPlanResponse planToDto(BusyBirdsMissionControl.MissionPlan plan) {
        if (plan.getStatus() == BusyBirdsMissionControl.MissionPlan.Status.Failure) {
            return new BuildPlanResponse("failure", null, plan.getMessages(), null);
        }

        final List<LegDto> legDtos = plan.getLegs().stream().map(leg -> new LegDto(
                leg.getType().name(),
                leg.getFromAirport().getIcao(),
                leg.getToAirport().getIcao(),
                leg.getPax(),
                (int) Geo.distance(leg.getFromAirport().getCoords(), leg.getToAirport().getCoords()),
                Time.toLdt(leg.getPlannedDepTime()).toLocalDate().toString(),
                JavaTime.toHhmm(Time.toLdt(leg.getPlannedDepTime()).toLocalTime()),
                JavaTime.toHhmm(Time.toLdt(leg.getPlannedArrTime()).toLocalTime()),
                JavaTime.toHhmm(leg.getPlannedDuration())
        )).toList();

        return new BuildPlanResponse("success", legDtos, null, plan.getDescription());
    }

    @PutMapping("/mission/book")
    public BookMissionResponse bookMission(@RequestAttribute("userId") int userId,
                                           @RequestParam(name = "missionId") final int missionId,
                                           @RequestParam(name = "aircraftId") final int aircraftId) {
        return worldBean.modifySync(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(aircraftId).orElseThrow();
            final Journeys.Journey journey = world.journeys().byId(missionId).orElseThrow();

            final BusyBirdsMissionControl.MissionPlan plan = world.busyBirdsMissionControl().buildPlan(journey, aircraft);

            if (plan.getStatus() == BusyBirdsMissionControl.MissionPlan.Status.Failure) {
                return new BookMissionResponse("failure", plan.getMessages());
            }

            List<String> messages = new ArrayList<>();
            for (final BusyBirdsMissionControl.Leg leg : plan.getLegs()) {
                FlightMissions.Mission flight = FlightMissionHelper.scheduleDispatchedMission(world, aircraft, leg.getFromAirport(), leg.getToAirport(), leg.getPlannedDepTime());
                flight.setCharacterMode(FlightMissions.CharacterMode.PC);
                flight.setUserId(userId);
                messages.add("Flight mission # " + flight.getId() + " scheduled, departure time: " + TimeTools.ts(flight.getPlannedDepartureWorldTime()));

                if (leg.getType() == BusyBirdsMissionControl.Leg.Type.Revenue) {
                    // todo ak1 F&J refactoring
                    final TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(flight, CabinLayout.FJWY(10, 0, 0, 0));
                    journey.setTransportFlight1Id(transportFlight.getId());
                    world.transportFlightControl().obtainFlightTickets(transportFlight, journey.getGroupSize(), journey.getCabinService());
                    world.journeyControl().waitForCheckin(journey);
                    messages.add("Transport flight # " + transportFlight.getId() + " created, journey # " + journey.getId() + " booked to the transport flight");
                }
            }

            Properties properties = BusyBirdsMissionGenerator.loadMissionsFile();
            BusyBirdsMissionGenerator.MissionInfo missionInfo = BusyBirdsMissionGenerator.getMissionInfoById(properties, missionId).orElseThrow();
            missionInfo.delete();
            BusyBirdsMissionGenerator.saveMissionsFile(properties);
            messages.add("BusyBirds mission removed from available");

            return new BookMissionResponse("success", messages);
        });
    }

    @Data
    @AllArgsConstructor
    public static class MissionDto {
        private int id;
        private String fromCityName;
        private String toCityName;
        private int pax;
        private int distance;
        private int pay;
        private String validTill;
    }

    @Data
    @AllArgsConstructor
    public static class AircraftDto {
        private int id;
        private String typeCode;
        private String regNo;
        private int locationAirportId;
        private String locationAirportIcao;
    }

    @Data
    @AllArgsConstructor
    public static class BuildPlanResponse {
        private String status;
        private List<LegDto> legs;
        private List<String> messages;
        private String description;
    }

    @Data
    @AllArgsConstructor
    public static class BuildPlansResponse {
        private List<BuildPlanResponse> plans;
    }

    @Data
    @AllArgsConstructor
    private static class LegDto {
        private String type; // reposition, revenue
        private String fromAirportIcao;
        private String toAirportIcao;
        private int pax;
        private int distance;
        private String dof;
        private String depTime;
        private String arrTime;
        private String duration;
    }

    @Data
    @AllArgsConstructor
    public static class BookMissionResponse {
        private String status;
        private List<String> messages;
    }
}
