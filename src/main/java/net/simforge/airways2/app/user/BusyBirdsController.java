package net.simforge.airways2.app.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.WorldRunnerBean;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.world.processors.BusyBirdsMissionControl;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/busy-birds")
@CrossOrigin
public class BusyBirdsController {
    // todo ak1 migrate ids to sqids

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(BusyBirdsController.class);

    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/mission/to-book")
    public List<MissionDto> getMissionsToBook(@RequestAttribute("userId") int userId) {
        return worldBean.read(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            return world.busyBirdsMissionControl().getJourneysToBook().stream()
                    .map(j -> {
                        final Cities.City fromCity = world.cities().byId(j.getFromCityId()).get();
                        final Cities.City toCity = world.cities().byId(j.getToCityId()).get();

                        final int distance = (int) Geo.distance(fromCity.getCoords(), toCity.getCoords());
                        final int pay = (int) (((distance / 400.0) * 7000.0 + 2000.0)
                                * (1 + lastDigit(fromCity.getId())*0.01)
                                * (1 + lastDigit(toCity.getId())*0.01)
                                * (1 + lastDigit(j.getId())*0.01));

                        return new MissionDto(
                                j.getId(),
                                j.getFromCityId(),
                                fromCity.getName(),
                                j.getToCityId(),
                                toCity.getName(),
                                j.getGroupSize(),
                                distance,
                                pay);
                    })
                    .toList();
        });
    }

    @GetMapping("/aircraft/available")
    public List<AircraftDto> getAvailableAircraft(@RequestAttribute("userId") int userId) {
        return worldBean.read(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            return world.aircrafts()
                    .byAircraftOperatorId(world.busyBirdsMissionControl().getBusyBirdsOperator().getId())
                    .filter(Aircrafts::isIdleAndParkedAtAirport)
                    .map(a -> new AircraftDto(
                            a.getId(),
                            world.aircraftTypes().byId(a.getAircraftTypeId()).get().getIcao(),
                            a.getRegNo(),
                            a.getLocationAirportId(),
                            world.airports().byId(a.getLocationAirportId()).get().getIcao()))
                    .toList();
        });
    }

    @GetMapping("/mission/build-plan")
    public BuildPlanResponse buildPlan(@RequestAttribute("userId") int userId,
                                       @RequestParam(name = "missionId") final int missionId,
                                       @RequestParam(name = "aircraftId") final int aircraftId) {
        return worldBean.read(world -> {
            world.busyBirdsMissionControl().checkUserHasAccessToBusyBirds(userId);

            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(aircraftId).get();
            final Journeys.Journey journey = world.journeys().byId(missionId).get();

            final BusyBirdsMissionControl.MissionPlan plan = world.busyBirdsMissionControl().buildPlan(journey, aircraft);

            if (plan.getStatus() == BusyBirdsMissionControl.MissionPlan.Status.Failure) {
                return new BuildPlanResponse("failure", null, plan.getMessages());
            }

            final List<LegDto> legDtos = plan.getLegs().stream().map(leg -> new LegDto(
                    leg.getType().name(),
                    leg.getFromAirport().getIcao(),
                    leg.getToAirport().getIcao(),
                    leg.getPax()
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

            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(aircraftId).get();
            final Journeys.Journey journey = world.journeys().byId(missionId).get();

            final BusyBirdsMissionControl.MissionPlan plan = world.busyBirdsMissionControl().buildPlan(journey, aircraft);

            if (plan.getStatus() == BusyBirdsMissionControl.MissionPlan.Status.Failure) {
                return new BookMissionResponse("failure", plan.getMessages());
            }

            List<String> messages = new ArrayList<>();
            int departureTime = world.getWorldTime() + Time.ONE_DAY;
            for (final BusyBirdsMissionControl.Leg leg : plan.getLegs()) {
                FlightMissions.Mission flight = FlightMissionHelper.scheduleDispatchedMission(world, aircraft, leg.getFromAirport(), leg.getToAirport(), departureTime);
                flight.setModePlayerCharacter(true);
                flight.setUserId(userId);
                messages.add("Flight mission # " + flight.getId() + " scheduled, departure time: " + TimeTools.ts(flight.getPlannedDepartureWorldTime()));

                if (leg.getType() == BusyBirdsMissionControl.Leg.Type.Revenue) {
                    final TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(flight, CabinLayout.FJWY(journey.getGroupSize(), 0, 0, 0));
                    journey.setTransportFlight1Id(transportFlight.getId());
                    world.transportFlightControl().obtainFlightTickets(transportFlight, journey.getGroupSize(), journey.getCabinService());
                    world.journeyControl().waitForCheckin(journey);
                    messages.add("Transport flight # " + transportFlight.getId() + " created, journey # " + journey.getId() + " booked to the transport flight");
                }

                departureTime = flight.getPlannedArrivalWorldTime() + Time.ONE_DAY;
            }

            return new BookMissionResponse("success", messages);
        });
    }

    private static int lastDigit(int v) {
        return v % 10;
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
    }

    @Data
    @AllArgsConstructor
    public static class BookMissionResponse {
        private String status;
        private List<String> messages;
    }
}
