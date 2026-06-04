package net.simforge.airways2.app.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.world.processors.BusyBirdsMissionControl;
import net.simforge.airways2.world.processors.BusyBirdsMissionGenerator;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.JavaTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/busy-birds")
@CrossOrigin
public class BusyBirdsController {
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
                                Id.encode(j.getId()),
                                fromCity.getName(),
                                toCity.getName(),
                                j.getPreferredCabinService().name(),
                                j.getGroupSize(),
                                m.getDistance(),
                                m.getPay(),
                                m.getValidTill().toString());
                    }).sorted(Comparator.comparing(MissionDto::getFromCityName)
                            .thenComparing(MissionDto::getToCityName))
                    .toList();
        });
    }

    @GetMapping("/aircraft/available")
    public List<AircraftDto> getAvailableAircraft(@RequestAttribute("userId") int userId) {
        return worldBean.read(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            return world.aircrafts()
                    .byAircraftOperatorIdAndIdleAndParkedAtAirport(World25.BusyBirdsOperatorId)
                    .map(a -> new AircraftDto(
                            Id.encode(a.getId()),
                            world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                            a.getRegNo(),
                            world.airports().byId(a.getLocationAirportId()).orElseThrow().getIcao()))
                    .toList();
        });
    }

    @GetMapping("/mission/build-plans")
    public BuildPlansResponse buildPlans(@RequestAttribute("userId") int userId,
                                         @RequestParam(name = "missionId") String missionId,
                                         @RequestParam(name = "aircraftId") String aircraftId,
                                         @RequestParam(name = "turnaroundTime", required = false, defaultValue = "1") int turnaroundTime,
                                         @RequestParam(name = "ferryBackToBase", required = false, defaultValue = "true") boolean ferryBackToBase) {
        return worldBean.read(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            Aircrafts.Aircraft aircraft = world.aircrafts().byId(Id.decode(aircraftId)).orElseThrow();
            Journeys.Journey journey = world.journeys().byId(Id.decode(missionId)).orElseThrow();

            List<BusyBirdsMissionControl.MissionPlan> plans = world.busyBirdsMissionControl().buildPlans(journey, aircraft, turnaroundTime, ferryBackToBase);

            return new BuildPlansResponse(plans.stream().map(BusyBirdsController.PlanDto::from).toList());
        });
    }

    @PutMapping("/mission/book")
    public BookMissionResponse bookMission(@RequestAttribute("userId") int userId,
                                           @RequestParam(name = "missionId") String missionId,
                                           @RequestParam(name = "aircraftId") String aircraftId,
                                           @RequestParam(name = "turnaroundTime", required = false, defaultValue = "1") int turnaroundTime,
                                           @RequestParam(name = "ferryBackToBase", required = false, defaultValue = "true") boolean ferryBackToBase,
                                           @RequestParam(name = "planId") String planId) {
        return worldBean.modifySync(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            Aircrafts.Aircraft aircraft = world.aircrafts().byId(Id.decode(aircraftId)).orElseThrow();
            Journeys.Journey journey = world.journeys().byId(Id.decode(missionId)).orElseThrow();

            List<BusyBirdsMissionControl.MissionPlan> plans = world.busyBirdsMissionControl().buildPlans(journey, aircraft, turnaroundTime, ferryBackToBase);

            Optional<BusyBirdsMissionControl.MissionPlan> plan = plans.stream().filter(p -> planId.equals(p.getId())).findFirst();
            if (plan.isEmpty()) {
                return new BookMissionResponse("failure", Collections.singletonList("unable to find plan by planId"));
            }

            if (plan.get().getStatus() == BusyBirdsMissionControl.MissionPlan.Status.Failure) {
                return new BookMissionResponse("failure", plan.get().getMessages());
            }

            List<String> messages = new ArrayList<>();
            for (final BusyBirdsMissionControl.Leg leg : plan.get().getLegs()) {
                FlightMissions.Mission flight = FlightMissionHelper.scheduleDispatchedMission(world, aircraft, leg.getFromAirport(), leg.getToAirport(), leg.getPlannedDepTime());
                flight.setCharacterMode(FlightMissions.CharacterMode.PC);
                flight.setUserId(userId);
                messages.add("Flight mission # " + flight.getId() + " scheduled, departure time: " + TimeTools.ts(flight.getPlannedDepartureWorldTime()));

                // all busyBirds aircraft are expected to have J-layout only
                if (leg.getType() == BusyBirdsMissionControl.Leg.Type.Revenue) {
                    TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(flight);
                    journey.setTransportFlight1Id(transportFlight.getId());
                    world.transportFlightControl().obtainFlightTickets(transportFlight, journey.getGroupSize(), CabinLayout.Service.J);
                    journey.setBookedCabinService(CabinLayout.Service.J);
                    world.journeyControl().waitForCheckin(journey);
                    messages.add("Transport flight # " + transportFlight.getId() + " created, journey # " + journey.getId() + " booked to the transport flight");
                }
            }

            Properties properties = BusyBirdsMissionGenerator.loadMissionsFile();
            BusyBirdsMissionGenerator.MissionInfo missionInfo = BusyBirdsMissionGenerator.getMissionInfoById(properties, Id.decode(missionId)).orElseThrow();
            missionInfo.delete();
            BusyBirdsMissionGenerator.saveMissionsFile(properties);
            messages.add("BusyBirds mission removed from available");

            return new BookMissionResponse("success", messages);
        });
    }

    @Data
    @AllArgsConstructor
    public static class MissionDto {
        private String id;
        private String fromCityName;
        private String toCityName;
        private String pCs;
        private int pax;
        private int distance;
        private int pay;
        private String validTill;
    }

    @Data
    @AllArgsConstructor
    public static class AircraftDto {
        private String id;
        private String typeCode;
        private String regNo;
        private String locationAirportIcao;
    }

    @Data
    @AllArgsConstructor
    public static class BuildPlansResponse {
        private List<PlanDto> plans;
    }

    @Data
    @AllArgsConstructor
    public static class PlanDto {
        private String status;
        private String id;
        private String description;
        private List<LegDto> legs;
        private List<String> messages;

        public static PlanDto from(BusyBirdsMissionControl.MissionPlan plan) {
            if (plan.getStatus() == BusyBirdsMissionControl.MissionPlan.Status.Failure) {
                return new PlanDto("failure", null, null, null, plan.getMessages());
            }

            final List<LegDto> legDtos = plan.getLegs().stream().map(leg -> new LegDto(
                    leg.getType().name(),
                    leg.getFromAirport().getIcao(),
                    leg.getToAirport().getIcao(),
                    leg.getPax(),
                    leg.getDistance(),
                    Time.toLdt(leg.getPlannedDepTime()).toLocalDate().toString(),
                    JavaTime.toHhmm(Time.toLdt(leg.getPlannedDepTime()).toLocalTime()),
                    JavaTime.toHhmm(Time.toLdt(leg.getPlannedArrTime()).toLocalTime()),
                    JavaTime.toHhmm(leg.getPlannedDuration())
            )).toList();

            return new PlanDto("success", plan.getId(), plan.getDescription(), legDtos, null);
        }
    }

    @Data
    @AllArgsConstructor
    private static class LegDto {
        private final String type; // reposition, revenue
        private final String fromAirportIcao;
        private final String toAirportIcao;
        private final int pax;
        private final int distance;
        private final String dof;
        private final String depTime;
        private final String arrTime;
        private final String duration;
    }

    @Data
    @AllArgsConstructor
    public static class BookMissionResponse {
        private String status;
        private List<String> messages;
    }
}
