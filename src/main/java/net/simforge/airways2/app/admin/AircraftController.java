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
import org.apache.logging.log4j.util.Strings;
import org.checkerframework.checker.units.qual.A;
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
        return worldBean.read(world -> {
            List<String> results = new ArrayList<>();

            AtomicInteger updated = new AtomicInteger(0);
            world.aircrafts().all()
                    .filter(a -> // a.getLocationStatus() == Aircrafts.LocationStatus.Flying
                            //&& a.getOperationalStatus() == Aircrafts.OperationalStatus.Active
                            //&& a.getLocationAirportId() == 0
                            //&&
                            a.getAircraftOperatorId() == World25.ShadowJetOperatorId
                            && a.getLastUpdated() + Time.ONE_DAY < world.getWorldTime())
                    .forEach(a -> {
                        Optional<FlightMissions.Mission> fm = world.flightMissions().byId(a.getFlightMissionId());
                        results.add(a.getId() + "\t" +
                                a.getLastUpdated() + "\t" +
                                a.getRegNo() + "\t" +
                                a.getOperationalStatus().name() + "\t" +
                                a.getLocationStatus().name() + "\t" +
                                a.getFlownCycles() + "\t" +
                                a.getFlightMissionId() + "\t" +
                                (a.getFlightMissionId() > 0 && vatsimTracker.getContextByFlightMissionId(a.getFlightMissionId()).isPresent()) + "\t" +
                                (a.getFlightMissionId() > 0 ? fm.map(f -> "exist").orElse("absent") : "f/m 0") + "\t" +
                                fm.map(f -> f.getAircraftId() == a.getId() ? "a/c ok" : "a/c fail").orElse("n/f") + "\t" +
                                fm.map(f -> f.getStatus().name()).orElse("n/a"));

                        if (!dryRun && updated.get() < 10) {
                            if (fm.isPresent()) {
                                updated.incrementAndGet();
                                AircraftHelper.releaseAndParkAircraft(world, fm.get());
                                results.add("A/C #" + a.getId() + ", " + a.getRegNo() + " is parked in " + world.airports().getIcao(a.getLocationAirportId()).orElseThrow());
                            } else {
                                results.add("CAN'T FIND FLIGHT MISSION");
                            }
                        }
                    });

            return Strings.join(results, '\n');
        });
    }

    @GetMapping(value = "/remove-missions-with-missing-aircraft", produces = "text/plain")
    public String resetAircraftStatus2() {
        return worldBean.modifySync(world -> {
            List<String> results = new ArrayList<>();

            List<FlightMissions.Mission> fms = world.flightMissions().all()
                    .filter(fm -> fm.getAircraftId() > 3000 && world.aircrafts().byId(fm.getAircraftId()).isEmpty())
                    .toList();
            results.add("Found " + fms.size());

            fms.forEach(fm -> world.flightMissions().deleteById(fm.getId()));
            results.add("Deleted " + fms.size());

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
