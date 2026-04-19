package net.simforge.airways2.app.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.WorldRunnerBean;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.computations.AircraftPerformanceData;
import net.simforge.airways2.world.computations.SimpleFlight;
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
                    .byAircraftOperatorId(World25.BusyBirdsOperatorId)
                    .filter(Aircrafts::isIdleAndParkedAtAirport)
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

            if (plan.getStatus() == BusyBirdsMissionControl.MissionPlan.Status.Failure) {
                return new BuildPlanResponse("failure", null, plan.getMessages());
            }

            final AircraftTypes.AircraftType aircraftType = world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow();
            final AircraftPerformanceData performanceData = AircraftPerformanceData.getData(aircraftType.getIcao());

            final List<LegDto> legDtos = plan.getLegs().stream().map(leg -> new LegDto(
                    leg.getType().name(),
                    leg.getFromAirport().getIcao(),
                    leg.getToAirport().getIcao(),
                    leg.getPax(),
                    (int) Geo.distance(leg.getFromAirport().getCoords(), leg.getToAirport().getCoords()),
                    JavaTime.toHhmm(SimpleFlight.forRoute(leg.getFromAirport().getCoords(), leg.getToAirport().getCoords(), performanceData).getTotalTime())
                    // todo ak0 add dof
                    // todo ak0 planned dep/arr time
            )).toList();

            return new BuildPlanResponse("success", legDtos, null);
        });
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
            int departureTime = world.getWorldTime() + Time.ONE_HOUR;
            for (final BusyBirdsMissionControl.Leg leg : plan.getLegs()) {
                FlightMissions.Mission flight = FlightMissionHelper.scheduleDispatchedMission(world, aircraft, leg.getFromAirport(), leg.getToAirport(), departureTime);
                flight.setModePlayerCharacter(true);
                flight.setUserId(userId);
                messages.add("Flight mission # " + flight.getId() + " scheduled, departure time: " + TimeTools.ts(flight.getPlannedDepartureWorldTime()));

                if (leg.getType() == BusyBirdsMissionControl.Leg.Type.Revenue) {
                    final TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(flight, CabinLayout.FJWY(10, 0, 0, 0)); // todo ak1 provide layouts for those several types
                    journey.setTransportFlight1Id(transportFlight.getId());
                    world.transportFlightControl().obtainFlightTickets(transportFlight, journey.getGroupSize(), journey.getCabinService());
                    world.journeyControl().waitForCheckin(journey);
                    messages.add("Transport flight # " + transportFlight.getId() + " created, journey # " + journey.getId() + " booked to the transport flight");
                }

                departureTime = flight.getPlannedArrivalWorldTime() + Time.ONE_HOUR;
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
    }

    @Data
    @AllArgsConstructor
    private static class LegDto {
        private String type; // reposition, revenue
        private String fromAirportIcao;
        private String toAirportIcao;
        private int pax;
        private int distance;
        private String duration;
    }

    @Data
    @AllArgsConstructor
    public static class BookMissionResponse {
        private String status;
        private List<String> messages;
    }
}
