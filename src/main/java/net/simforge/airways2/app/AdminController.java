package net.simforge.airways2.app;

import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.commons.io.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@CrossOrigin
public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/log/full")
    public ResponseEntity<byte[]> getFullLog() throws IOException {
        byte[] bytes = IOHelper.loadFile(new File("./logs/logback.log")).getBytes();

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(bytes.length)
                .body(bytes);
    }

    @GetMapping("/log/tail")
    public ResponseEntity<byte[]> getLogTail() throws IOException {
        final File file = new File("./logs/logback.log");
        try (final RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() > 1_000_000) {
                raf.seek(raf.length() - 1_000_000);
            }

            byte[] bytes = new byte[1_000_000];
            raf.readFully(bytes);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .contentLength(bytes.length)
                    .body(bytes);
        }
    }

    @GetMapping("/vatsim-flight-stats")
    public Map<String, Integer> getVatsimFlightStats() {
        return FlightStats.getStats();
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
}
