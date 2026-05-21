package net.simforge.airways2.app.pblc;

import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.dto.AircraftMapDto;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.world.datamodel.Aircrafts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/map")
@CrossOrigin
public class MapController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/flying")
    public List<AircraftMapDto> getFlyingAircraft() {
        // todo ak2 separate thread to build this data outside of web-request and provide caching of this big dataset
        try (final Timing.Timer ignored = Timing.label("MapController - getFlyingAircraft")) {
            return worldBean.read(world -> world.aircrafts()
                    .filter(world.aircrafts().byLocationStatus(Aircrafts.LocationStatus.Flying))
                    .map(a -> AircraftMapDto.from(world, a))
                    .toList());
        }
    }
}
