package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.vatsimtracker.Flightplan;
import net.simforge.airways2.app.vatsimtracker.PilotContext;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.commons.legacy.misc.Settings;
import net.simforge.commons.misc.JavaTime;
import net.simforge.networkview.core.Network;
import net.simforge.networkview.core.Position;
import net.simforge.networkview.core.report.ReportUtils;
import net.simforge.networkview.core.report.compact.CompactifiedStorage;
import net.simforge.networkview.core.report.persistence.Report;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/vatsim-tracker")
@CrossOrigin
public class VatsimTrackerController {
    private static final Logger log = LoggerFactory.getLogger(VatsimTrackerController.class);
    private static final DecimalFormat df3digits = new DecimalFormat("#.###");
    private static final String storageRoot = Settings.get("network.view.storage.root");
    private static final CompactifiedStorage storage = CompactifiedStorage.getStorage(storageRoot, Network.VATSIM);

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
                                    .map(l -> "(" + df3digits.format(l.getDistance()) + "," + df3digits.format(l.getTime()) + ")")
                                    .collect(Collectors.joining(", ")) : null,
                            c.shouldBeRemoved() + " / " + c.getRemovalCounter(),
                            c.getFlightMissionId(),
                            mission.map(value -> value.getStatus().name()).orElse(null),
                            mission.map(m -> world.aircrafts().byId(m.getAircraftId()).orElseThrow().getRegNo()).orElse(null),
                            mission.filter(m -> m.getActualLandingAirportId() != 0)
                                    .flatMap(m -> world.airports().byId(m.getActualLandingAirportId()))
                                    .map(Airports.Airport::getIcao)
                                    .orElse(null));
                })
                .toList());
    }

    @GetMapping("/pilot/positions")
    public List<PositionDto> getPositions(@RequestParam final int pilotNumber,
                                          @RequestParam final String timeframe) throws IOException {
        return storage.listAllReports().stream()
                .filter(r -> checkReportTimeframe(r, timeframe))
                .map(r -> loadPositions(r).stream()
                        .filter(p -> p.getPilotNumber() == pilotNumber)
                        .findFirst()
                        .orElseGet(() -> Position.createOfflinePosition(createEmptyReport(r))))
                .map(PositionDto::fromPosition)
                .toList();
    }

    private static List<Position> loadPositions(String r) {
        try {
            return storage.loadPositions(r);
        } catch (IOException e) {
            log.error("unable to load positions for report '" + r + "'", e);
            throw new RuntimeException(e);
        }
    }

    private Report createEmptyReport(final String report) {
        final Report r = new Report();
        r.setReport(report);
        return r;
    }

    private boolean checkReportTimeframe(final String report, final String timeframe) {
        final LocalDateTime reportDt = ReportUtils.fromTimestampJava(report);
        final LocalDateTime now = JavaTime.nowUtc();
        final LocalDateTime nowMinusOneHour = now.minusHours(1);
        return nowMinusOneHour.isBefore(reportDt);
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
        private String trackTail;
        private String removal;
        private int fmId;
        private String fmStatus;
        private String fmAircraftRegNo;
        private String fmAcLanding;
    }

    @Data
    @AllArgsConstructor
    private static class PositionDto {
        private String rp; // report
        private String st; // status - offline, on ground, in airport, flying
        private float lat;
        private float lon;
        private int alt;
        private int hdg;
        private String apt; // location airport icao

        public static PositionDto fromPosition(final Position p) {
            if (p.isPositionKnown()) {
                return new PositionDto(
                        p.getReportInfo().getDt().toString(),
                        "Offline",
                        0, 0, 0, 0, null);
            }

            return new PositionDto(
                    p.getReportInfo().getDt().toString(),
                    (p.isInAirport() ? "Airport" : (p.isOnGround() ? "On Ground" : "Flying")),
                    (float) p.getCoords().getLat(),
                    (float) p.getCoords().getLon(),
                    p.getActualAltitude(),
                    p.getHeading(),
                    p.getAirportIcao());
        }
    }
}
