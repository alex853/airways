package net.simforge.airways2.app.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.app.vatsimtracker.VatsimTrackerBean;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.app.vatsimtracker.PilotContext;
import net.simforge.airways2.tools.Formatting;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.world.processors.AircraftHelper;
import net.simforge.airways2.world.processors.City2CityFlowHistogramAnalyser;
import net.simforge.airways2.world.processors.CityFlowHelper;
import net.simforge.airways2.world.processors.TransportFlightHelper;
import net.simforge.commons.io.IOHelper;
import net.simforge.commons.misc.JavaTime;
import net.simforge.commons.misc.Str;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.RandomAccessFile;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
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
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @SuppressWarnings("SpringJavaAutowiredFieldsWarningInspection")
    @Autowired
    private WorldRunnerBean worldBean;

    @SuppressWarnings("SpringJavaAutowiredFieldsWarningInspection")
    @Autowired
    private VatsimTrackerBean vatsimTracker;

    // The log files are big, they are streamed from the disk and never loaded into the memory (1 GB host).
    @GetMapping("/log/full")
    public ResponseEntity<Resource> getFullLog() {
        return streamLogFile(new File("./logs/logback.log"));
    }

    @GetMapping("/log/date/{date}")
    public ResponseEntity<Resource> getFullLog(@PathVariable final String date) {
        checkArgument(date.length() == 10);
        final LocalDate parsedDate = checkNotNull(LocalDate.parse(date));

        final String fileName = String.format("logback.%04d-%02d-%02d.log",
                parsedDate.getYear(), parsedDate.getMonthValue(), parsedDate.getDayOfMonth());
        // the log of the current day is in logback.log, the dated file appears after the rotation at midnight
        return streamLogFile(new File("./logs/" + fileName));
    }

    private static ResponseEntity<Resource> streamLogFile(final File file) {
        if (!file.isFile()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                // no content length: the current log is being written while it is sent, so the size is not known in advance
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(new FileSystemResource(file));
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

    /**
     * Searches log lines, q may contain several alternatives separated by '||', a line matches if it contains any of them,
     * e.g. q=j/y #33742||t/f #9467||f/m #4986
     */
    // Qodana's taint analysis reports XSS: false positive, q is only a filter and is never written to the response,
    // the response is text/plain and /admin/** requires the admin token (AuthFilter).
    @SuppressWarnings("JvmTaintAnalysis")
    @GetMapping("/log/search")
    public ResponseEntity<StreamingResponseBody> searchLog(@RequestParam final String q,
                                                           @RequestParam(defaultValue = "false") final boolean all) {
        checkArgument(!q.isEmpty(), "Query must not be empty");

        final List<String> terms = Arrays.stream(q.split("\\|\\|"))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .toList();
        checkArgument(!terms.isEmpty(), "Query must have at least one non-empty term");

        final List<File> files = new ArrayList<>();
        if (all) {
            final File[] historical = new File("./logs").listFiles((dir, name) -> name.matches("logback\\.\\d{4}-\\d{2}-\\d{2}\\.log"));
            if (historical != null) {
                Arrays.sort(historical, Comparator.comparing(File::getName));
                files.addAll(Arrays.asList(historical));
            }
        }
        files.add(new File("./logs/logback.log"));

        // The matching lines are written to the response one by one, they are never collected in the memory (1 GB host).
        final StreamingResponseBody body = outputStream -> {
            final Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            for (final File file : files) {
                boolean headerPrinted = false;
                try (final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (terms.stream().anyMatch(line::contains)) {
                            if (all && !headerPrinted) {
                                writer.write("=== " + file.getName() + " ===\n");
                                headerPrinted = true;
                            }
                            writer.write(line);
                            writer.write('\n');
                        }
                    }
                }
            }
            writer.flush();
        };

        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(body);
    }

    @GetMapping(value = "/timing", produces = "text/plain")
    public String getTiming() {
        return Timing.printStatusToString();
    }

    @GetMapping("/flight-stats")
    public Map<String, Integer> getFlightStats() {
        return FlightStats.getStats();
    }

    @GetMapping("/flight-stats/date/{date}")
    public Map<String, Integer> getFlightStats(@PathVariable final String date) {
        checkArgument(date.length() == 10);
        final LocalDate parsedDate = checkNotNull(LocalDate.parse(date));

        return loadFlightStats(parsedDate.toString());
    }

    @GetMapping(value = "/flight-stats/vatsim-top-missing-airports", produces = "text/plain")
    public String getTopMissingAirports() {
        LocalDate date = JavaTime.todayUtc();
        final Map<String, Integer> allMissingAirports = new TreeMap<>();
        for (int i = 0; i <= 7; i++) {
            final Map<String, Integer> dateData = loadFlightStats(date.toString());
            dateData.entrySet().stream()
                    .filter(e -> e.getKey().startsWith("vatsim - missingAirport"))
                    .collect(Collectors.toMap(
                            entry -> entry.getKey().substring("vatsim - missingAirport ".length()),
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
            return "Pilot context for f/m #" + flightId + " NOT FOUND";
        }
        vatsimTracker.removePilot(pc.get().getPilotNumber());
        return "Pilot context for f/m #" + flightId + " REMOVED";
    }

    @GetMapping("/vatsim/remove-context-by-pilot-number")
    public String removeVatsimContextByPilotNumber(@RequestParam(name = "pilotNumber") final int pilotNumber) {
        final Optional<PilotContext> pc = vatsimTracker.contexts().stream()
                .filter(f -> f.getPilotNumber() == pilotNumber)
                .findFirst();
        if (pc.isEmpty()) {
            return "Pilot context for p/n #" + pilotNumber + " NOT FOUND";
        }
        vatsimTracker.removePilot(pc.get().getPilotNumber());
        return "Pilot context for p/n #" + pilotNumber + " REMOVED";
    }

    @GetMapping(value = "/flight/cancel", produces = "text/plain")
    public String cancelFlight(@RequestParam(name = "id") String fmIdStr) {
        return worldBean.modifySync(world -> {
            int fmId = Id.decode(fmIdStr);

            List<String> results = new ArrayList<>();

            FlightMissions.Mission fm = world.flightMissions().byId(fmId).orElseThrow();
            fm.setStatus(FlightMissions.Status.Cancelled);
            results.add("F/M #" + fmId + " cancelled");

            // todo ak2 cancelling a flight while the flight is not active or is not flying should not update an aircraft as this will affect another flight if there is any one is in progress
            Aircrafts.Aircraft aircraft = AircraftHelper.releaseAndParkAircraft(world, world.aircrafts().byId(fm.getAircraftId()).orElseThrow());
            results.add("A/C #" + aircraft.getId() + ", " + aircraft.getRegNo() + " is parked in " + world.airports().getIcao(aircraft.getLocationAirportId()).orElseThrow());
            world.personControl().onMissionCancelled(fm, aircraft.getLocationAirportId());

            Optional<TransportFlights.Flight> tf = world.transportFlights().byFlightMissionId(fmId);
            if (tf.isPresent()) {
                world.transportFlightControl().unloadJourneysForcefullyFromActiveFlight(tf.get());
                tf.get().setStatus(TransportFlights.Status.Cancelled);
                results.add("T/F #" + tf.get().getId() + " cancelled");
            } else {
                results.add("T/F not found");
            }

            return Strings.join(results, '\n');
        });
    }

    @GetMapping("/flight/remove")
    public String removeFlight(@RequestParam(name = "flightId") final int flightId) {
        return worldBean.modifySync(world -> {
            final FlightMissions.Mission mission = world.flightMissions().byId(flightId).orElseThrow();
            final Aircrafts.Aircraft aircraft = AircraftHelper.releaseAndParkAircraft(world, world.aircrafts().byId(mission.getAircraftId()).orElseThrow());
            world.pilotContracts().deleteByFlightMissionId(flightId);
            world.flightMissions().deleteById(flightId);
            return "F/M #" + flightId + " removed, A/C #" + aircraft.getId() + " is parked in airport #" + aircraft.getLocationAirportId();
        });
    }

    @GetMapping(value = "/flight/remove-obsolete", produces = "text/plain")
    public String removeObsoleteFlights(@RequestParam(name = "days", defaultValue = "90") final int days,
                                        @RequestParam(name = "max", defaultValue = "10") final int max) {
        String result = "Days: " + days + ", Max: " + max + "\n";

        List<FlightMissions.Mission> flightsToRemove = worldBean.read(world -> {
            final int thresholdTime = world.getWorldTime() - days * Time.ONE_DAY;
            return world.flightMissions().all()
                    .filter(f -> f.getPlannedDepartureWorldTime() <= thresholdTime)
                    .toList();
        });

        result = result + "Found " + flightsToRemove.size() + " obsolete flights\n";
        int count = Math.min(flightsToRemove.size(), max);
        result = result + count + " will be removed\n";

        for (int i = 0; i < count; i++) {
            removeFlight(flightsToRemove.get(i).getId());
        }

        result = result + "Done\n";

        return result;
    }

    @GetMapping(value = "/flight/heartbeat-time-index", produces = "text/plain")
    public String showFlightMissionHeartbeatTimeIndex() {
        return worldBean.read(world -> world.flightMissions().printHeartbeatTimeIndex());
    }

    @GetMapping(value = "/flight/clear-user-id", produces = "text/plain")
    public String clearUserId() {
        worldBean.modifySync(world -> {
            world.flightMissions().allByUserId(1).forEach(m -> m.setUserId(0));
            return null;
        });
        return "Done";
    }

    @GetMapping("/transport-flight/cancel")
    public String cancelTransportFlight(@RequestParam(name = "tfId") final int tfId) {
        return worldBean.modifySync(world -> {
            final TransportFlights.Flight flight = world.transportFlights().byId(tfId).orElseThrow();
            flight.setStatus(TransportFlights.Status.Cancelled);
            return "T/F #" + tfId + " cancelled";
        });
    }

    @GetMapping("/transport-flight/remove")
    public String removeTransportFlight(@RequestParam(name = "tfId") final int tfId) {
        return worldBean.modifySync(world -> {
            final List<String> results = new ArrayList<>();

            final TransportFlights.Flight transportFlight = world.transportFlights().byId(tfId).orElseThrow();
            final int flightId = transportFlight.getFlightMissionId();
            final int scheduledFlightId = transportFlight.getScheduledFlightId();
            world.transportFlightExtras().deleteByTransportFlightId(tfId);
            world.transportFlights().deleteById(tfId);
            results.add("T/F #" + tfId + " removed");

            if (flightId == 0) {
                results.add("F/M # is 0");
            } else {
                final Optional<FlightMissions.Mission> mission = world.flightMissions().byId(flightId);
                if (mission.isEmpty()) {
                    results.add("F/M #" + flightId + " NOT FOUND");
                }
            }

            if (scheduledFlightId == 0) {
                results.add("S/F # is 0");
            } else {
                final Optional<ScheduledFlights.Flight> scheduledFlight = world.scheduledFlights().byId(scheduledFlightId);
                if (scheduledFlight.isEmpty()) {
                    results.add("S/F #" + scheduledFlightId + " NOT FOUND");
                } else {
                    world.scheduledFlights().deleteById(scheduledFlightId);
                }
            }

            return Strings.join(results, '\n');
        });
    }

    @GetMapping(value = "/transport-flight/remove-all-broken", produces = "text/plain")
    public String removeAllBrokenTransportFlight() {
        return worldBean.modifySync(world -> {
            final List<String> results = new ArrayList<>();

            final List<TransportFlights.Flight> brokenTfs = world.transportFlights().all()
                    .filter(tf -> world.flightMissions().byId(tf.getFlightMissionId()).isEmpty())
                    .toList();
            results.add("Found " + brokenTfs.size() + " broken transport flights");

            int tfCount = 0;
            int sfCount = 0;
            for (int i = 0; i < Math.min(10, brokenTfs.size()); i++) {
                TransportFlights.Flight tf = brokenTfs.get(i);
                int scheduledFlightId = tf.getScheduledFlightId();

                world.transportFlightExtras().deleteByTransportFlightId(tf.getId());
                world.transportFlights().deleteById(tf.getId());
                results.add("T/F #" + tf.getId() + " removed");
                tfCount++;

                if (world.scheduledFlights().byId(scheduledFlightId).isPresent()) {
                    world.scheduledFlights().deleteById(scheduledFlightId);
                    results.add("S/F #" + scheduledFlightId + " removed");
                    sfCount++;
                }
            }

            results.add("Removed " + tfCount + " transport flights including " + sfCount + " scheduled flights");

            return Strings.join(results, '\n');
        });
    }

    @GetMapping("/journey/kick-all-looking-for-tickets")
    public String kickAllLookingForTickets() {
        return worldBean.modifySync(world -> {
            world.journeys()
                    .filter(world.journeys().byStatus(Journeys.Status.LookingForTickets))
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

    @GetMapping("/journey/remove")
    public String journeyFlight(@RequestParam(name = "journeyId") final int journeyId) {
        return worldBean.modifySync(world -> {
            world.journeys().deleteById(journeyId);
            return "J/Y #" + journeyId + " removed";
        });
    }

    @GetMapping("/journey/delete-all-without-heartbeat")
    public String deleteAllJourneysWithoutHeartbeat() {
        return worldBean.modifySync(world -> {
            world.journeys().allWithZeroHeartbeat().forEach(j -> world.journeys().deleteById(j.getId()));
            return "DONE";
        });
    }

    @GetMapping(value = "/journey/delete-invalid", produces = "text/plain")
    public String deleteInvalidJourneys(@RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun) {
        return worldBean.modifySync(world -> {
            final String deletedLabel = dryRun ? "  would be deleted  " : "  deleted . . . . . ";
            final String updatedLabel = dryRun ? "  would be updated  " : "  updated . . . . . ";

            String result = dryRun ? "DRY RUN - nothing is changed\n\n" : "";



            List<Journeys.Journey> waitingForBoardingWithBrokenTransportFlights = world.journeys().allWithZeroHeartbeat()
                    .filter(j -> j.getStatus() == Journeys.Status.WaitingForBoarding)
                    .filter(j -> world.transportFlights().byId(j.getTransportFlight1Id()).isEmpty())
                    .toList();
            result = result + "WaitingForBoarding with broken transport flights:\n";
            result = result + "  found . . . . . . " + waitingForBoardingWithBrokenTransportFlights.size() + "\n";

            int toProcess = Math.min(waitingForBoardingWithBrokenTransportFlights.size(), 100);
            for (int i = 0; i < toProcess; i++) {
                final Journeys.Journey journey = waitingForBoardingWithBrokenTransportFlights.get(i);
                logInvalidJourneyChange(dryRun, journey, "delete", "WaitingForBoarding with broken transport flight");
                if (!dryRun) {
                    world.journeys().deleteById(journey.getId());
                }
            }

            result = result + deletedLabel + toProcess + "\n";
            result = result + "\n";



            List<Journeys.Journey> waitingForBoardingWithTransportFlightsInInappropriateStatus = world.journeys().allWithZeroHeartbeat()
                    .filter(j -> j.getStatus() == Journeys.Status.WaitingForBoarding)
                    .filter(j -> {
                        Optional<TransportFlights.Flight> flight = world.transportFlights().byId(j.getTransportFlight1Id());
                        boolean present = flight.isPresent();
                        if (!present) {
                            return false;
                        }
                        return !TransportFlightHelper.flightStatusAllowsToBoard(flight.get().getStatus())
                                && flight.get().getStatus() != TransportFlights.Status.WaitingForDeparture;
                    })
                    .toList();
            result = result + "WaitingForBoarding with transport flights in inappropriate status:\n";
            result = result + "  found . . . . . . " + waitingForBoardingWithTransportFlightsInInappropriateStatus.size() + "\n";

            // the breakdown by status of the transport flight helps to understand where such journeys come from
            final Map<TransportFlights.Status, Integer> inappropriateByTfStatus = new TreeMap<>();
            for (final Journeys.Journey journey : waitingForBoardingWithTransportFlightsInInappropriateStatus) {
                final TransportFlights.Status tfStatus = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow().getStatus();
                inappropriateByTfStatus.merge(tfStatus, 1, Integer::sum);
            }
            for (final Map.Entry<TransportFlights.Status, Integer> entry : inappropriateByTfStatus.entrySet()) {
                result = result + "  by t/f status " + entry.getKey() + " . . " + entry.getValue() + "\n";
            }

            toProcess = Math.min(waitingForBoardingWithTransportFlightsInInappropriateStatus.size(), 100);
            for (int i = 0; i < toProcess; i++) {
                final Journeys.Journey journey = waitingForBoardingWithTransportFlightsInInappropriateStatus.get(i);
                final TransportFlights.Status tfStatus = world.transportFlights().byId(journey.getTransportFlight1Id()).orElseThrow().getStatus();
                logInvalidJourneyChange(dryRun, journey, "delete", "WaitingForBoarding with transport flight in inappropriate status " + tfStatus);
                if (!dryRun) {
                    world.journeys().deleteById(journey.getId());
                }
            }

            result = result + deletedLabel + toProcess + "\n";
            result = result + "\n";



            List<Journeys.Journey> waitingForCheckinWithBrokenTransportFlights = world.journeys().allWithZeroHeartbeat()
                    .filter(j -> j.getStatus() == Journeys.Status.WaitingForCheckIn)
                    .filter(j -> world.transportFlights().byId(j.getTransportFlight1Id()).isEmpty())
                    .toList();
            result = result + "WaitingForCheckin with broken transport flights:\n";
            result = result + "  found . . . . . . " + waitingForCheckinWithBrokenTransportFlights.size() + "\n";

            toProcess = Math.min(waitingForCheckinWithBrokenTransportFlights.size(), 100);
            for (int i = 0; i < toProcess; i++) {
                final Journeys.Journey journey = waitingForCheckinWithBrokenTransportFlights.get(i);
                logInvalidJourneyChange(dryRun, journey, "delete", "WaitingForCheckin with broken transport flight");
                if (!dryRun) {
                    world.journeys().deleteById(journey.getId());
                }
            }

            result = result + deletedLabel + toProcess + "\n";
            result = result + "\n";



            List<Journeys.Journey> lookingForTicketsWithoutHeartbeat = world.journeys().allWithZeroHeartbeat()
                    .filter(j -> j.getStatus() == Journeys.Status.LookingForTickets)
                    .toList();
            result = result + "LookingForTickets without heartbeat:\n";
            result = result + "  found . . . . . . " + lookingForTicketsWithoutHeartbeat.size() + "\n";

            toProcess = Math.min(lookingForTicketsWithoutHeartbeat.size(), 100);
            for (int i = 0; i < toProcess; i++) {
                final Journeys.Journey journey = lookingForTicketsWithoutHeartbeat.get(i);
                logInvalidJourneyChange(dryRun, journey, "set heartbeat time to now", "LookingForTickets without heartbeat");
                if (!dryRun) {
                    journey.setHeartbeatTime(world.getWorldTime());
                }
            }

            result = result + updatedLabel + toProcess + "\n";
            result = result + "\n";



            // terminal statuses are cleaned up by heartbeat processing, so without heartbeat they would stay forever
            List<Journeys.Journey> terminalStatusWithoutHeartbeat = world.journeys().allWithZeroHeartbeat()
                    .filter(j -> j.getStatus() == Journeys.Status.Finished
                            || j.getStatus() == Journeys.Status.CouldNotFindTickets)
                    .toList();
            result = result + "Terminal statuses (Finished, CouldNotFindTickets) without heartbeat:\n";
            result = result + "  found . . . . . . " + terminalStatusWithoutHeartbeat.size() + "\n";

            toProcess = Math.min(terminalStatusWithoutHeartbeat.size(), 100);
            for (int i = 0; i < toProcess; i++) {
                final Journeys.Journey journey = terminalStatusWithoutHeartbeat.get(i);
                logInvalidJourneyChange(dryRun, journey, "delete", "terminal status without heartbeat");
                if (!dryRun) {
                    world.journeys().deleteById(journey.getId());
                }
            }

            result = result + deletedLabel + toProcess + "\n";
            result = result + "\n";



            // TooLateToBoard is not terminal, it gets back to looking for tickets by heartbeat processing
            List<Journeys.Journey> tooLateToBoardWithoutHeartbeat = world.journeys().allWithZeroHeartbeat()
                    .filter(j -> j.getStatus() == Journeys.Status.TooLateToBoard)
                    .toList();
            result = result + "TooLateToBoard without heartbeat:\n";
            result = result + "  found . . . . . . " + tooLateToBoardWithoutHeartbeat.size() + "\n";

            toProcess = Math.min(tooLateToBoardWithoutHeartbeat.size(), 100);
            for (int i = 0; i < toProcess; i++) {
                final Journeys.Journey journey = tooLateToBoardWithoutHeartbeat.get(i);
                logInvalidJourneyChange(dryRun, journey, "set heartbeat time to now", "TooLateToBoard without heartbeat");
                if (!dryRun) {
                    journey.setHeartbeatTime(world.getWorldTime());
                }
            }

            result = result + updatedLabel + toProcess + "\n";
            result = result + "\n";

            // such journeys are not able to find tickets, they are located nowhere
            List<Journeys.Journey> lookingForTicketsWithZeroLocation = world.journeys()
                    .filter(world.journeys().byStatus(Journeys.Status.LookingForTickets))
                    .filter(j -> j.getLocationCityId() == 0)
                    .toList();
            result = result + "LookingForTickets with zero location city:\n";
            result = result + "  found . . . . . . " + lookingForTicketsWithZeroLocation.size() + "\n";

            toProcess = Math.min(lookingForTicketsWithZeroLocation.size(), 100);
            for (int i = 0; i < toProcess; i++) {
                final Journeys.Journey journey = lookingForTicketsWithZeroLocation.get(i);
                logInvalidJourneyChange(dryRun, journey, "delete", "LookingForTickets with zero location city");
                if (!dryRun) {
                    world.journeys().deleteById(journey.getId());
                }
            }

            result = result + deletedLabel + toProcess + "\n";
            result = result + "\n";



            return result;
        });
    }

    /**
     * Journeys in WaitingForDeboarding without heartbeat are leftovers of the old deboarding scheme: nothing wakes them up anymore.
     * New scheme always sets the heartbeat when the journey goes to WaitingForDeboarding, so such journeys are stuck forever.
     * <p>
     * mode=deboard (default): the heartbeat is set to a random moment within the next 30 minutes and JourneyProcessor does the rest
     * (the finished flight - deboards, the lost flight - forceful return, an unexpected status - orphaned), the passengers are kept.
     * mode=delete: the journeys are deleted.
     */
    @GetMapping(value = "/journey/stuck-waiting-for-deboarding", produces = "text/plain")
    public String stuckWaitingForDeboarding(@RequestParam(name = "dryRun", defaultValue = "true") final boolean dryRun,
                                            @RequestParam(name = "mode", defaultValue = "deboard") final String mode,
                                            @RequestParam(name = "limit", defaultValue = "500") final int limit) {
        checkArgument(mode.equals("deboard") || mode.equals("delete"), "mode must be deboard or delete");
        return worldBean.modifySync(world -> {
            final List<Journeys.Journey> stuck = world.journeys().allWithZeroHeartbeat()
                    .filter(j -> j.getStatus() == Journeys.Status.WaitingForDeboarding)
                    .toList();

            final Map<String, Integer> byTfStatus = new TreeMap<>();
            final Map<String, Integer> paxByTfStatus = new TreeMap<>();
            for (final Journeys.Journey journey : stuck) {
                final String tfStatus = world.transportFlights().byId(journey.getTransportFlight1Id())
                        .map(tf -> tf.getStatus().name())
                        .orElse("<t/f not found>");
                byTfStatus.merge(tfStatus, 1, Integer::sum);
                paxByTfStatus.merge(tfStatus, journey.getGroupSize(), Integer::sum);
            }

            final StringBuilder result = new StringBuilder();
            if (dryRun) {
                result.append("DRY RUN - nothing is changed\n\n");
            }
            result.append("WaitingForDeboarding without heartbeat, mode ").append(mode).append(":\n");
            result.append("  found . . . . . . ").append(stuck.size()).append("\n");
            byTfStatus.forEach((status, count) -> result.append("  by t/f status ").append(status)
                    .append(" . . ").append(count).append(" j/y, ").append(paxByTfStatus.get(status)).append(" pax\n"));

            final int toProcess = Math.min(stuck.size(), limit);
            for (int i = 0; i < toProcess; i++) {
                final Journeys.Journey journey = stuck.get(i);
                if (mode.equals("delete")) {
                    logInvalidJourneyChange(dryRun, journey, "delete", "WaitingForDeboarding without heartbeat");
                    if (!dryRun) {
                        world.journeys().deleteById(journey.getId());
                    }
                } else {
                    logInvalidJourneyChange(dryRun, journey, "set heartbeat time to soon", "WaitingForDeboarding without heartbeat");
                    if (!dryRun) {
                        journey.setHeartbeatTime(world.getWorldTime() + 1 + (int) (Math.random() * 30 * Time.ONE_MINUTE));
                    }
                }
            }
            result.append(dryRun ? "  would be processed " : "  processed . . . . ").append(toProcess)
                    .append(" (limit ").append(limit).append(")\n");
            return result.toString();
        });
    }

    private void logInvalidJourneyChange(final boolean dryRun, final Journeys.Journey journey, final String action, final String reason) {
        log.info("delete-invalid{} - j/y #{} - {} - {}, status {}, from city #{}, to city #{}, location city #{}, t/f1 #{}, t/f2 #{}, heartbeat {}",
                dryRun ? " (dry run)" : "",
                journey.getId(),
                action,
                reason,
                journey.getStatus(),
                journey.getFromCityId(),
                journey.getToCityId(),
                journey.getLocationCityId(),
                journey.getTransportFlight1Id(),
                journey.getTransportFlight2Id(),
                Time.toLdtOrNull(journey.getHeartbeatTime()));
    }

    @GetMapping("/fix-129")
    public void fix129() {
        worldBean.modifySync(world -> {
            final int journeyId = 129;

            final Journeys.Journey journey = world.journeys().byId(journeyId).orElseThrow();

            journey.setHeartbeatTime(world.getWorldTime() + 5 * Time.ONE_MINUTE);

            return null;
        });
    }

    @GetMapping("/fix-981")
    public void fix981() {
        worldBean.modifySync(world -> {
            final int journeyId = 981;

            final Journeys.Journey journey = world.journeys().byId(journeyId).orElseThrow();

            journey.setTransportFlight1Id(114);

            return null;
        });
    }

    @GetMapping("/fix-it")
    public String fixIt() {
        return worldBean.modifySync(world -> {
            int journeyId = 9179;

            Journeys.Journey journey = world.journeys().byId(journeyId).orElseThrow();
            journey.setFromCityId(49);

            return "Done";
        });
    }

    @GetMapping("/fix-17783")
    public String fix17783() {
        return worldBean.modifySync(world -> {
            final int fmId = 17783;

            FlightMissions.Mission fm = world.flightMissions().byId(fmId).orElseThrow();
            fm.setDestinationAirportId(world.airports().byIcao("KLGA").orElseThrow().getId());

            return "Done";
        });
    }

    @GetMapping("/flows/reset-city-redistribution")
    public void resetCityRedistribution() {
        worldBean.modifySync(world -> {
            world.cityFlows().all().forEach(cf -> cf.setLastRedistributionTime(0));

            return null;
        });
    }

    /**
     * The histograms of the flows of cities as they are now, the same two lines which the log gets once an hour: by the heartbeat (c2c h/b) and by the
     * accumulated flow time (c2c a/t).
     */
    @GetMapping(value = "/flows/c2c/histograms", produces = "text/plain")
    public String getC2CFlowHistograms() {
        return worldBean.read(world -> City2CityFlowHistogramAnalyser.histogramLine(world) + "\n" + City2CityFlowHistogramAnalyser.clockLine(world) + "\n");
    }

    /**
     * The active flows with the oldest clock (a/t), the oldest first (limit lines, 20 by default, 200 at most): what they are and why the clock does not move.
     */
    @GetMapping(value = "/flows/c2c/oldest", produces = "text/plain")
    public String getC2COldestFlows(@RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
        return worldBean.read(world -> {
            final int shown = Math.min(limit, 200);
            final List<String> lines = new ArrayList<>(City2CityFlowHistogramAnalyser.oldestByClock(world, shown));
            lines.add(City2CityFlowHistogramAnalyser.totalNeedPax(world, shown));
            return String.join(System.lineSeparator(), lines) + System.lineSeparator();
        });
    }

    /**
     * Takes the oldest (by a/t) flows whose clock is in the past and whose time to accumulate the next group is longer than 100 days (limit of them,
     * 20 by default, 200 at most), sets the next group size of them to 1 and the heartbeat to the world time: the flow takes the small group at its next
     * pass, which is soon. Nothing else is changed.
     */
    @GetMapping(value = "/flows/c2c/slow-reset", produces = "text/plain")
    public String resetSlowC2CFlows(@RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
        return worldBean.modifySync(world -> {
            final int worldTime = world.getWorldTime();
            final List<City2CityFlows.Flow> flows = City2CityFlowHistogramAnalyser.lateWithLongAccumulation(world, Math.min(limit, 200), 100 * Time.ONE_DAY);
            final List<String> lines = new ArrayList<>();
            for (final City2CityFlows.Flow c2c : flows) {
                final int was = c2c.getNextGroupSize();
                final int daysToAccumulate = CityFlowHelper.calcTimeToAccumulateFlow(world, c2c) / Time.ONE_DAY;
                c2c.setNextGroupSize(1);
                c2c.setHeartbeatTime(worldTime);
                log.info("c2c #{}/{} - slow-reset, next g/s {} -> 1 ({} d to accumulate), h/b set to {}", c2c.getFromCityId(), c2c.getToCityId(), was, daysToAccumulate, Time.toLdt(worldTime));
                lines.add("#" + c2c.getFromCityId() + "/" + c2c.getToCityId() + " g/s " + was + " -> 1, was " + daysToAccumulate + " d to accumulate");
            }
            lines.add("Done, " + flows.size() + " flows");
            return String.join(System.lineSeparator(), lines) + System.lineSeparator();
        });
    }

    @GetMapping(value = "/flows/c2c/heartbeat", produces = "text/plain")
    public String updateC2CFlowHeartbeat(@RequestParam("from") int fromCityId, @RequestParam("to") int toCityId) {
        return worldBean.modifySync(world -> {
            City2CityFlows.Flow c2c = world.city2cityFlows().getFromCityIdToCityId(fromCityId, toCityId).orElseThrow();
            c2c.setHeartbeatTime(world.getWorldTime());
            return "Done";
        });
    }

    /**
     * Sets the size of the next group of the flow as it is given (1 to 255, the range of the field), nothing else is changed: the flow takes
     * the size at its next pass, the time to accumulate it is calculated from the size.
     */
    @GetMapping(value = "/flows/c2c/group-size", produces = "text/plain")
    public String updateC2CFlowGroupSize(@RequestParam("from") int fromCityId, @RequestParam("to") int toCityId, @RequestParam("size") int size) {
        if (size < 1 || size > 255) {
            throw new IllegalArgumentException("size must be 1 to 255: " + size);
        }
        return worldBean.modifySync(world -> {
            City2CityFlows.Flow c2c = world.city2cityFlows().getFromCityIdToCityId(fromCityId, toCityId).orElseThrow();
            final int was = c2c.getNextGroupSize();
            c2c.setNextGroupSize(size);
            log.info("c2c #{}/{} - next g/s set by admin, was {}, now {}", fromCityId, toCityId, was, size);
            return "Done, next g/s was " + was + ", now " + size;
        });
    }

    /**
     * Resets the heartbeat of every active flow to the world time: all of them are looked at in turn (10 per world step) and get their own heartbeat
     * (a/t + the time to accumulate, 9 to 11 minutes from now at least, 90 days from now at most).
     */
    @GetMapping(value = "/flows/c2c/heartbeat-reset-all", produces = "text/plain")
    public String resetAllC2CFlowHeartbeats() {
        return worldBean.modifySync(world -> {
            final int worldTime = world.getWorldTime();
            final int[] count = new int[1];

            world.city2cityFlows().all()
                    .filter(City2CityFlows.Flow::isActive)
                    .forEach(c2c -> {
                        c2c.setHeartbeatTime(worldTime);
                        count[0]++;
                    });

            log.info("c2c heartbeat-reset-all - h/b of {} active flows set to {}", count[0], Time.toLdt(worldTime));
            return "Done, " + count[0] + " active flows";
        });
    }

    @GetMapping(value = "/flows/c2c/heartbeat-in-future", produces = "text/plain")
    public String updateC2CFlowHeartbeat() {
        return worldBean.modifySync(world -> {
            int worldTime = world.getWorldTime();
            int threshold = worldTime + 10 * Time.ONE_DAY;

            world.city2cityFlows().all()
                    .filter(City2CityFlows.Flow::isActive)
                    .filter(c2c -> c2c.getHeartbeatTime() > threshold)
                    .forEach(c2c -> c2c.setHeartbeatTime(worldTime));

            return "Done";
        });
    }

    @GetMapping(value = "/flows/details", produces = "text/plain")
    public String getFlowInfo(@RequestParam("id") String idStr) {
        return worldBean.read(world -> {
            List<String> results = new ArrayList<>();

            int cityId = Id.decode(idStr);
            Cities.City city = world.cities().byId(cityId).orElseThrow();
            CityFlows.Flow flow = world.cityFlows().byCityId(city.getId()).orElseThrow();

            results.add("City\t\t" + city.getName());
            results.add("Attraction\t" + flow.getAttractionFactor());
            results.add("Mobility\t" + flow.getMobilityFactor());
            results.add("Redist time\t" + Time.toLdtOrNull(flow.getLastRedistributionTime()));
            results.add("Daily flow\t" + CityFlowHelper.getDailyFlow(world, flow));
            results.add("");

            world.city2cityFlows().allFromCityId(cityId).forEach(c2c -> {
                results.add(String.format("To city %s [%s]        Active %s    H/b %s    Next g/s %s    Time to acc %s    Curr acc flow / time %s / %s    S.rate/Flow %s / %s (max %s)",
                        c2c.getToCityId(), world.cities().byId(c2c.getToCityId()).orElseThrow().getName(),
                        c2c.isActive(),
                        Time.toLdtOrNull(c2c.getHeartbeatTime()),
                        c2c.getNextGroupSize(),
                        Time.toLdt(c2c.getAccumulatedFlowTime() + CityFlowHelper.calcTimeToAccumulateFlow(world, c2c)),
                        c2c.getAccumulatedFlow(),
                        Time.toLdtOrNull(c2c.getAccumulatedFlowTime()),
                        Formatting.df6.format(c2c.getSuccessRate()),
                        (int)(c2c.getFlowFraction() * c2c.getSuccessRate() * CityFlowHelper.getDailyFlow(world, flow)),
                        (int)(c2c.getFlowFraction() * 1 * CityFlowHelper.getDailyFlow(world, flow))));
            });

            return Strings.join(results, '\n');
        });
    }

    @GetMapping(value = "/flows/set-attraction", produces = "text/plain")
    public String setFlowAttraction(@RequestParam("id") String idStr, @RequestParam("attraction") float attraction) {
        worldBean.modifySync(world -> {
            int cityId = Id.decode(idStr);
            Cities.City city = world.cities().byId(cityId).orElseThrow();
            CityFlows.Flow flow = world.cityFlows().byCityId(city.getId()).orElseThrow();
            flow.setAttractionFactor(attraction);
            flow.setLastRedistributionTime(0);
            return "Done";
        });
        return getFlowInfo(idStr);
    }

    @GetMapping(value = "/flows/set-mobility", produces = "text/plain")
    public String setFlowMobility(@RequestParam("id") String idStr, @RequestParam("mobility") float mobility) {
        worldBean.modifySync(world -> {
            int cityId = Id.decode(idStr);
            Cities.City city = world.cities().byId(cityId).orElseThrow();
            CityFlows.Flow flow = world.cityFlows().byCityId(city.getId()).orElseThrow();
            flow.setMobilityFactor(mobility);
            flow.setLastRedistributionTime(0);
            return "Done";
        });
        return getFlowInfo(idStr);
    }

    @GetMapping("/sqids/generate-alphabet")
    public String generateSqidsAlphabet() {
        String alphabet = Id.generateRandomAlphabet();
        log.info("generated alphabet = {}", alphabet);
        return alphabet;
    }

    @GetMapping("/sqids/decode")
    public String decodeSqidsId(@RequestParam("id") String id) {
        return worldBean.modifySync(world -> {
            List<String> results = new ArrayList<>();

            int intId = Id.decode(id);
            results.add(id + "\t = " + intId);

            return Strings.join(results, '\n');
        });
    }
}
