package net.simforge.airways2.app.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.userstorage.User;
import net.simforge.airways2.app.userstorage.UserStorage;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.PilotLogbook;
import net.simforge.airways2.world.datamodel.Persons;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The pilot of the user: what the user sees about the person who flies for the user (token authentication, see AuthFilter).
 */
@RestController
@RequestMapping("/pilot")
@CrossOrigin
public class PilotController {
    @Autowired
    private WorldRunnerBean worldBean;

    /**
     * The pilot of the user as a person: who it is and where the pilot is now. Facts only, no verdicts (whether a flight may be booked or
     * started is told by the dashboard and by BusyBirds).
     */
    @GetMapping
    public PilotDto getPilot(@RequestAttribute("userId") final int userId) {
        return worldBean.read(world -> {
            final int personId = UserStorage.getInstance().findById(userId).map(User::getPersonId).orElse(0);
            final Persons.Person person = personId > 0 ? world.persons().byId(personId).orElse(null) : null;
            if (person == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The user has no pilot (person)");
            }

            String airportIcao = null;
            String airportName = null;
            String cityName = null;
            switch (person.getLocationType()) {
                case Airport -> {
                    airportIcao = world.airports().getIcao(person.getLocationId()).orElse(null);
                    airportName = world.airports().byId(person.getLocationId()).map(a -> a.getName()).orElse(null);
                }
                case City -> cityName = world.cities().byId(person.getLocationId()).map(c -> c.getName()).orElse(null);
                default -> { }
            }

            return new PilotDto(person.getInitials(), person.getState().name(), person.getLocationType().name(), airportIcao, airportName, cityName);
        });
    }

    @GetMapping("/logbook")
    public LogbookDto getLogbook(@RequestAttribute("userId") final int userId) {
        return worldBean.read(world -> {
            final int personId = UserStorage.getInstance().findById(userId).map(User::getPersonId).orElse(0);
            if (personId <= 0 || world.persons().byId(personId).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The user has no pilot (person)");
            }
            return buildLogbook(world, personId);
        });
    }

    /**
     * The experience of the pilot: the totals of the logbook and, for every type of aircraft in the logbook, the currency on the type (how recent and how
     * much). It is the first look at the qualifications, the rules which use these numbers (stars, pay) come later.
     */
    @GetMapping("/qualifications")
    public QualificationsDto getQualifications(@RequestAttribute("userId") final int userId) {
        return worldBean.read(world -> {
            final int personId = UserStorage.getInstance().findById(userId).map(User::getPersonId).orElse(0);
            if (personId <= 0 || world.persons().byId(personId).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The user has no pilot (person)");
            }
            return buildQualifications(world, personId, world.getWorldTime());
        });
    }

    // The window of "recent landings" is 24 hours for the tests, it is going to be 90 days
    static final int RECENT_LANDINGS_WINDOW_HOURS = 24;
    // The flights of the last 12 months
    static final int RECENT_FLIGHTS_WINDOW_DAYS = 365;

    static QualificationsDto buildQualifications(final World world, final int personId, final int worldTime) {
        final int recentLandingsFrom = worldTime - RECENT_LANDINGS_WINDOW_HOURS * 60 * 60;
        final int recentFlightsFrom = worldTime - RECENT_FLIGHTS_WINDOW_DAYS * 24 * 60 * 60;

        // the type of the aircraft by its ICAO code, in the order of the first flight on it
        final Map<String, TypeAccumulator> byType = new LinkedHashMap<>();
        world.pilotLogbook()
                .filter(world.pilotLogbook().byPersonId(personId))
                .sorted(Comparator.comparingInt(PilotLogbook.Entry::getBlocksOffTime))
                .forEach(e -> {
                    final String type = world.aircrafts().byId(e.getAircraftId())
                            .flatMap(a -> world.aircraftTypes().byId(a.getAircraftTypeId()))
                            .map(t -> t.getIcao())
                            .orElse("?");
                    final TypeAccumulator acc = byType.computeIfAbsent(type, TypeAccumulator::new);

                    final int landings = e.getDayLandings() + e.getNightLandings();
                    acc.flights++;
                    acc.landings += landings;
                    acc.nightTakeoffs += e.getNightTakeoffs();
                    acc.nightLandings += e.getNightLandings();
                    acc.flightTime += e.getFlightTime();
                    // a flight counts for the time of its landing, that is the blocks on
                    if (e.getBlocksOnTime() >= recentLandingsFrom) {
                        acc.recentLandings += landings;
                    }
                    if (e.getBlocksOnTime() >= recentFlightsFrom) {
                        acc.recentFlights++;
                    }
                });

        final List<TypeCurrencyDto> types = byType.values().stream()
                .sorted(Comparator.comparingDouble((TypeAccumulator a) -> a.flightTime).reversed().thenComparing(a -> a.type))
                .map(a -> new TypeCurrencyDto(a.type, a.recentLandings, a.landings, a.nightTakeoffs, a.nightLandings,
                        a.recentFlights, a.flights, round1(a.flightTime)))
                .toList();

        return new QualificationsDto(buildLogbook(world, personId).getTotals(), RECENT_LANDINGS_WINDOW_HOURS, RECENT_FLIGHTS_WINDOW_DAYS, types);
    }

    private static class TypeAccumulator {
        final String type;
        int flights;
        int recentFlights;
        int landings;
        int recentLandings;
        int nightTakeoffs;
        int nightLandings;
        float flightTime;

        TypeAccumulator(final String type) {
            this.type = type;
        }
    }

    // The flights of the person, the latest first, with the totals.
    static LogbookDto buildLogbook(final World world, final int personId) {
        final List<LogbookEntryDto> entries = world.pilotLogbook()
                .filter(world.pilotLogbook().byPersonId(personId))
                .sorted(Comparator.comparingInt(PilotLogbook.Entry::getBlocksOffTime).reversed())
                .map(e -> toDto(world, e))
                .toList();

        float flightTime = 0;
        float nightTime = 0;
        float ifrTime = 0;
        int dayTakeoffs = 0;
        int nightTakeoffs = 0;
        int dayLandings = 0;
        int nightLandings = 0;
        for (final LogbookEntryDto e : entries) {
            flightTime += e.getFlightTime();
            nightTime += e.getNightTime();
            ifrTime += e.getIfrTime();
            dayTakeoffs += e.getDayTakeoffs();
            nightTakeoffs += e.getNightTakeoffs();
            dayLandings += e.getDayLandings();
            nightLandings += e.getNightLandings();
        }

        return new LogbookDto(new LogbookTotalsDto(entries.size(), round1(flightTime), round1(nightTime), round1(ifrTime),
                        dayTakeoffs, nightTakeoffs, dayLandings, nightLandings),
                entries);
    }

    private static LogbookEntryDto toDto(final World world, final PilotLogbook.Entry e) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(e.getAircraftId()).orElse(null);
        return new LogbookEntryDto(
                e.getId(),
                e.getDateOfFlight().toString(),
                world.airports().getIcao(e.getFromAirportId()).orElse(null),
                world.airports().getIcao(e.getToAirportId()).orElse(null),
                aircraft != null ? aircraft.getRegNo() : null,
                aircraft != null ? world.aircraftTypes().byId(aircraft.getAircraftTypeId()).map(t -> t.getIcao()).orElse(null) : null,
                TimeTools.hhmmOrNull(e.getBlocksOffTime()),
                TimeTools.hhmmOrNull(e.getBlocksOnTime()),
                e.getDurationMinutes(),
                e.getFlightTime(),
                e.getNightTime(),
                e.getIfrTime(),
                e.isIfr(),
                e.getDayTakeoffs(),
                e.getNightTakeoffs(),
                e.getDayLandings(),
                e.getNightLandings());
    }

    private static float round1(final float hours) {
        return Math.round(hours * 10.0f) / 10.0f;
    }

    @Data
    @AllArgsConstructor
    public static class PilotDto {
        private String initials;
        private String state;
        private String locationType;
        // the airport where the pilot is (ICAO and name), null if the pilot is not at an airport
        private String airportIcao;
        private String airportName;
        // the city where the pilot is, null if the pilot is not in a city
        private String cityName;
    }

    @Data
    @AllArgsConstructor
    public static class QualificationsDto {
        // the totals of the whole logbook
        private LogbookTotalsDto totals;
        // the windows of the "recent" numbers of the types: landings (hours) and flights (days)
        private int recentLandingsWindowHours;
        private int recentFlightsWindowDays;
        private List<TypeCurrencyDto> types;
    }

    /**
     * The currency of the pilot on a type of aircraft (the ICAO code of the type).
     */
    @Data
    @AllArgsConstructor
    public static class TypeCurrencyDto {
        private String aircraftType;
        // landings in the recent window (recentLandingsWindowHours)
        private int recentLandings;
        private int landings;
        private int nightTakeoffs;
        private int nightLandings;
        // flights (segments) in the last 12 months
        private int recentFlights;
        private int flights;
        // hours
        private float flightTime;
    }

    @Data
    @AllArgsConstructor
    public static class LogbookDto {
        private LogbookTotalsDto totals;
        private List<LogbookEntryDto> entries;
    }

    @Data
    @AllArgsConstructor
    public static class LogbookTotalsDto {
        private int flights;
        private float flightTime;
        private float nightTime;
        private float ifrTime;
        private int dayTakeoffs;
        private int nightTakeoffs;
        private int dayLandings;
        private int nightLandings;
    }

    @Data
    @AllArgsConstructor
    public static class LogbookEntryDto {
        private int id;
        private String date;
        private String from;
        private String to;
        private String regNo;
        private String aircraftType;
        private String blocksOff;
        private String blocksOn;
        private int minutes;
        private float flightTime;
        private float nightTime;
        private float ifrTime;
        private boolean ifr;
        private int dayTakeoffs;
        private int nightTakeoffs;
        private int dayLandings;
        private int nightLandings;
    }
}
