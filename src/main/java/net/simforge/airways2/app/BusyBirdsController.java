package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.tools.Timing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/busy-birds")
@CrossOrigin
public class BusyBirdsController {
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/mission/all")
    public List<MissionDto> getMissions() {
        try (final Timing.Timer ignored = Timing.label("BusyBirdsController - getAllMissions")) {
            return worldBean.read(world -> world.journeys().filter(world.journeys().bySpecialProcessing())
                    .map(j -> new MissionDto(
                            j.getId(),
                            j.getFromCityId(),
                            world.cities().byId(j.getFromCityId()).get().getName(),
                            j.getToCityId(),
                            world.cities().byId(j.getToCityId()).get().getName()))
                    .toList());
        }
    }

    @Data
    @AllArgsConstructor
    private static class MissionDto {
        private int id;
        private int fromCityId;
        private String fromCityName;
        private int toCityId;
        private String toCityName;
    }
}
