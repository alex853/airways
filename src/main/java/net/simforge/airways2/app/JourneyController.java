package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/journey")
@CrossOrigin
public class JourneyController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public List<JourneyDto> getAll() {
        return worldBean.read(world -> world.journeys().all()
                .map(j -> toDto(world, j))
                .toList());
    }

    @GetMapping("/stats")
    public Map<String, Integer> getStats() {
        return worldBean.read(world -> world.journeys().all()
                .collect(Collectors.groupingBy(
                    j -> j.getStatus().name(),
                    Collectors.summingInt(Journeys.Journey::getGroupSize))));
    }

    private static JourneyDto toDto(final World world, final Journeys.Journey j) {
        final Optional<TransportFlights.Flight> tf1 = world.transportFlights().byId(j.getTransportFlight1Id());
        final Optional<FlightMissions.Mission> fm1 = tf1.flatMap(f -> world.flightMissions().byId(f.getFlightMissionId()));

        final Optional<TransportFlights.Flight> tf2 = world.transportFlights().byId(j.getTransportFlight2Id());
        final Optional<FlightMissions.Mission> fm2 = tf2.flatMap(f -> world.flightMissions().byId(f.getFlightMissionId()));

        return new JourneyDto(
                j.getId(),
                j.getStatus().name(),
                WebTime.ts(j.getHeartbeatTime()),
                j.getFromCityId(),
                world.cities().byId(j.getFromCityId()).orElseThrow().getName(),
                j.getToCityId(),
                world.cities().byId(j.getToCityId()).orElseThrow().getName(),
                j.getGroupSize(),
                j.getCabinService().name(),
                j.isReturningBack() ? 1 : 0,
                j.getAttemptCounter(),
                tf1.map(TransportFlights.Flight::getId).orElse(null),
                tf1.map(f -> f.getStatus().name()).orElse(null),
                fm1.map(f -> world.airports().getIcao(f.getDepartureAirportId())).orElse(null),
                fm1.map(f -> world.airports().getIcao(f.getDestinationAirportId())).orElse(null),
                tf2.map(TransportFlights.Flight::getId).orElse(null),
                tf2.map(f -> f.getStatus().name()).orElse(null),
                fm2.map(f -> world.airports().getIcao(f.getDepartureAirportId())).orElse(null),
                fm2.map(f -> world.airports().getIcao(f.getDestinationAirportId())).orElse(null)
        );
    }

    @Data
    @AllArgsConstructor
    private static class JourneyDto {
        private int id;
        private String st;
        private String hrtBt;
        private int fCId;
        private String fCN;
        private int tCId;
        private String tCN;
        private int gs;
        private String cs;
        private int dir;
        private int atCnt;
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
