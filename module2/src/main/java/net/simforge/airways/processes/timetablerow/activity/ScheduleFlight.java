/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.processes.timetablerow.activity;

import net.simforge.airways.ops.TimetableOps;
import net.simforge.airways.processengine.ProcessEngine;
import net.simforge.airways.processengine.Result;
import net.simforge.airways.processengine.activity.Activity;
import net.simforge.airways.persistence.EventLog;
import net.simforge.airways.persistence.model.flight.Flight;
import net.simforge.airways.persistence.model.flight.TimetableRow;
import net.simforge.airways.persistence.model.flight.TransportFlight;
import net.simforge.airways.processes.flight.event.Planned;
import net.simforge.airways.processes.transportflight.event.Scheduled;
import net.simforge.airways.util.FlightTimeline;
import net.simforge.airways.util.TimeMachine;
import net.simforge.commons.hibernate.HibernateUtils;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.JavaTime;
import net.simforge.commons.misc.Weekdays;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.simforge.airways.processengine.Result.When.NextDay;
import static net.simforge.airways.processengine.Result.When.NextHour;

/**
 * It creates TransportFlight and Flight for the according to the timetable and date.
 */
public class ScheduleFlight implements Activity {
    private static Logger logger = LoggerFactory.getLogger(ScheduleFlight.class);

    @Inject
    private TimetableRow timetableRow;
    @Inject
    private SessionFactory sessionFactory;
    @Inject
    private TimetableOps timetableService;
    @Inject
    private ProcessEngine engine;
    @Inject
    private TimeMachine timeMachine;

    @Override
    public Result act() {
        BM.start("ScheduleFlight.act");
        try {

            logger.debug("Scheduling flights for {} ...", timetableRow);

            Integer horizon = timetableRow.getHorizon();
            if (horizon == null) {
                horizon = 7; // default horizon
            }

            LocalDate today = timeMachine.today();
            LocalDate tillDay = today.plusDays(horizon);

            Collection<TransportFlight> transportFlights;
            try (Session session = sessionFactory.openSession()) {
                transportFlights = TimetableOps.loadTransportFlights(session, timetableRow, today, tillDay);
            }

            logger.debug("Loaded " + transportFlights.size() + " flights for horizon " + horizon + " days");

            Map<LocalDate, TransportFlight> flightByDate = transportFlights.stream().collect(Collectors.toMap(TransportFlight::getDateOfFlight, Function.identity()));

            Weekdays weekdays = Weekdays.valueOf(timetableRow.getWeekdays());

            boolean someFlightFailed = false;
            for (LocalDate curr = today; curr.isBefore(tillDay) || curr.isEqual(tillDay); curr = curr.plusDays(1)) {
                if (!weekdays.isOn(curr.getDayOfWeek())) {
                    logger.debug("Date {} - skip due to weekdays config", curr);
                    continue;
                }

                TransportFlight existingTransportFlight = flightByDate.get(curr);
                if (existingTransportFlight != null) {
                    logger.debug("Date {} - flight exists", curr);
                    continue;
                }

                logger.debug("Date {} - creating...", curr);

                TransportFlight transportFlight = initTransportFlight(curr, timetableRow);
                Flight flight = initFlight(transportFlight, timetableRow);

                try (Session session = sessionFactory.openSession()) {
                    String msg = String.format("Flight for %s scheduled", curr);

                    HibernateUtils.transaction(session, "ScheduleFlight.act#createFlight", () -> {
                        session.save(flight);

                        transportFlight.setFlight(flight);
                        session.save(transportFlight);

                        flight.setTransportFlight(transportFlight);
                        session.update(flight);

                        session.save(EventLog.make(timetableRow, msg, flight, transportFlight));
                        session.save(EventLog.make(flight, "Scheduled", timetableRow, transportFlight));
                        session.save(EventLog.make(transportFlight, "Scheduled", timetableRow, flight));

                        engine.fireEvent(session, Scheduled.class, transportFlight);
                        engine.fireEvent(session, Planned.class, flight);
                    });

                    logger.info("Flight {} {}-{} departing at {} is scheduled",
                            timetableRow.getNumber(),
                            timetableRow.getFromAirport().getIcao(),
                            timetableRow.getToAirport().getIcao(),
                            timetableRow.getDepartureTime());
                } catch (Exception e) {
                    logger.error("Unable to createOrdinalPerson a flight, timetableRow " + timetableRow, e);
                    someFlightFailed = true;
                }
            }

            if (!someFlightFailed) {
                // process it on the next day
                return Result.resume(NextDay);
            } else {
                // in case of any failure we are going to retry some minutes later
                return Result.resume(NextHour);
            }

        } finally {
            BM.stop();
        }
    }

    private TransportFlight initTransportFlight(LocalDate dateOfFlight, TimetableRow timetableRow) {
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

    private Flight initFlight(TransportFlight transportFlight, TimetableRow timetableRow) {
        Flight flight = new Flight();

        flight.setDateOfFlight(transportFlight.getDateOfFlight());
        flight.setCallsign("TODO"); // todo AK
        flight.setAircraftType(timetableRow.getAircraftType());
        flight.setNumber(transportFlight.getNumber());
        flight.setFromAirport(transportFlight.getFromAirport());
        flight.setToAirport(transportFlight.getToAirport());
        flight.setAlternativeAirport(null); // todo AK from kind of typical flights

        flight.setScheduledDepartureTime(transportFlight.getDepartureDt());
        flight.setScheduledArrivalTime(transportFlight.getArrivalDt());

        FlightTimeline flightTimeline = FlightTimeline.byScheduledDepartureArrivalTime(transportFlight.getDepartureDt(), transportFlight.getArrivalDt());

        flight.setScheduledTakeoffTime(flightTimeline.getTakeoff().getScheduledTime());
        flight.setScheduledLandingTime(flightTimeline.getLanding().getScheduledTime());

        flight.setStatus(Flight.Status.Planned);
        //flight.setHeartbeatDt(JavaTime.nowUtc());

        return flight;
    }

    @Override
    public Result onExpiry() {
        return Result.nothing();
    }
}
