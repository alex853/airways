package net.simforge.airways2.app.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/aircraft")
@CrossOrigin
public class AircraftController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public List<FullAircraftDto> getAll() {
        try (final Timing.Timer ignored = Timing.label("AircraftController - getAll")) {
            return worldBean.read(world -> world.aircrafts().all()
                    .map(a -> new FullAircraftDto(
                            a.getId(),
                            world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                            a.getRegNo(),
                            a.getAircraftOperatorId(),
                            a.getFlightMissionId(),
                            a.getFlightMissionId() != 0 ? FlightMissionHelper.formatRouteOrNull(world, a.getFlightMissionId()) : null,
                            a.getOperationalStatus().name(),
                            a.getLocationStatus().name(),
                            a.getLocationAirportId() != 0 ? world.airports().byId(a.getLocationAirportId()).orElseThrow().getIcao() : null,
                            a.getLocationLatitude(),
                            a.getLocationLongitude()))
                    .toList());
        }
    }

    @GetMapping("/flying")
    public List<FlyingAircraftDto> getFlying() {
        try (final Timing.Timer ignored = Timing.label("AircraftController - getFlying")) {
            return worldBean.read(world -> world.aircrafts()
                    .filter(world.aircrafts().byLocationStatus(Aircrafts.LocationStatus.Flying))
                    .map(a -> {
                        final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(a.getFlightMissionId());
                        return new FlyingAircraftDto(
                                a.getId(),
                                world.aircraftTypes().byId(a.getAircraftTypeId()).orElseThrow().getIcao(),
                                a.getRegNo(),
                                a.getLocationLatitude(),
                                a.getLocationLongitude(),
                                mission.map(m -> (int) FlightMissionHelper.calculateHeading(world, a.getFlightMissionId())).orElse(0),
                                mission.map(m -> world.airports().byId(m.getDepartureAirportId()).orElseThrow().getIcao()).orElse("n/a"),
                                mission.map(m -> world.airports().byId(m.getDestinationAirportId()).orElseThrow().getIcao()).orElse("n/a"),
                                mission.map(m -> TimeTools.hhmmOrNull(m.getPlannedDepartureWorldTime())).orElse("n/a"),
                                mission.map(m -> TimeTools.hhmmOrNull(m.getPlannedArrivalWorldTime())).orElse("n/a"),
                                mission.map(m -> TimeTools.hhmmOrNull(m.getActualTakeoffWorldTime())).orElse("n/a"),
                                null);
                    })
                    .toList());
        }
    }

    @Data
    @AllArgsConstructor
    private static class FullAircraftDto {
        private int id;
        private String type;
        private String regNo;
        private int aircraftOperatorId;
        private int flightMissionId;
        private String flightRoute;
        private String operationalStatus;
        private String locationStatus;
        private String locationAirport;
        private float locationLatitude;
        private float locationLongitude;
    }

    @Data
    @AllArgsConstructor
    private static class FlyingAircraftDto {
        private int id;
        private String acType;
        private String acReg;
        private float lat;
        private float lon;
        private int hdg;
        private String fpDep;
        private String fpDest;
        private String pDep;
        private String pArr;
        private String aTof;
        private String eLdg;
    }
}
