package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.vatsimtracker.Flightplan;
import net.simforge.airways2.app.vatsimtracker.PilotContext;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.networkview.core.Position;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
                    final Flightplan fp = c.getFlightplan();
                    final Map<Integer, Position> positions = vatsimTrackerBean.getLastProcessedPositions();
                    final Position cp = positions != null ? positions.get(c.getPilotNumber()) : null;
                    return new PilotDto(
                            c.getPilotNumber(),
                            c.getFlightStage().name(),
                            c.getAircraftRegNo(),
                            fp != null ? fp.getStatus().name() : null,
                            fp != null ? fp.getFiledAt() : null,
                            fp != null ? fp.getAircraftType() : null,
                            fp != null ? fp.getDeparture() : null,
                            fp != null ? fp.getDestination() : null,
                            cp != null ? cp.getAirportIcao() : null,
                            cp != null ? cp.getFpAircraftType() : null,
                            cp != null ? cp.getFpDeparture() : null,
                            cp != null ? cp.getFpDestination() : null,
                            c.getTrackTail() != null ? c.getTrackTail().stream()
                                    .map(l -> "(" + df3digits.format(l.getDistance()) + "," + df3digits.format(l.getTime()))
                                    .collect(Collectors.joining(", ")) : null,
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
        private String regNo;
        private String fpStatus;
        private String fpFiledAt;
        private String fpType;
        private String fpDep;
        private String fpDest;
        private String cpLocation;
        private String cpType;
        private String cpDep;
        private String cpDest;
        private String lastTrackedDistance;
        private String removal;
        private int fmId;
        private String fmStatus;
        private String fmAircraftRegNo;
    }
}
