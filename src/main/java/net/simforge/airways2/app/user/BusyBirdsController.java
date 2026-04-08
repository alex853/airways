package net.simforge.airways2.app.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.WorldRunnerBean;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.processors.BusyBirdsMissionControl;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/busy-birds")
@CrossOrigin
public class BusyBirdsController {
    // todo ak1 migrate ids to sqids

    private static final Logger log = LoggerFactory.getLogger(BusyBirdsController.class);

    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/mission/to-book")
    public List<MissionDto> getMissionsToBook(@RequestAttribute("userId") Long userId) {
        log.info("getMissionsToBook for user {}", userId);

        try (final Timing.Timer ignored = Timing.label("BusyBirdsController - getMissionsToBook")) {
            return worldBean.read(world -> world.journeys().filter(world.journeys().bySpecialProcessing())
                    .map(j -> {
                        final Cities.City fromCity = world.cities().byId(j.getFromCityId()).get();
                        final Cities.City toCity = world.cities().byId(j.getToCityId()).get();
                        final int distance = (int) Geo.distance(fromCity.getCoords(), toCity.getCoords());
                        final int pay = (int) (((distance / 400.0) * 7000.0 + 2000.0)
                                * (1 + fromCity.getId()/1000.0)
                                * (1 + toCity.getId()/1000.0)
                                * (1 + j.getId()/1000.0));

                        return new MissionDto( // todo ak0 some filtering by status
                                j.getId(),
                                j.getFromCityId(),
                                fromCity.getName(),
                                j.getToCityId(),
                                toCity.getName(),
                                j.getGroupSize(),
                                distance,
                                pay);
                    })
                    .toList());
        }
    }

    @GetMapping("/aircraft/available")
    public List<AircraftDto> getAvailableAircraft() {
        try (final Timing.Timer ignored = Timing.label("BusyBirdsController - getAvailableAircraft")) {
            return worldBean.read(world -> world.aircrafts()
                    .byAircraftOperatorId(3) // todo ak3 this operatorId should go into some constant
                    .filter(Aircrafts::isIdleAndParkedAtAirport)
                    .map(a -> new AircraftDto(
                            a.getId(),
                            world.aircraftTypes().byId(a.getAircraftTypeId()).get().getIcao(),
                            a.getRegNo(),
                            a.getLocationAirportId(),
                            world.airports().byId(a.getLocationAirportId()).get().getIcao()))
                    .toList());
        }
    }

    @PostMapping("/mission/get-plan")
    public GetPlanResponse getPlan(@RequestParam(name = "missionId") final int missionId,
                                   @RequestParam(name = "aircraftId") final int aircraftId) {
        try (final Timing.Timer ignored = Timing.label("BusyBirdsController - getAvailableAircraft")) {
            return worldBean.read(world -> {
                final BusyBirdsMissionControl bbControl = world.busyBirdsMissionControl();
                final Aircrafts.Aircraft aircraft = world.aircrafts().byId(aircraftId).get();
                final Journeys.Journey journey = world.journeys().byId(missionId).get();
                final BusyBirdsMissionControl.MissionPlan plan = bbControl.buildPlan(journey, aircraft);
                if (plan.getStatus() == BusyBirdsMissionControl.MissionPlan.Status.Failure) {
                    return new GetPlanResponse("failure", null, plan.getMessages());
                }

                final List<LegDto> legDtos = plan.getLegs().stream().map(leg -> new LegDto(
                        leg.getType().name(),
                        leg.getFromAirport().getIcao(),
                        leg.getToAirport().getIcao(),
                        leg.getPax()
                )).toList();

                return new GetPlanResponse("success", legDtos, null);
            });
        }
    }

    @Data
    @AllArgsConstructor
    public static class MissionDto {
        private int id;
        private int fromCityId;
        private String fromCityName;
        private int toCityId;
        private String toCityName;
        private int pax;
        private int distance;
        private int pay;
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
    public static class GetPlanResponse {
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
    }
}
