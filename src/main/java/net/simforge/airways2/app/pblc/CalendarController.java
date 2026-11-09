package net.simforge.airways2.app.pblc;

import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.processors.RosterForecast;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * The calendar of the flights of a pilot as an iCalendar file (docs/calendar.md), to subscribe to it by the link in Google Calendar or any other one. No authentication: the
 * link itself is the key (see CalendarKeys). A wrong link and a missing secret are the same 404.
 */
@RestController
@RequestMapping("/calendar")
public class CalendarController {
    private static final String DASHBOARD_URL = "https://simforge.net/airways/flight-dashboard.html";

    @SuppressWarnings("SpringJavaAutowiredFieldsWarningInspection")
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping("/{slug}")
    public ResponseEntity<String> calendar(@PathVariable("slug") final String slug) {
        final OptionalInt userId = CalendarKeys.getInstance().userIdOf(slug);
        if (userId.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        final String body = worldBean.read(world -> render(world, userId.getAsInt(), Instant.now()));
        return ResponseEntity.ok()
                .header("Content-Type", "text/calendar;charset=UTF-8")
                .header("Cache-Control", "no-cache")
                .body(body);
    }

    static String render(final World world, final int userId, final Instant now) {
        final List<CalendarFeed.Event> events = new ArrayList<>();
        for (final RosterForecast.Entry entry : RosterForecast.of(world, userId)) {
            final FlightMissions.Mission flight = entry.mission();
            if (entry.expectedDeparture() == 0) {
                continue;
            }
            final Instant start = Instant.ofEpochSecond(entry.expectedDeparture());
            // a flight always lasts something, a calendar does not like an event which ends before it starts
            final Instant end = entry.expectedArrival() > entry.expectedDeparture() ? Instant.ofEpochSecond(entry.expectedArrival()) : start.plusSeconds(60);

            final Airports.Airport departure = world.airports().byId(flight.getDepartureAirportId()).orElse(null);
            final String depIcao = departure != null ? departure.getIcao() : "?";
            final String destIcao = world.airports().getIcao(flight.getDestinationAirportId()).orElse("?");

            final StringBuilder description = new StringBuilder();
            final Aircrafts.Aircraft aircraft = world.aircrafts().byId(flight.getAircraftId()).orElse(null);
            if (aircraft != null) {
                final String type = world.aircraftTypes().byId(aircraft.getAircraftTypeId()).map(t -> t.getIcao()).orElse("?");
                description.append("Aircraft ").append(aircraft.getRegNo()).append(" (").append(type).append(")\n");
            }
            description.append("Status ").append(flight.getStatus().name()).append("\n").append(DASHBOARD_URL);

            events.add(new CalendarFeed.Event(
                    "flight-" + flight.getId() + "@airways.simforge.net",
                    start,
                    end,
                    depIcao + " → " + destIcao,
                    description.toString(),
                    departure != null ? departure.getIcao() + " " + departure.getName() : null));
        }
        return CalendarFeed.build("Airways flights", events, now);
    }
}
