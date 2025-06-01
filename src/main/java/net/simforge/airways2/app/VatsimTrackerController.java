package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.vatsimtracker.PilotContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/vatsim-tracker")
@CrossOrigin
public class VatsimTrackerController {
    @Autowired
    private VatsimTrackerBean vatsimTrackerBean;

    @GetMapping("/all")
    public List<PilotDto> getAll() {
        return vatsimTrackerBean.contexts().stream()
                .sorted(Comparator.comparing(PilotContext::getPilotNumber))
                .map(c -> new PilotDto(
                        c.getPilotNumber(),
                        c.getFlightStage().name(),
                        c.getPlanningStatus().name(),
                        c.getOverallStatus().name(),
                        c.getLastTrackedDistance(),
                        c.getLocationAirport(),
                        c.getAircraftType(),
                        c.getAircraftRegNo(),
                        c.getPlannedDeparture(),
                        c.getPlannedDestination()))
                .toList();
    }

    @Data
    @AllArgsConstructor
    private static class PilotDto {
        private int pilotNumber;
        private String flightStage;
        private String planningStatus;
        private String overallStatus;
        private float lastTrackedDistance;
        private String locationIcao;
        private String aircraftType;
        private String regNo;
        private String departureIcao;
        private String destinationIcao;
    }
}
