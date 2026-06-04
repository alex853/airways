package net.simforge.airways2.app.admin;

import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.dto.AircraftFullDto;
import net.simforge.airways2.app.vatsimtracker.VatsimTrackerBean;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.processors.AircraftHelper;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Str;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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

    @GetMapping(value = "/frozen-list", produces = "text/plain")
    public String getFrozenList() {
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

    @GetMapping(value = "/suspicious-list", produces = "text/plain")
    public String getSuspiciousList(@RequestParam(name = "dry-run", required = false, defaultValue = "true") boolean dryRun) {
        return worldBean.modifySync(world -> {
            List<String> results = new ArrayList<>();

            AtomicInteger updated = new AtomicInteger(0);
            world.aircrafts().all()
                    .filter(a -> a.getAircraftOperatorId() == World25.ShadowJetOperatorId
                            && a.getLastUpdated() + Time.ONE_DAY < world.getWorldTime()
                            && a.getOperationalStatus() == Aircrafts.OperationalStatus.Active)
                    .forEach(a -> {
                        Optional<FlightMissions.Mission> fm = world.flightMissions().byId(a.getFlightMissionId());
                        results.add(Str.al(String.valueOf(a.getId()), 5) + "\t" +
                                Str.al(String.valueOf(Time.toLdtOrNull(a.getLastUpdated())), 19) + "\t" +
                                a.getRegNo() + "\t" +
                                a.getOperationalStatus().name() + "\t" +
                                a.getLocationStatus().name() + "\t" +
                                a.getFlownCycles() + "\t" +
                                a.getFlightMissionId() + "\t" +
                                (a.getFlightMissionId() > 0 && vatsimTracker.getContextByFlightMissionId(a.getFlightMissionId()).isPresent()) + "\t" +
                                (a.getFlightMissionId() > 0 ? fm.map(f -> "exist").orElse("absent") : "f/m 0") + "\t" +
                                fm.map(f -> f.getAircraftId() == a.getId() ? "a/c OK" : "a/c F/L").orElse("f/m N/F") + "\t" +
                                fm.map(f -> f.getStatus().name()).orElse("n/a"));

                        if (!dryRun && updated.get() < 10) {
                            if (fm.isPresent()) {
                                updated.incrementAndGet();
                                AircraftHelper.releaseAndParkAircraft(world, a);
                                results.add("A/C #" + a.getId() + ", " + a.getRegNo() + " is parked in " + world.airports().getIcao(a.getLocationAirportId()).orElseThrow());
                            } else if (a.getLocationAirportId() > 0) {
                                updated.incrementAndGet();
                                a.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);
                                a.setOperationalStatus(Aircrafts.OperationalStatus.Idle);
                                a.setFlightMissionId(0);
                                AircraftHelper.moveParkedAircraftToAnotherAirport(world, a, world.airports().byId(a.getLocationAirportId()).orElseThrow());
                                results.add("A/C #" + a.getId() + " is parked in airport #" + world.airports().getIcao(a.getLocationAirportId()).orElseThrow());
                            } else {
                                results.add("DONT KNOW WHAT TO DO HERE");
                            }
                        }
                    });

            return Strings.join(results, '\n');
        });
    }

    @GetMapping("/reset-status")
    public String getFrozenList(@RequestParam(name = "aircraftId") final int aircraftId) {
        return worldBean.modifySync(world -> {
            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(aircraftId).orElseThrow();

            aircraft.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);
            aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Idle);
            aircraft.setFlightMissionId(0);

            aircraft.setLastUpdated(world.getWorldTime());

            return "A/C #" + aircraft.getId() + " is parked in airport #" + aircraft.getLocationAirportId();
        });
    }

    @GetMapping("/move-to-airport")
    public String moveAircraftToAirport(@RequestParam(name = "aircraftId") final int aircraftId, @RequestParam(name = "airportId") final int airportId) {
        return worldBean.modifySync(world -> {
            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(aircraftId).orElseThrow();
            final Airports.Airport airport = world.airports().byId(airportId).orElseThrow();

            AircraftHelper.moveParkedAircraftToAnotherAirport(world, aircraft, airport);

            return "A/C #" + aircraft.getId() + " is parked in airport #" + aircraft.getLocationAirportId();
        });
    }
}
