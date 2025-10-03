package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.datamodel.Journeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        return new JourneyDto(
                e.getId(),
                e.getStatus().name(),
                WebTime.ts(e.getHeartbeatTime()),
                world.cities().byId(e.getFromCityId()).orElseThrow().getName(),
                world.cities().byId(e.getToCityId()).orElseThrow().getName(),
                e.getGroupSize(),
                e.getTransportFlight1Id() != 0 ? e.getTransportFlight1Id() : null);
    }

    @Data
    @AllArgsConstructor
    private static class JourneyDto {
        private int id;
        private String st;
        private String hrtBt;
        private int fCId;
        private int tCId;
        private int gs;
        private Integer tf1Id;
    }
}
