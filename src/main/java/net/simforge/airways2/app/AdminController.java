package net.simforge.airways2.app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.app.vatsimtracker.PilotContext;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.processors.AircraftHelper;
import net.simforge.commons.io.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@RestController
@RequestMapping("/admin")
@CrossOrigin
public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();;

    @Autowired
    private WorldRunnerBean worldBean;
    @Autowired
    private VatsimTrackerBean vatsimTracker;

    @GetMapping("/log/full")
    public ResponseEntity<byte[]> getFullLog() throws IOException {
        byte[] bytes = IOHelper.loadFile(new File("./logs/logback.log")).getBytes();

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(bytes.length)
                .body(bytes);
    }

    @GetMapping("/log/date/{date}")
    public ResponseEntity<byte[]> getFullLog(@PathVariable final String date) throws IOException {
        checkArgument(date.length() == 10);
        checkNotNull(LocalDate.parse(date));

        byte[] bytes = IOHelper.loadFile(new File("./logs/" + date + ".log")).getBytes();

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(bytes.length)
                .body(bytes);
    }

    @GetMapping("/log/tail")
    public ResponseEntity<byte[]> getLogTail() throws IOException {
        final File file = new File("./logs/logback.log");
        try (final RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            final int maxLength = 100_000;

            if (raf.length() > maxLength) {
                raf.seek(raf.length() - maxLength);
            }

            final byte[] bytes = new byte[(int) Math.min(maxLength, raf.length())];
            raf.readFully(bytes);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .contentLength(bytes.length)
                    .body(bytes);
        }
    }

    @GetMapping("/vatsim/flight-stats")
    public Map<String, Integer> getVatsimFlightStats() {
        return FlightStats.getStats();
    }

    @GetMapping("/vatsim/flight-stats/date/{date}")
    public Map<String, Integer> getVatsimFlightStats(@PathVariable final String date) {
        checkArgument(date.length() == 10);
        checkNotNull(LocalDate.parse(date));

        final File file = new File(FlightStats.statsRoot, date + ".json");
        final String json;
        try {
            json = IOHelper.loadFile(file);
        } catch (IOException e) {
            log.error("unable to load flight stats data", e);
            return null;
        }
        Type type = new TypeToken<Map<String, Integer>>(){}.getType();
        return gson.fromJson(json, type);
    }

    @GetMapping("/vatsim/remove-context-by-flight")
    public String removeVatsimContextByFlight(@RequestParam(name = "flightId") final int flightId) {
        final Optional<PilotContext> pc = vatsimTracker.contexts().stream()
                .filter(f -> f.getFlightMissionId() == flightId)
                .findFirst();
        if (pc.isEmpty()) {
            return "Pilot context for f/m # " + flightId + " NOT FOUND";
        }
        vatsimTracker.removePilot(pc.get().getPilotNumber());
        return "Pilot context for f/m # " + flightId + " REMOVED";
    }

    @GetMapping("/flight/cancel")
    public String cancelFlight(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission mission = world.flightMissions().byId(flightId).orElseThrow();
            mission.setStatus(FlightMissions.Status.Cancelled);

            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
            if (aircraft.getLocationStatus() != Aircrafts.LocationStatus.Flying) {
                aircraft.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);

                aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Idle);
                aircraft.setFlightMissionId(0);
            } else {
                final Airports.Airport departureAirport = world.airports().byId(mission.getDepartureAirportId()).orElseThrow();

                aircraft.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);
                aircraft.setLocationAirportId(mission.getDepartureAirportId());
                aircraft.setLocationLatitude(departureAirport.getLatitude());
                aircraft.setLocationLongitude(departureAirport.getLongitude());

                aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Idle);
                aircraft.setFlightMissionId(0);
            }
            return "F/M # " + flightId + " cancelled, A/C # " + aircraft.getId() + " is parked in airport # " + aircraft.getLocationAirportId();
        });
    }

    @GetMapping("/flight/delete")
    public String deleteFlight(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            world.flightMissions().deleteById(flightId);
            return "F/M # " + flightId + " deleted";
        });
    }

    @GetMapping("/aircraft/reset-status")
    public String resetAircraftStatus(@RequestParam(name = "aircraftId") final int aircraftId) {
        return worldBean.modifySync(world -> {
            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(aircraftId).orElseThrow();

            aircraft.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);
            aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Idle);
            aircraft.setFlightMissionId(0);

            return "A/C # " + aircraft.getId() + " is parked in airport # " + aircraft.getLocationAirportId();
        });
    }

    @GetMapping("/aircraft/move-to-airport")
    public String moveAircraftToAirport(@RequestParam(name = "aircraftId") final int aircraftId, @RequestParam(name = "airportId") final int airportId) {
        return worldBean.modifySync(world -> {
            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(aircraftId).orElseThrow();
            final Airports.Airport airport = world.airports().byId(airportId).orElseThrow();

            AircraftHelper.moveParkedAircraftToAnotherAirport(world, aircraft, airport);

            return "A/C # " + aircraft.getId() + " is parked in airport # " + aircraft.getLocationAirportId();
        });
    }
}
