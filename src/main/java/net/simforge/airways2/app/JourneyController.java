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
import java.util.Optional;

@RestController
@RequestMapping("/journey")
@CrossOrigin
public class JourneyController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/all")
    public List<JourneyDto> getAll() {
        return worldBean.read(world -> world.journeys().all().stream()
                .map(j -> toDto(world, j))
                .toList());
    }

    private static JourneyDto toDto(final World world, final Journeys.Journey e) {
        final Optional<TransportFlights.Flight> tf1 = world.transportFlights().byId(e.getTransportFlight1Id());
        final Optional<FlightMissions.Mission> fm1 = tf1.map(f -> world.flightMissions().byId(f.getFlightMissionId())).orElse(Optional.empty());
        // todo ak0 'stopover support' - tf2
        return new JourneyDto(
                e.getId(),
                e.getStatus().name(),
                WebTime.ts(e.getHeartbeatTime()),
                e.getFromCityId(),
                world.cities().byId(e.getFromCityId()).orElseThrow().getName(),
                e.getToCityId(),
                world.cities().byId(e.getToCityId()).orElseThrow().getName(),
                e.getGroupSize(),
                tf1.map(f -> f.getId()).orElse(null),
                tf1.map(f -> f.getStatus().name()).orElse(null),
                fm1.map(f -> world.airports().getIcao(f.getDepartureAirportId())).orElse(null),
                fm1.map(f -> world.airports().getIcao(f.getDestinationAirportId())).orElse(null)
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
        private Integer tf1Id;
        private String tf1St;
        private String tf1From;
        private String tf1To;
    }
}
