package net.simforge.airways.ops;

import net.simforge.airways.EventLog;
import net.simforge.airways.model.flight.Flight;
import net.simforge.airways.model.flight.TimetableRow;
import net.simforge.airways.model.flight.TransportFlight;
import net.simforge.airways.processengine.ProcessEngineScheduling;
import net.simforge.airways.processengine.TimeMachine;
import net.simforge.airways.processes.DurationConsts;
import net.simforge.airways.processes.flight.event.Planned;
import net.simforge.airways.processes.transportflight.event.Scheduled;
import net.simforge.airways.util.FlightNumbers;
import net.simforge.airways.util.FlightTimeline;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.JavaTime;
import net.simforge.commons.misc.Weekdays;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TimetableOps {
    private static final Logger log = LoggerFactory.getLogger(TimetableOps.class);

    @SuppressWarnings("WeakerAccess")
    public static Collection<TransportFlight> loadTransportFlights(Session session, TimetableRow timetableRow, LocalDate fromDate, LocalDate toDate) {
        BM.start("TimetableOps.loadTransportFlights");
        try {

            //noinspection unchecked
            return session
                    .createQuery("select tf from TransportFlight tf where tf.timetableRow = :timetableRow and tf.dateOfFlight between :fromDate and :toDate")
                    .setEntity("timetableRow", timetableRow)
                    .setParameter("fromDate", fromDate)
                    .setParameter("toDate", toDate)
                    .list();

        } finally {
            BM.stop();
        }
    }

    public static boolean scheduleFlights(TimetableRow timetableRow, Session session, ProcessEngineScheduling scheduling, TimeMachine timeMachine) {
        BM.start("ScheduleFlight.act");
        try {

            log.debug("Scheduling flights for {} ...", timetableRow);

            Integer horizon = timetableRow.getHorizon();
            if (horizon == null) {
                horizon = 7; // default horizon
            }

            LocalDate today = timeMachine.now().toLocalDate();
            LocalDateTime todayFlightDeadline = today.atTime(LocalTime.parse(timetableRow.getDepartureTime())).minusHours(DurationConsts.DO_NOT_SCHEDULE_FLIGHTS_CLOSER_THAN_THAT_HOURS);
            LocalDate fromDay = todayFlightDeadline.isAfter(timeMachine.now()) ? today : today.plusDays(1);

            LocalDate tillDay = today.plusDays(horizon);

            Collection<TransportFlight> transportFlights = TimetableOps.loadTransportFlights(session, timetableRow, today, tillDay);

            log.debug("Loaded " + transportFlights.size() + " flights for horizon " + horizon + " days");

            Map<LocalDate, TransportFlight> flightByDate = transportFlights.stream().collect(Collectors.toMap(TransportFlight::getDateOfFlight, Function.identity()));

            Weekdays weekdays = Weekdays.valueOf(timetableRow.getWeekdays());

            boolean someFlightFailed = false;
            for (LocalDate curr = fromDay; curr.isBefore(tillDay) || curr.isEqual(tillDay); curr = curr.plusDays(1)) {
                if (!weekdays.isOn(curr.getDayOfWeek())) {
                    log.debug("Date {} - skip due to weekdays config", curr);
                    continue;
                }

                TransportFlight existingTransportFlight = flightByDate.get(curr);
                if (existingTransportFlight != null) {
                    log.debug("Date {} - flight exists", curr);
                    continue;
                }

                log.debug("Date {} - creating...", curr);

                TransportFlight transportFlight = initTransportFlight(curr, timetableRow);
                Flight flight = initFlight(transportFlight, timetableRow);

                try {
                    String msg = String.format("Flight for %s scheduled", curr);

                    HibernateUtils.transaction(session, "ScheduleFlight.act#createFlight", () -> {
                        session.save(flight);

                        transportFlight.setFlight(flight);
                        session.save(transportFlight);

                        flight.setTransportFlight(transportFlight);
                        session.update(flight);

                        EventLog.info(session, log, timetableRow, msg, flight, transportFlight);
                        EventLog.info(session, log, flight, "Scheduled", timetableRow, transportFlight);
                        EventLog.info(session, log, transportFlight, "Scheduled", timetableRow, flight);

                        scheduling.fireEvent(session, Scheduled.class, transportFlight);
                        scheduling.fireEvent(session, Planned.class, flight);
                    });

                    log.info("Flight {} {}-{} departing at {} is scheduled",
                            timetableRow.getNumber(),
                            timetableRow.getFromAirport().getIcao(),
                            timetableRow.getToAirport().getIcao(),
                            timetableRow.getDepartureTime());
                } catch (RuntimeException e) {
                    log.error("Unable to create a flight, timetableRow " + timetableRow, e);
                    someFlightFailed = true;
                }
            }

            //noinspection RedundantIfStatement
            if (!someFlightFailed) {
                // all ok
                return true;
            } else {
                // somewhat failed
                return false;
            }

        } finally {
            BM.stop();
        }
    }

    private static TransportFlight initTransportFlight(LocalDate dateOfFlight, TimetableRow timetableRow) {
        TransportFlight transportFlight = new TransportFlight();

        transportFlight.setTimetableRow(timetableRow);
        transportFlight.setDateOfFlight(dateOfFlight);
        transportFlight.setNumber(timetableRow.getNumber());
        transportFlight.setFromAirport(timetableRow.getFromAirport());
        transportFlight.setToAirport(timetableRow.getToAirport());
        transportFlight.setDepartureDt(dateOfFlight.atTime(LocalTime.parse(timetableRow.getDepartureTime())));
        transportFlight.setArrivalDt(transportFlight.getDepartureDt().plus(JavaTime.hhmmToDuration(timetableRow.getDuration())));
        transportFlight.setStatus(TransportFlight.Status.Scheduled);
        transportFlight.setTotalTickets(timetableRow.getTotalTickets());
        transportFlight.setFreeTickets(transportFlight.getTotalTickets());
        //transportFlight.setHeartbeatDt(JavaTime.nowUtc());

        return transportFlight;
    }

    private static Flight initFlight(TransportFlight transportFlight, TimetableRow timetableRow) {
        Flight flight = new Flight();

        flight.setDateOfFlight(transportFlight.getDateOfFlight());
        flight.setCallsign(FlightNumbers.makeCallsign(transportFlight.getTimetableRow().getAirline(), transportFlight.getFlightNumber()));
        flight.setAircraftType(timetableRow.getAircraftType());
        flight.setFlightNumber(transportFlight.getFlightNumber());
        flight.setFromAirport(transportFlight.getFromAirport());
        flight.setToAirport(transportFlight.getToAirport());

        flight.setScheduledDepartureTime(transportFlight.getDepartureDt());
        flight.setScheduledArrivalTime(transportFlight.getArrivalDt());

        FlightTimeline flightTimeline = FlightTimeline.byScheduledDepartureArrivalTime(transportFlight.getDepartureDt(), transportFlight.getArrivalDt());

        flight.setScheduledTakeoffTime(flightTimeline.getTakeoff().getScheduledTime());
        flight.setScheduledLandingTime(flightTimeline.getLanding().getScheduledTime());

        flight.setStatus(Flight.Status.Planned);
        //flight.setHeartbeatDt(JavaTime.nowUtc());

        return flight;
    }
}
