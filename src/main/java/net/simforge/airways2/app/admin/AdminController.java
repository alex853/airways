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
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.world.processors.AircraftHelper;
import net.simforge.airways2.worldbuilder.tools.ImportCities;
import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import net.simforge.commons.misc.Geo;
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
import java.util.stream.Stream;

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
        checkNotNull(LocalDate.parse(date));

        return loadFlightStats(date);
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

    @SuppressWarnings("unused")
    @GetMapping(value = "/flight/reschedule", produces = "text/plain")
    public String rescheduleFlight(@RequestParam(name = "id") int fmId,
                                   @RequestParam(name = "dof") String newDOF,
                                   @RequestParam(name = "depTime") String newDepTime) {
        /*
         * this can be done only in fm dispatched state
         * is new time in past or too close to now?
         * fmId -> userId -> list of user's assignments -> is there any overlapping between user's flights?
         * fmId -> aircraft -> aircraft's assignments -> is there any overlapping between aircraft's flights?
         *                            is this order of flights doable in terms of aircraft location?
         * update transport flight heartbeat, update journeys heartbeat
         */
        return null; // todo ak0 00000000 implement!
    }

    @GetMapping(value = "/flight/cancel", produces = "text/plain")
    public String cancelFlight(@RequestParam(name = "id") String fmIdStr) {
        return worldBean.modifySync(world -> {
            int fmId = Id.decode(fmIdStr);

            List<String> results = new ArrayList<>();

            FlightMissions.Mission fm = world.flightMissions().byId(fmId).orElseThrow();
            fm.setStatus(FlightMissions.Status.Cancelled);
            results.add("F/M #" + fmId + " cancelled");

            // todo ak1 cancelling a flight while the flight is not active or is not flying should not update an aircraft as this will affect another flight if there is any one is in progress
            Aircrafts.Aircraft aircraft = releaseAndParkAircraft(world, fm);
            results.add("A/C #" + aircraft.getId() + ", " + aircraft.getRegNo() + " is parked in " + world.airports().getIcao(aircraft.getLocationAirportId()).orElseThrow());

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
            final Aircrafts.Aircraft aircraft = releaseAndParkAircraft(world, mission);
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

    private static Aircrafts.Aircraft releaseAndParkAircraft(final World world, final FlightMissions.Mission mission) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        //noinspection IfStatementWithIdenticalBranches
        if (aircraft.getLocationStatus() != Aircrafts.LocationStatus.Flying) {
            aircraft.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);

            aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Idle);
            aircraft.setFlightMissionId(0);
        } else {
            //noinspection DuplicatedCode
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

    @GetMapping("/aircraft/reset-status")
    public String resetAircraftStatus(@RequestParam(name = "aircraftId") final int aircraftId) {
        return worldBean.modifySync(world -> {
            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(aircraftId).orElseThrow();

            aircraft.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);
            aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Idle);
            aircraft.setFlightMissionId(0);

            return "A/C #" + aircraft.getId() + " is parked in airport #" + aircraft.getLocationAirportId();
        });
    }

    @GetMapping("/aircraft/move-to-airport")
    public String moveAircraftToAirport(@RequestParam(name = "aircraftId") final int aircraftId, @RequestParam(name = "airportId") final int airportId) {
        return worldBean.modifySync(world -> {
            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(aircraftId).orElseThrow();
            final Airports.Airport airport = world.airports().byId(airportId).orElseThrow();

            AircraftHelper.moveParkedAircraftToAnotherAirport(world, aircraft, airport);

            return "A/C #" + aircraft.getId() + " is parked in airport #" + aircraft.getLocationAirportId();
        });
    }

    @GetMapping("/aircraft/frozen-lsit")
    public String resetAircraftStatus() {
        return worldBean.modifySync(world -> {
            List<String> results = new ArrayList<>();

            List<Aircrafts.Aircraft> aircrafts = world.aircrafts().all()
                    .filter(a -> a.getLocationStatus() == Aircrafts.LocationStatus.ParkedAtAirport
                            && a.getOperationalStatus() == Aircrafts.OperationalStatus.Active
                            && a.getLocationAirportId() > 0)
                    .toList();

            aircrafts.forEach(a -> {
                results.add(a.getId() + "\t" +
                        a.getRegNo() + "\t" +
                        a.getFlightMissionId() + "\t" +
                        (a.getFlightMissionId() > 0 && vatsimTracker.getContextByFlightMissionId(a.getFlightMissionId()).isPresent()));
            });

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
    public String deleteInvalidJourneys() {
        return worldBean.modifySync(world -> {
            String result = "";



            List<Journeys.Journey> waitingForBoardingWithBrokenTransportFlights = world.journeys().allWithZeroHeartbeat()
                    .filter(j -> j.getStatus() == Journeys.Status.WaitingForBoarding)
                    .filter(j -> world.transportFlights().byId(j.getTransportFlight1Id()).isEmpty())
                    .toList();
            result = result + "WaitingForBoarding with broken transport flights:\n";
            result = result + "  found . . . . . . " + waitingForBoardingWithBrokenTransportFlights.size() + "\n";

            int toProcess = Math.min(waitingForBoardingWithBrokenTransportFlights.size(), 100);
            for (int i = 0; i < toProcess; i++) {
                world.journeys().deleteById(waitingForBoardingWithBrokenTransportFlights.get(i).getId());
            }

            result = result + "  deleted . . . . . " + toProcess + "\n";
            result = result + "\n";



            List<Journeys.Journey> waitingForBoardingWithTransportFlightsInInappropriateStatus = world.journeys().allWithZeroHeartbeat()
                    .filter(j -> j.getStatus() == Journeys.Status.WaitingForBoarding)
                    .filter(j -> {
                        Optional<TransportFlights.Flight> flight = world.transportFlights().byId(j.getTransportFlight1Id());
                        boolean present = flight.isPresent();
                        if (!present) {
                            return false;
                        }
                        return flight.get().getStatus() != TransportFlights.Status.CheckIn
                                && flight.get().getStatus() != TransportFlights.Status.Boarding
                                && flight.get().getStatus() != TransportFlights.Status.WaitingForDeparture;
                    })
                    .toList();
            result = result + "WaitingForBoarding with transport flights in inappropriate status:\n";
            result = result + "  found . . . . . . " + waitingForBoardingWithTransportFlightsInInappropriateStatus.size() + "\n";

            toProcess = Math.min(waitingForBoardingWithTransportFlightsInInappropriateStatus.size(), 100);
            for (int i = 0; i < toProcess; i++) {
                world.journeys().deleteById(waitingForBoardingWithTransportFlightsInInappropriateStatus.get(i).getId());
            }

            result = result + "  deleted . . . . . " + toProcess + "\n";
            result = result + "\n";



            List<Journeys.Journey> waitingForCheckinWithBrokenTransportFlights = world.journeys().allWithZeroHeartbeat()
                    .filter(j -> j.getStatus() == Journeys.Status.WaitingForCheckIn)
                    .filter(j -> world.transportFlights().byId(j.getTransportFlight1Id()).isEmpty())
                    .toList();
            result = result + "WaitingForCheckin with broken transport flights:\n";
            result = result + "  found . . . . . . " + waitingForCheckinWithBrokenTransportFlights.size() + "\n";

            toProcess = Math.min(waitingForCheckinWithBrokenTransportFlights.size(), 100);
            for (int i = 0; i < toProcess; i++) {
                world.journeys().deleteById(waitingForCheckinWithBrokenTransportFlights.get(i).getId());
            }

            result = result + "  deleted . . . . . " + toProcess + "\n";
            result = result + "\n";



            List<Journeys.Journey> lookingForTicketsWithoutHeartbeat = world.journeys().allWithZeroHeartbeat()
                    .filter(j -> !j.isBusyBirdsProcessing())
                    .filter(j -> j.getStatus() == Journeys.Status.LookingForTickets)
                    .toList();
            result = result + "LookingForTickets without heartbeat:\n";
            result = result + "  found . . . . . . " + lookingForTicketsWithoutHeartbeat.size() + "\n";

            toProcess = Math.min(lookingForTicketsWithoutHeartbeat.size(), 100);
            for (int i = 0; i < toProcess; i++) {
                lookingForTicketsWithoutHeartbeat.get(i).setHeartbeatTime(world.getWorldTime());
            }

            result = result + "  updated . . . . . " + toProcess + "\n";
            result = result + "\n";


            // todo ak1 check terminal states without heartbeat

            return result;
        });
    }

    @GetMapping("/journey/set-busy-birds-processing")
    public String turnToSpecialProcessing(@RequestParam("jId") final int jId) {
        return worldBean.modifySync(world -> {
            world.journeys().byId(jId).orElseThrow().setBusyBirdsProcessing(true);
            return "DONE";
        });
    }

    @GetMapping("/journey/reset-busy-birds-processing")
    public String turnToSpecialProcessing() {
        return worldBean.modifySync(world -> {
            world.journeys().filter(world.journeys().byBusyBirdsProcessing()).forEach(j -> j.setBusyBirdsProcessing(false));
            return "DONE";
        });
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
            int journeyId = 4399;

            Journeys.Journey journey = world.journeys().byId(journeyId).orElseThrow();
            journey.setBookedCabinService(CabinLayout.Service.J);

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

    @GetMapping(value = "/flows/c2c/print", produces = "text/plain")
    public String printC2CFlowStatus(@RequestParam("from") int fromCityId, @RequestParam("to") int toCityId) {
        return worldBean.read(world -> {
            City2CityFlows.Flow c2c = world.city2cityFlows().getFromCityIdToCityId(fromCityId, toCityId).orElseThrow();
            return String.format("C2C Flow #%s/%s [%s -> %s]\n\nHeartbeat %s\nNext group size %s\nAccumulated flow %s\nAccumulated flow time %s",
                    fromCityId, toCityId, world.cities().byId(fromCityId).orElseThrow().getName(), world.cities().byId(toCityId).orElseThrow().getName(),
                    Time.toLdtOrNull(c2c.getHeartbeatTime()),
                    c2c.getNextGroupSize(),
                    c2c.getAccumulatedFlow(),
                    Time.toLdtOrNull(c2c.getAccumulatedFlowTime()));
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

    @GetMapping(value = "/flows/details", produces = "text/plain")
    public String getFlowInfo(@RequestParam("id") String idStr) {
        return worldBean.read(world -> {
            List<String> results = new ArrayList<>();

            int cityId = Id.decode(idStr);
            Cities.City city = world.cities().byId(cityId).orElseThrow();
            CityFlows.Flow flow = world.cityFlows().all().filter(f -> f.getId() == city.getId()).findFirst().orElseThrow();

            results.add("City\t\t" + city.getName());
            results.add("Attraction\t" + flow.getAttractionFactor());
            results.add("Mobility\t" + flow.getMobilityFactor());
            results.add("Redist time\t" + Time.toLdtOrNull(flow.getLastRedistributionTime()));

            return Strings.join(results, '\n');
        });
    }

    @GetMapping(value = "/flows/set-attraction", produces = "text/plain")
    public String setFlowAttraction(@RequestParam("id") String idStr, @RequestParam("attraction") float attraction) {
        worldBean.modifySync(world -> {
            int cityId = Id.decode(idStr);
            Cities.City city = world.cities().byId(cityId).orElseThrow();
            CityFlows.Flow flow = world.cityFlows().all().filter(f -> f.getId() == city.getId()).findFirst().orElseThrow();
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
            CityFlows.Flow flow = world.cityFlows().all().filter(f -> f.getId() == city.getId()).findFirst().orElseThrow();
            flow.setMobilityFactor(mobility);
            flow.setLastRedistributionTime(0);
            return "Done";
        });
        return getFlowInfo(idStr);
    }

    @GetMapping("/f1-tour-fixes")
    public void f1TourFixes() {
        worldBean.modifySync(world -> {
            final Cities.City singapore = world.cities().all().filter(c -> c.getName().equalsIgnoreCase("Singapore")).findFirst().orElseThrow();
            singapore.setPopulation(6037000);

            final Cities.City sanFrancisco = world.cities().all().filter(c -> c.getName().equalsIgnoreCase("San Francisco")).findFirst().orElseThrow();
            sanFrancisco.setName("San Francisco");
            sanFrancisco.setPopulation(7650000);

            world.aircrafts().byRegNo("F-AUWV").orElseThrow().setAircraftOperatorId(0);
            world.aircrafts().byRegNo("F-AUWW").orElseThrow().setAircraftOperatorId(0);
            world.aircrafts().byRegNo("F-AUWX").orElseThrow().setAircraftOperatorId(0);
            world.aircrafts().byRegNo("F-AUWY").orElseThrow().setAircraftOperatorId(0);
            world.aircrafts().byRegNo("F-AUWZ").orElseThrow().setAircraftOperatorId(0);

            return null;
        });
    }

    @GetMapping(value = "/airport/missing-cities", produces = "text/plain")
    public String printAirportsWithMissingCities() throws IOException {
        Csv citiesCsv = ImportCities.loadCityPopulationCsv();

        return worldBean.read(world -> {
            List<String> results = new ArrayList<>();

            world.airports().all().forEach(airport -> {
                List<Airport2City.Link> links = world.airport2city().allByAirportId(airport.getId()).toList();
                if (links.isEmpty()) {
                    results.add(Str.al(airport.getIcao(), 10) + "No any city linked");
                }

                List<ImportCities.CityInfo> cities = ImportCities.getCitiesNearAirport(citiesCsv, airport.getCoords(), 50);
                List<ImportCities.CityInfo> bigCities = cities.stream().filter(c -> c.getPopulation() >= 1000000).toList();

                bigCities.forEach(bigCity -> {
                    Optional<Cities.City> found = world.cities().all().filter(c -> c.getName().equals(bigCity.getName())).findFirst();
                    if (found.isEmpty()) {
                        results.add(Str.al(airport.getIcao(), 10) + "MISSING BIG CITY     " + bigCity.getName() + " (population " + bigCity.getPopulation() + ")");
                    }
                });
            });

            return Strings.join(results, '\n');
        });
    }

    @GetMapping(value = "/airport/create-links", produces = "text/plain")
    public String createAirport2CityLinks(@RequestParam("icao") String icao,
                                          @RequestParam(name = "maxDistance", defaultValue = "50") int maxDistance,
                                          @RequestParam(name = "dryRun", defaultValue = "false") boolean dryRun) {
        return worldBean.modifySync(world -> {
            List<String> results = new ArrayList<>();

            final Airports.Airport airport = world.airports().byIcao(icao).orElseThrow();
            world.cities().all()
                    .filter(c -> Geo.distance(c.getCoords(), airport.getCoords()) < maxDistance)
                    .forEach(c -> {
                        Optional<Airport2City.Link> link = world.airport2city().byAirportIdAndCityId(airport.getId(), c.getId());
                        if (link.isPresent()) {
                            results.add(Str.al(c.getName(), 20) + " exists");
                            return;
                        }

                        if (!dryRun) {
                            world.airport2city().create(airport.getId(), c.getId());
                            results.add(Str.al(c.getName(), 20) + " created");
                        } else {
                            results.add(Str.al(c.getName(), 20) + " should be created");
                        }
                    });

            return Strings.join(results, '\n');
        });
    }

    @GetMapping(value = "/airport/create-business-aviation-terminal", produces = "text/plain")
    public String createAirportBusinessAviationTerminal(@RequestParam("icao") String icao) {
        return worldBean.modifySync(world -> {
            List<String> results = new ArrayList<>();

            final Airports.Airport airport = world.airports().byIcao(icao).orElseThrow();
            boolean exists = world.airportFacilities().hasFacility(airport, AirportFacilities.Type.BusinessAviationTerminal);
            if (!exists) {
                world.airportFacilities().createIfAbsent(airport, AirportFacilities.Type.BusinessAviationTerminal);
                results.add("Created");
            } else {
                results.add("Exists");
            }

            return Strings.join(results, '\n');
        });
    }

    @GetMapping("/sqids/generate-alphabet")
    public String generateSqidsAlphabet() {
        return Id.generateRandomAlphabet();
    }

    @GetMapping("/sqids/decode")
    public String decodeSqidsId(@RequestParam("is") String id) {
        return worldBean.modifySync(world -> {
            List<String> results = new ArrayList<>();

            int intId = Id.decode(id);
            results.add(id + "\t = " + intId);

            return Strings.join(results, '\n');
        });
    }
}
