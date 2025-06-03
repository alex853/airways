package net.simforge.airways2.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.commons.misc.JavaTime;
import net.simforge.networkview.core.report.ReportUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/health")
@CrossOrigin
public class HealthController {
    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    @Autowired
    private WorldRunnerBean worldBean;
    @Autowired
    private VatsimTrackerBean vatsimTrackerBean;

    @GetMapping("/status")
    public ResponseEntity<StatusDto> downloadDirect() {
        final int worldTime = worldBean.read(World::getWorldTime);
        final LocalDateTime worldTimeLdt = Time.toLdt(worldTime);

        final String lastProcessedVatsimReport = vatsimTrackerBean.getLastProcessedReport();
        final LocalDateTime lastProcessedVatsimReportLdt = ReportUtils.fromTimestampJava(lastProcessedVatsimReport);

        final boolean status = isWithin10MinsFromNow(worldTimeLdt)
                && isWithin10MinsFromNow(lastProcessedVatsimReportLdt);

        final StatusDto statusDto = new StatusDto(
                status ? "ok" : "fail",
                worldTimeLdt.toString(),
                lastProcessedVatsimReportLdt.toString());

        return ResponseEntity
                .status(status ? HttpStatus.OK : HttpStatus.EXPECTATION_FAILED)
                .body(statusDto);
    }

    private static boolean isWithin10MinsFromNow(final LocalDateTime ldt) {
        final LocalDateTime now = JavaTime.nowUtc();
        return Duration.between(ldt, now).getSeconds() < 10*Time.ONE_MINUTE;
    }

    @Data
    @AllArgsConstructor
    private static class StatusDto {
        private final String status;
        private final String worldTime;
        private final String lastProcessedVatsimReport;
    }
}
