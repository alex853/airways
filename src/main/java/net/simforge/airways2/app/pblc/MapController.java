package net.simforge.airways2.app.pblc;

import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.dto.AircraftDetailsDto;
import net.simforge.airways2.app.dto.AircraftInfoDto;
import net.simforge.airways2.app.dto.AircraftMapDto;
import net.simforge.airways2.app.dto.FlightMinDto;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/map")
@CrossOrigin
public class MapController {
    private static final int RECENT_FLIGHTS_LIMIT = 10;

    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/aircraft/flying")
    public List<AircraftMapDto> getFlyingAircraft() {
        // todo ak2 separate thread to build this data outside of web-request and provide caching of this big dataset
        return worldBean.read(world -> world.aircrafts()
                .filter(world.aircrafts().byLocationStatus(Aircrafts.LocationStatus.Flying))
                .map(AircraftMapDto::from)
                .sorted(Comparator.comparing(AircraftMapDto::getAcReg))
                .toList());
    }

    @GetMapping("/aircraft/info")
    public AircraftInfoDto getAircraftInfo(@RequestParam("id") String aircraftId) {
        return worldBean.read(world -> AircraftInfoDto.from(world,
                world.aircrafts()
                        .byId(Id.decode(aircraftId))
                        .orElseThrow(),
                true));
    }

    @GetMapping("/aircraft/details")
    public AircraftDetailsDto getAircraftDetails(@RequestParam("id") String aircraftId) {
        return worldBean.read(world -> {
            final Aircrafts.Aircraft aircraft = world.aircrafts()
                    .byId(Id.decode(aircraftId))
                    .orElseThrow();
            final List<FlightMinDto> recentFlights = world.flightMissions().allForAircraft(aircraft)
                    .filter(fm -> fm.getStatus() == FlightMissions.Status.Finished)
                    .sorted(FlightMissions.sortByDepartureTimeFromFutureToPast)
                    .limit(RECENT_FLIGHTS_LIMIT)
                    .map(fm -> FlightMinDto.from(world, fm))
                    .toList();
            return new AircraftDetailsDto(recentFlights);
        });
    }
}
