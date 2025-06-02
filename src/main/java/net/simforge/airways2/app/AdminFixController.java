package net.simforge.airways2.app;

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
import java.io.IOException;

@RestController
@RequestMapping("/admin-fix")
@CrossOrigin
public class AdminFixController {
    private static final Logger log = LoggerFactory.getLogger(AdminFixController.class);

    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/log")
    public ResponseEntity<byte[]> downloadDirect() throws IOException {
        byte[] bytes = IOHelper.loadFile(new File("./logs/logback.log")).getBytes();

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .contentLength(bytes.length)
                .body(bytes);
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

    @GetMapping("/aircraft/reset-status")
    public String resetAircraftStatus(@RequestParam(name = "aircraftId") final int aircraftId) {
        return worldBean.modifySync(world -> {
            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(aircraftId).orElseThrow();

            aircraft.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);
            aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Idle);
            aircraft.setFlightMissionId(0);

            return "A/C # " + aircraft + " is parked in airport # " + aircraft.getLocationAirportId();
        });
    }
}
