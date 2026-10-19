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
import java.util.List;

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
