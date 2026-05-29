package net.simforge.airways2.app.admin;

import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.dto.AircraftFullDto;
import net.simforge.airways2.app.vatsimtracker.VatsimTrackerBean;
import net.simforge.airways2.world.datamodel.Aircrafts;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/admin/aircraft")
@CrossOrigin
public class AircraftController {
    @Autowired
    private WorldRunnerBean worldBean;
    @Autowired
    private VatsimTrackerBean vatsimTracker;

    @GetMapping("/all")
    public List<AircraftFullDto> getAll(@RequestParam(name = "offset", required = false) final Integer offset,
                                        @RequestParam(name = "limit", required = false) final Integer limit) {
        return worldBean.read(world -> world.aircrafts().all()
                .skip(offset != null ? offset : 0)
                .limit(limit != null ? limit : Long.MAX_VALUE)
                .map(a -> AircraftFullDto.from(world, a, false))
                .toList());
    }

    @GetMapping(name = "/frozen-list", produces = "text/plain")
    public String resetAircraftStatus() {
        return worldBean.read(world -> {
            List<String> results = new ArrayList<>();

            List<Aircrafts.Aircraft> aircrafts = world.aircrafts().all()
                    .filter(a -> a.getLocationStatus() == Aircrafts.LocationStatus.ParkedAtAirport
                            && a.getOperationalStatus() == Aircrafts.OperationalStatus.Active
                            && a.getLocationAirportId() > 0)
                    .toList();

            aircrafts.forEach(a -> {
                results.add(a.getId() + "\t" +
                        a.getRegNo() + "\t" +
                        a.getFlownCycles() + "\t" +
                        a.getFlightMissionId() + "\t" +
                        (a.getFlightMissionId() > 0 && vatsimTracker.getContextByFlightMissionId(a.getFlightMissionId()).isPresent()));
            });

            return Strings.join(results, '\n');
        });
    }
}
