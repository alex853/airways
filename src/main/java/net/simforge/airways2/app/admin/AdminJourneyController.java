package net.simforge.airways2.app.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import net.simforge.airways2.tools.CabinLayout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/journey")
@CrossOrigin
public class AdminJourneyController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public List<JourneyDto> getAll(
            @RequestParam(name = "offset", required = false) final Integer offset,
            @RequestParam(name = "limit", required = false) final Integer limit) {
        return worldBean.read(world -> world.journeys().all()
                .skip(offset != null ? offset : 0)
                .limit(limit != null ? limit : Long.MAX_VALUE)
                .map(j -> toDto(world, j))
                .toList());
    }

    @GetMapping("/kick-all-looking-for-tickets")
    public String kickAllLookingForTickets() {
        return worldBean.modifySync(world -> {
            world.journeys()
                    .filter(world.journeys().byStatus(Journeys.Status.LookingForTickets))
                    .forEach(j -> j.setHeartbeatTime(world.getWorldTime()));
            return "DONE";
        });
    }

    @GetMapping("/set-heartbeat-to-now")
    public String setHeartbeatToNow(@RequestParam(name = "jId") final int journeyId) {
        return worldBean.modifySync(world -> {
            world.journeys().byId(journeyId).orElseThrow().setHeartbeatTime(world.getWorldTime());
            return "DONE";
        });
    }

    @GetMapping("/remove")
    public String journeyFlight(@RequestParam(name = "journeyId") final int journeyId) {
        return worldBean.modifySync(world -> {
            world.journeys().deleteById(journeyId);
            return "J/Y #" + journeyId + " removed";
        });
    }

    @GetMapping("/delete-all-without-heartbeat")
    public String deleteAllJourneysWithoutHeartbeat() {
        return worldBean.modifySync(world -> {
            world.journeys().allWithZeroHeartbeat().forEach(j -> world.journeys().deleteById(j.getId()));
            return "DONE";
        });
    }

    /**
     * The flag of the special group on a journey by its id (an existing journey, the immortal one), for example POST /admin/journey/special-group/flag?journeyId=1&value=true.
     * value=false clears the flag (the journey is an ordinary one again). The size and the class are set by special-group/params.
     */
    @PostMapping("/special-group/flag")
    public String setSpecialGroupFlag(
            @RequestParam(name = "journeyId") final int journeyId,
            @RequestParam(name = "value", defaultValue = "true") final boolean value) {
        return worldBean.modifySync(world -> {
            final Journeys.Journey journey = world.journeys().byId(journeyId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no journey #" + journeyId));
            world.journeyControl().setSpecialGroup(journey, value);
            return "J/Y #" + journeyId + " special group: " + value;
        });
    }

    /**
     * The size and the class of a special group (a journey with the flag set by special-group/flag), for example
     * POST /admin/journey/special-group/params?journeyId=1&groupSize=6&service=F. Only while the group holds no seats (it looks for tickets or spends its time in a city).
     */
    @PostMapping("/special-group/params")
    public String setSpecialGroupParams(
            @RequestParam(name = "journeyId") final int journeyId,
            @RequestParam(name = "groupSize") final int groupSize,
            @RequestParam(name = "service") final String service) {
        final CabinLayout.Service parsedService;
        try {
            parsedService = CabinLayout.Service.valueOf(service.trim().toUpperCase());
        } catch (final IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The class is Y, W, J or F");
        }
        return worldBean.modifySync(world -> {
            final Journeys.Journey journey = world.journeys().byId(journeyId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no journey #" + journeyId));
            try {
                world.journeyControl().setSpecialGroupParams(journey, groupSize, parsedService);
            } catch (final IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            } catch (final IllegalStateException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
            }
            return "J/Y #" + journeyId + " special group: " + groupSize + " x " + parsedService;
        });
    }

    @GetMapping("/stats")
    public Map<String, Integer> getStats() {
        return worldBean.read(world -> world.journeys().all()
                .collect(Collectors.groupingBy(
                    j -> j.getStatus().name(),
                    Collectors.summingInt(Journeys.Journey::getGroupSize))));
    }

    private static String flags(final Journeys.Journey j) {
        final String flags = (j.isPilotJourney() ? "P" : "") + (j.isSpecialGroup() ? "S" : "");
        return flags.isEmpty() ? "n" : flags;
    }

    private static JourneyDto toDto(final World world, final Journeys.Journey j) {
        final Optional<TransportFlights.Flight> tf1 = world.transportFlights().byId(j.getTransportFlight1Id());
        final Optional<FlightMissions.Mission> fm1 = tf1.flatMap(f -> world.flightMissions().byId(f.getFlightMissionId()));

        final Optional<TransportFlights.Flight> tf2 = world.transportFlights().byId(j.getTransportFlight2Id());
        final Optional<FlightMissions.Mission> fm2 = tf2.flatMap(f -> world.flightMissions().byId(f.getFlightMissionId()));

        return new JourneyDto(
                j.getId(),
                j.getStatus().name(),
                TimeTools.ts(j.getHeartbeatTime()),
                j.getLocationCityId(),
                j.getLocationCityId() > 0 ? world.cities().byId(j.getLocationCityId()).orElseThrow().getName() : null,
                j.getFromCityId(),
                world.cities().byId(j.getFromCityId()).orElseThrow().getName(),
                j.getToCityId(),
                world.cities().byId(j.getToCityId()).orElseThrow().getName(),
                j.getGroupSize(),
                j.getPreferredCabinService().name(),
                j.getBookedCabinService().name(),
                j.isReturningBack() ? 1 : 0,
                j.getAttemptCounter(),
                flags(j),
                tf1.map(TransportFlights.Flight::getId).orElse(null),
                tf1.map(f -> f.getStatus().name()).orElse(null),
                fm1.map(f -> world.airports().getIcao(f.getDepartureAirportId()).orElseThrow()).orElse(null),
                fm1.map(f -> world.airports().getIcao(f.getDestinationAirportId()).orElseThrow()).orElse(null),
                tf2.map(TransportFlights.Flight::getId).orElse(null),
                tf2.map(f -> f.getStatus().name()).orElse(null),
                fm2.map(f -> world.airports().getIcao(f.getDepartureAirportId()).orElseThrow()).orElse(null),
                fm2.map(f -> world.airports().getIcao(f.getDestinationAirportId()).orElseThrow()).orElse(null)
        );
    }

    @Data
    @AllArgsConstructor
    public static class JourneyDto {
        private int id;
        private String st;
        private String hrtBt;
        private int lCId;
        private String lCN;
        private int fCId;
        private String fCN;
        private int tCId;
        private String tCN;
        private int gs;
        private String pCs;
        private String bCs;
        private int dir;
        private int atCnt;
        // the flags of the journey (the column Dir Cnt of the page): n - none, P - the journey of a pilot, S - the special group
        private String sp;
        private Integer tf1Id;
        private String tf1St;
        private String tf1From;
        private String tf1To;
        private Integer tf2Id;
        private String tf2St;
        private String tf2From;
        private String tf2To;
    }
}
