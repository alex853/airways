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
                .map(JourneyController::toDto)
                .toList());
    }

    private static JourneyDto toDto(final Journeys.Journey e) {
        return new JourneyDto(
                e.getId(),
                e.getStatus().name(),
                WebTime.ts(e.getHeartbeatTime()),
                e.getFromCityId(),
                e.getToCityId(),
                e.getGroupSize());
    }

    @Data
    @AllArgsConstructor
    private static class JourneyDto {
        private int id;
        private String status;
        private String heartbeat;
        private int fromCityId;
        private int toCityId;
        private int groupSize;
    }
}
