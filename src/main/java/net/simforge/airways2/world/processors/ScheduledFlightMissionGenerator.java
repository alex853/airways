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
import java.util.Objects;
import java.util.Optional;

public class ScheduledFlightMissionGenerator {
    private static final Logger log = LoggerFactory.getLogger(ScheduledFlightMissionGenerator.class);
    private static long lastExecution;

    private static final int schedulingDepthDays = 3;

    private static final ScheduledFlight[] schedule = {
            new ScheduledFlight(101, "AW101", "F-AUWA", "LFPG", "EGLL", "05:00"),
            new ScheduledFlight(102, "AW102", "F-AUWA", "EGLL", "LFPG", "08:00"),
            new ScheduledFlight(121, "AW121", "F-AUWA", "LFPG", "LIRF", "11:00"),
            new ScheduledFlight(122, "AW122", "F-AUWA", "LIRF", "LFPG", "15:00"),
            new ScheduledFlight(103, "AW103", "F-AUWA", "LFPG", "EGLL", "19:00"),
            new ScheduledFlight(104, "AW104", "F-AUWA", "EGLL", "LFPG", "22:00"),
        
            new ScheduledFlight(131, "AW131", "F-AUWB", "LFPG", "EDDM", "06:00"),
            new ScheduledFlight(132, "AW132", "F-AUWB", "EDDM", "LFPG", "10:00"),
            new ScheduledFlight(141, "AW141", "F-AUWB", "LFPG", "LEBL", "14:00"),
            new ScheduledFlight(142, "AW142", "F-AUWB", "LEBL", "LFPG", "18:00"),
            new ScheduledFlight(105, "AW105", "F-AUWB", "LFPG", "EGLL", "22:00"),
            new ScheduledFlight(106, "AW106", "F-AUWB", "EGLL", "LFPG", "02:00"),
        
            new ScheduledFlight(151, "AW151", "F-AUWC", "LFPG", "EDDH", "04:00"),
            new ScheduledFlight(152, "AW152", "F-AUWC", "EDDH", "LFPG", "07:00"),
            new ScheduledFlight(153, "AW153", "F-AUWC", "LFPG", "EDDB", "10:00"),
            new ScheduledFlight(154, "AW154", "F-AUWC", "EDDB", "LFPG", "13:00"),
            new ScheduledFlight(155, "AW155", "F-AUWC", "LFPG", "LKPR", "16:00"),
            new ScheduledFlight(156, "AW156", "F-AUWC", "LKPR", "LFPG", "19:00"),
        
            new ScheduledFlight(161, "AW161", "F-AUWD", "LFPG", "LOWW", "04:00"),
            new ScheduledFlight(162, "AW162", "F-AUWD", "LOWW", "LFPG", "07:00"),
            new ScheduledFlight(163, "AW163", "F-AUWD", "LFPG", "EIDW", "10:00"),
            new ScheduledFlight(164, "AW164", "F-AUWD", "EIDW", "LFPG", "13:00"),
            new ScheduledFlight(165, "AW165", "F-AUWD", "LFPG", "EGPH", "16:00"),
            new ScheduledFlight(166, "AW166", "F-AUWD", "EGPH", "LFPG", "19:00"),
        
            new ScheduledFlight(171, "AW171", "F-AUWE", "LFPG", "LIMC", "04:30"),
            new ScheduledFlight(172, "AW172", "F-AUWE", "LIMC", "LFPG", "08:00"),
            new ScheduledFlight(173, "AW173", "F-AUWE", "LFPG", "LEMD", "11:30"),
            new ScheduledFlight(174, "AW174", "F-AUWE", "LEMD", "LFPG", "15:30"),
            new ScheduledFlight(107, "AW107", "F-AUWE", "LFPG", "EGLL", "19:30"),
            new ScheduledFlight(108, "AW108", "F-AUWE", "EGLL", "LFPG", "22:30"),
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
                    .map(f -> world.flightMissions().byId(f.getFlightMissionId()).orElse(null)) // todo ak3 that .orElse(null) happens due to removal of flight missions, think about it when you will be back to that code
                    .filter(Objects::nonNull)
                    .filter(f -> f.getDateOfFlight().equals(flightDate))
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
            final ScheduledFlights.Flight scheduledFlight = world.scheduledFlights().create(schedule.scheduleId, newFlightMission.getId());

            final TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(newFlightMission, scheduledFlight);

            world.log(EventLog.EventType.FlightScheduledAndDispatched, EventLog.pilotId(0), newFlightMission, aircraft.get());
            log.info("f/m #{} - flight scheduled and dispatched, flight no {}, date of flight {}, aircraft {}",
                    newFlightMission.getId(), schedule.flightNo, flightDate, aircraft.get().getRegNo());
            log.info("t/f #{} - created", transportFlight.getId());
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
