package net.simforge.airways2.app.pblc;

import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.dto.AircraftFullDto;
import net.simforge.airways2.app.dto.AircraftMapDto;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.world.datamodel.Aircrafts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/map")
@CrossOrigin
public class MapController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/aircraft/flying")
    public List<AircraftMapDto> getFlyingAircraft() {
        // todo ak2 separate thread to build this data outside of web-request and provide caching of this big dataset
        try (final Timing.Timer ignored = Timing.label("MapController - getFlyingAircraft")) {
            return worldBean.read(world -> world.aircrafts()
                    .filter(world.aircrafts().byLocationStatus(Aircrafts.LocationStatus.Flying))
                    .map(AircraftMapDto::from)
                    .toList());
        }
    }

    @GetMapping("/aircraft/details")
    public AircraftFullDto getAircraftDetails(@RequestParam("id") String aircraftId) {
        return worldBean.read(world -> AircraftFullDto.from(world,
                world.aircrafts()
                        .byId(Id.decode(aircraftId))
                        .orElseThrow(),
                true));
    }
}
