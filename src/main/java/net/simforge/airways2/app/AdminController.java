package net.simforge.airways2.app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.app.vatsimtracker.PilotContext;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.world.processors.AircraftHelper;
import net.simforge.commons.io.IOHelper;
import net.simforge.commons.misc.JavaTime;
import net.simforge.commons.misc.Str;
import org.apache.logging.log4j.util.Strings;
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
import java.util.*;
import java.util.stream.Collectors;

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

        byte[] bytes = IOHelper.loadFile(new File("./logs/logback." + date + ".log")).getBytes();

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

        return loadFlightStats(date);
    }

    @GetMapping(value = "/vatsim/flight-stats/top-missing-airports", produces = "text/plain")
    public String getTopMissingAirports() {
        LocalDate date = JavaTime.todayUtc();
        final Map<String, Integer> allMissingAirports = new TreeMap<>();
        for (int i = 0; i <= 7; i++) {
            final Map<String, Integer> dateData = loadFlightStats(date.toString());
            dateData.entrySet().stream()
                    .filter(e -> e.getKey().startsWith("missingAirport"))
                    .collect(Collectors.toMap(
                            entry -> entry.getKey().substring("missingAirport ".length()),
                            Map.Entry::getValue))
                    .forEach((key, value) -> allMissingAirports.merge(key, value, Integer::sum));

            date = date.minusDays(1);
        }

        return allMissingAirports.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(50)
                .map(entry -> Str.al(entry.getKey(), 10) + " " + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    private static Map<String, Integer> loadFlightStats(final String date) {
        final File file = new File(FlightStats.statsRoot, date + ".json");
        final String json;
        try {
            json = IOHelper.loadFile(file);
        } catch (IOException e) {
            log.error("unable to load flight stats data", e);
            return new HashMap<>();
        }
        final Type type = new TypeToken<Map<String, Integer>>(){}.getType();
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

    @GetMapping("/vatsim/remove-context-by-pilot-number")
    public String removeVatsimContextByPilotNumber(@RequestParam(name = "pilotNumber") final int pilotNumber) {
        final Optional<PilotContext> pc = vatsimTracker.contexts().stream()
                .filter(f -> f.getPilotNumber() == pilotNumber)
                .findFirst();
        if (pc.isEmpty()) {
            return "Pilot context for p/n # " + pilotNumber + " NOT FOUND";
        }
        vatsimTracker.removePilot(pc.get().getPilotNumber());
        return "Pilot context for p/n # " + pilotNumber + " REMOVED";
    }

    @GetMapping("/flight/cancel")
    public String cancelFlight(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission mission = world.flightMissions().byId(flightId).orElseThrow();
            mission.setStatus(FlightMissions.Status.Cancelled);
            final Aircrafts.Aircraft aircraft = releaseAndParkAircraft(world, mission);
            return "F/M # " + flightId + " cancelled, A/C # " + aircraft.getId() + " is parked in airport # " + aircraft.getLocationAirportId();
        });
    }

    @GetMapping("/flight/remove")
    public String removeFlight(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission mission = world.flightMissions().byId(flightId).orElseThrow();
            final Aircrafts.Aircraft aircraft = releaseAndParkAircraft(world, mission);
            world.flightMissions().deleteById(flightId);
            return "F/M # " + flightId + " removed, A/C # " + aircraft.getId() + " is parked in airport # " + aircraft.getLocationAirportId();
        });
    }

    private static Aircrafts.Aircraft releaseAndParkAircraft(final World world, final FlightMissions.Mission mission) {
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
        return aircraft;
    }

    @GetMapping("/transport-flight/cancel")
    public String cancelTransportFlight(@RequestParam(name = "tfId") final int tfId) {
        return worldBean.modifySync(world -> {
            final TransportFlights.Flight flight = world.transportFlights().byId(tfId).orElseThrow();
            flight.setStatus(TransportFlights.Status.Cancelled);
            return "T/F # " + tfId + " cancelled";
        });
    }

    @GetMapping("/transport-flight/remove")
    public String removeTransportFlight(@RequestParam(name = "tfId") final int tfId) {
        return worldBean.modifySync(world -> {
            final List<String> results = new ArrayList<>();

            final TransportFlights.Flight transportFlight = world.transportFlights().byId(tfId).orElseThrow();
            final int flightId = transportFlight.getFlightMissionId();
            final int scheduledFlightId = transportFlight.getScheduledFlightId();
            world.transportFlights().deleteById(tfId);
            results.add("T/F # " + tfId + " removed");

            if (flightId == 0) {
                results.add("F/M # is 0");
            } else {
                final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(flightId);
                if (mission.isEmpty()) {
                    results.add("F/M # " + flightId + " NOT FOUND");
                }
            }

            if (scheduledFlightId == 0) {
                results.add("S/F # is 0");
            } else {
                final Optional<ScheduledFlights.Flight> scheduledFlight = world.scheduledFlights().byId(scheduledFlightId);
                if (scheduledFlight.isEmpty()) {
                    results.add("S/F # " + scheduledFlightId + " NOT FOUND");
                } else {
                    world.scheduledFlights().deleteById(scheduledFlightId);
                }
            }

            return Strings.join(results, '\n');
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

    @GetMapping("/journey/kick-all-looking-for-tickets")
    public String kickAllLookingForTickets() {
        return worldBean.modifySync(world -> {
            world.journeys()
                    .filter(j -> j.getStatus() == Journeys.Status.LookingForTickets)
                    .forEach(j -> j.setHeartbeatTime(world.getWorldTime()));
            return "DONE";
        });
    }

    @GetMapping("/journey/set-heartbeat-to-now")
    public String setHeartbeatToNow(@RequestParam(name = "jId") final int journeyId) {
        return worldBean.modifySync(world -> {
            world.journeys().byId(journeyId).orElseThrow().setHeartbeatTime(world.getWorldTime());
            return "DONE";
        });
    }

    @GetMapping("/fix-682")
    public void fix682() {
        worldBean.modifySync(world -> {
            final int flightId = 682;

            final TransportFlights.Flight transportFlight = world.transportFlights().byFlightMissionId(flightId).orElse(null);

            transportFlight.setHeartbeatTime(world.getWorldTime());

            return null;
        });
    }

    @GetMapping("/fix-129")
    public void fix() {
        worldBean.modifySync(world -> {
            final int journeyId = 129;

            final Journeys.Journey journey = world.journeys().byId(journeyId).orElse(null);

            journey.setHeartbeatTime(world.getWorldTime() + 5 * Time.ONE_MINUTE);

            return null;
        });
    }

    @GetMapping("/reset-city-redistribution")
    public void resetCityRedistribution() {
        worldBean.modifySync(world -> {
            world.cityFlows().all().forEach(cf -> cf.setLastRedistributionTime(0));

            return null;
        });
    }

    @GetMapping("/f1-tour-fixes")
    public void f1TourFixes() {
        worldBean.modifySync(world -> {
            final Cities.City sanFrancisco = world.cities().all().stream().filter(c -> c.getName().equalsIgnoreCase("San Francisco")).findFirst().orElseThrow();
            sanFrancisco.setName("San Francisco");
            sanFrancisco.setPopulation(7520000);

            return null;
        });
    }
}
