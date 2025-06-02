package net.simforge.airways2.world.processors;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.commons.misc.JavaTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

public class ScheduledFlightMissionGenerator {
    private static final Logger log = LoggerFactory.getLogger(ScheduledFlightMissionGenerator.class);
    private static long lastExecution;

    private static final int schedulingDepthDays = 2;

    private static final ScheduledFlight[] schedule = {
            new ScheduledFlight(101, "AW101", "F-AUWA", "LFPG", "EGLL", "05:00"),
            new ScheduledFlight(102, "AW102", "F-AUWA", "EGLL", "LFPG", "08:00"),
            new ScheduledFlight(121, "AW121", "F-AUWA", "LFPG", "LIRF", "11:00"),
            new ScheduledFlight(122, "AW122", "F-AUWA", "LIRF", "LFPG", "15:00"),
    };

    public static void process(final World world) {
        if (System.currentTimeMillis() - lastExecution < 3600000) {
            return;
        }
        lastExecution = System.currentTimeMillis();

        Arrays.stream(schedule).forEach(each -> scheduleFlight(world, each));
    }

    private static void scheduleFlight(final World world, final ScheduledFlight schedule) {
        final Collection<ScheduledFlights.Flight> scheduledFlights = world.scheduledFlights().byScheduleId(schedule.scheduleId);
        final LocalDateTime worldDateTime = Time.toLdt(world.getWorldTime());
        final LocalDate worldDate = worldDateTime.toLocalDate();
        for (int i = 0; i <= schedulingDepthDays; i++) {
            final LocalDate flightDate = worldDate.plusDays(i);
            final Optional<FlightMissions.Mission> flightMission = scheduledFlights.stream()
                    .map(f -> world.flightMissions().byId(f.getFlightMissionId()).orElseThrow())
                    .filter(f -> Time.toLdt(f.getPlannedDepartureTime()).toLocalDate().equals(flightDate))
                    .findFirst();
            if (flightMission.isPresent()) {
                continue;
            }

            final LocalDateTime departureTime = flightDate.atTime(JavaTime.hhmmToLocalTime(schedule.getDepartureTime()));
            final Duration remainingTimeToDepartureTime = Duration.between(worldDateTime, departureTime);
            if (remainingTimeToDepartureTime.getSeconds() < Time.ONE_HOUR) {
                continue;
            }

            final Optional<Aircrafts.Aircraft> aircraft = world.aircrafts().byRegNo(schedule.regNo);
            if (aircraft.isEmpty()) {
                log.warn("flight no {} - unable to find aircraft with reg no {}", schedule.flightNo, schedule.regNo);
                continue;
            }
            final Airports.Airport departureAirport = world.airports().byIcao(schedule.from).orElseThrow();
            final Airports.Airport destinationAirport = world.airports().byIcao(schedule.to).orElseThrow();
            final FlightMissions.Mission newFlightMission = FlightMissionHelper.scheduleDispatchedMission(world, aircraft.get(), departureAirport, destinationAirport, Time.fromLdt(departureTime));
            world.scheduledFlights().create(schedule.scheduleId, newFlightMission.getId());

            world.log(EventLog.EventType.FlightScheduledAndDispatched, EventLog.pilotId(0), newFlightMission, aircraft.get());
            log.info("f/m #{} - flight scheduled and dispatched, flight no {}, date of flight {}, aircraft {}",
                    newFlightMission.getId(), schedule.flightNo, flightDate, aircraft.get().getRegNo());
        }
    }

    @Data
    @AllArgsConstructor
    private static class ScheduledFlight {
        private int scheduleId;
        private String flightNo;
        private String regNo;
        private String from;
        private String to;
        private String departureTime;
    }
}
