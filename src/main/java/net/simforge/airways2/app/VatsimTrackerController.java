package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.vatsimtracker.PilotContext;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/vatsim-tracker")
@CrossOrigin
public class VatsimTrackerController {
    private static final DecimalFormat df3digits = new DecimalFormat("#.###");

    @Autowired
    private WorldRunnerBean worldBean;
    @Autowired
    private VatsimTrackerBean vatsimTrackerBean;

    @GetMapping("/all")
    public List<PilotDto> getAll() {
        return worldBean.read(world -> vatsimTrackerBean.contexts().stream()
                .sorted(Comparator.comparing(PilotContext::getPilotNumber))
                .map(c -> {
                    final Optional<FlightMissions.Mission> mission = c.getFlightMissionId() != 0
                            ? world.flightMissions().byId(c.getFlightMissionId())
                            : Optional.empty();
                    return new PilotDto(
                            c.getPilotNumber(),
                            c.getFlightStage().name(),
                            null,
                            null,
                            df3digits.format(c.getTrackTailDistance()),
                            c.getLocationAirport(),
                            c.getAircraftType(),
                            c.getAircraftRegNo(),
                            c.getPlannedDeparture(),
                            c.getPlannedDestination(),
                            c.shouldBeRemoved() + " / " + c.getRemovalCounter(),
                            c.getFlightMissionId(),
                            mission.map(value -> value.getStatus().name()).orElse(null),
                            mission.map(m -> world.aircrafts().byId(m.getAircraftId()).orElseThrow().getRegNo()).orElse(null));
                })
                .toList());
    }

    @Data
    @AllArgsConstructor
    private static class PilotDto {
        private int pilotNumber;
        private String flightStage;
        private String planningStatus;
        private String overallStatus;
        private String lastTrackedDistance;
        private String locationIcao;
        private String aircraftType;
        private String regNo;
        private String departureIcao;
        private String destinationIcao;
        private String removal;
        private int fmId;
        private String fmStatus;
        private String fmAircraftRegNo;
    }
}
