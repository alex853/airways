package net.simforge.airways2.app.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.userstorage.User;
import net.simforge.airways2.app.userstorage.UserStorage;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PilotPay;
import net.simforge.airways2.world.computations.PilotRest;
import net.simforge.airways2.world.computations.PilotPayParams;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.PilotLogbook;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.Persons;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
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
        // a pilot older than the awake credit gets the credit at the first look, that is a change of the world
        final boolean needsCredit = worldBean.read(world -> world.personControl().awakeCreditIsNotStarted(personOf(world, userId)));
        if (needsCredit) {
            return worldBean.modifySync(world -> {
                final Persons.Person person = personOf(world, userId);
                world.personControl().startAwakeCredit(person);
                return buildPilot(world, person);
            });
        }
        return worldBean.read(world -> buildPilot(world, personOf(world, userId)));
    }

    /**
     * The pilot goes to sleep (only a free one can). The answer is the pilot as GET /pilot gives it.
     */
    @PostMapping("/sleep")
    public PilotDto sleep(@RequestAttribute("userId") final int userId) {
        return worldBean.modifySync(world -> {
            final Persons.Person person = personOf(world, userId);
            try {
                world.personControl().startSleep(person);
            } catch (final IllegalStateException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
            }
            return buildPilot(world, person);
        });
    }

    /**
     * The pilot wakes up (only a sleeping one can): the sleep is added to the awake credit.
     */
    @PostMapping("/wake")
    public PilotDto wake(@RequestAttribute("userId") final int userId) {
        return worldBean.modifySync(world -> {
            final Persons.Person person = personOf(world, userId);
            try {
                world.personControl().wakeUp(person);
            } catch (final IllegalStateException e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
            }
            return buildPilot(world, person);
        });
    }

    private static Persons.Person personOf(final World world, final int userId) {
        final int personId = UserStorage.getInstance().findById(userId).map(User::getPersonId).orElse(0);
        final Persons.Person person = personId > 0 ? world.persons().byId(personId).orElse(null) : null;
        if (person == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The user has no pilot (person)");
        }
        return person;
    }

    /**
     * The awake credit as the page shows it. While the pilot is awake it is the credit now (it falls with the time); while the pilot sleeps it is the credit
     * at the moment of falling asleep, and the credit after waking up now is next to it. A pilot who has no credit started yet shows the initial one.
     */
    static AwakeDto buildAwake(final World world, final Persons.Person person) {
        final int now = world.getWorldTime();
        final boolean started = person.getCreditTime() != 0;
        final int stored = started ? person.getAwakeCreditMinutes() : PilotRest.INITIAL_CREDIT_MINUTES;
        if (person.getState() == Persons.State.Resting && started) {
            final int sleepMinutes = PilotRest.minutesBetween(person.getCreditTime(), now);
            return new AwakeDto(stored, true, sleepMinutes, PilotRest.creditAfterSleep(stored, sleepMinutes));
        }
        final int current = started ? PilotRest.creditNow(stored, person.getCreditTime(), now) : stored;
        return new AwakeDto(current, false, 0, null);
    }

    static PilotDto buildPilot(final World world, final Persons.Person person) {
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

        // the money of the pilot: the balance of the bank account the person refers to, null while the person has no account
        final Long bankBalance = person.getBankAccountId() > 0
                ? world.bankAccounts().byId(person.getBankAccountId()).map(BankAccounts.BankAccount::getBalance).orElse(null)
                : null;

        return new PilotDto(person.getInitials(), person.getState().name(), person.getLocationType().name(), airportIcao, airportName, cityName, bankBalance,
                buildAwake(world, person));
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

    // The window of "recent landings" is 90 days
    static final int RECENT_LANDINGS_WINDOW_HOURS = 90 * 24;
    // The flights of the last 12 months
    static final int RECENT_FLIGHTS_WINDOW_DAYS = 365;

    // The criteria of the currency on a type. Can carry passengers: enough landings in the recent window. Night flying: a night takeoff and a night
    // landing in the recent window. Type currency: enough flights and enough hours in the last 12 months. The totals of the type are not used in the criteria.
    static final int CRITERIA_RECENT_LANDINGS = 3;
    static final int CRITERIA_NIGHT_TAKEOFFS = 1;
    static final int CRITERIA_NIGHT_LANDINGS = 1;
    static final int CRITERIA_RECENT_FLIGHTS = 12;
    static final int CRITERIA_RECENT_FLIGHT_HOURS = 12;

    // The experience level on a type, 0 to 5 (0 is no level, the page shows it as 0, I, II, III, IV, V). A level needs both the flights (segments) and the
    // hours of all the logbook on the type; the pilot has the highest level whose both numbers are reached. The level does not go down with time.
    // Row i is the level i + 1: { flights, hours }.
    static final int[][] EXPERIENCE_LEVELS = {{25, 50}, {50, 100}, {100, 200}, {250, 500}, {500, 1000}};

    static int experienceLevel(final int flights, final float hours) {
        int level = 0;
        for (int i = 0; i < EXPERIENCE_LEVELS.length; i++) {
            if (flights >= EXPERIENCE_LEVELS[i][0] && hours >= EXPERIENCE_LEVELS[i][1]) {
                level = i + 1;
            }
        }
        return level;
    }

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
                    acc.lastFlightTime = Math.max(acc.lastFlightTime, e.getBlocksOnTime());
                    acc.flights++;
                    acc.landings += landings;
                    acc.nightTakeoffs += e.getNightTakeoffs();
                    acc.nightLandings += e.getNightLandings();
                    acc.flightTime += e.getFlightTime();
                    // a flight counts for the time of its landing, that is the blocks on
                    if (e.getBlocksOnTime() >= recentLandingsFrom) {
                        acc.recentLandings += landings;
                        acc.recentNightTakeoffs += e.getNightTakeoffs();
                        acc.recentNightLandings += e.getNightLandings();
                    }
                    if (e.getBlocksOnTime() >= recentFlightsFrom) {
                        acc.recentFlights++;
                        acc.recentFlightTime += e.getFlightTime();
                    }
                });

        final List<TypeCurrencyDto> types = byType.values().stream()
                // the page sorts the types as the pilot chooses, the default is the recent one: the type of the last flight goes first
                .sorted(Comparator.comparingInt((TypeAccumulator a) -> a.lastFlightTime).reversed().thenComparing(a -> a.type))
                .map(a -> new TypeCurrencyDto(a.type, a.recentLandings, a.landings, a.recentNightTakeoffs, a.recentNightLandings,
                        a.nightTakeoffs, a.nightLandings, a.recentFlights, round1(a.recentFlightTime), a.flights, round1(a.flightTime),
                        a.recentLandings >= CRITERIA_RECENT_LANDINGS,
                        a.recentNightTakeoffs >= CRITERIA_NIGHT_TAKEOFFS && a.recentNightLandings >= CRITERIA_NIGHT_LANDINGS,
                        a.recentFlights >= CRITERIA_RECENT_FLIGHTS && round1(a.recentFlightTime) >= CRITERIA_RECENT_FLIGHT_HOURS,
                        experienceLevel(a.flights, round1(a.flightTime)),
                        PilotPay.baseIncomeSharePercent(experienceLevel(a.flights, round1(a.flightTime))),
                        a.lastFlightTime))
                .toList();

        return new QualificationsDto(buildLogbook(world, personId).getTotals(), RECENT_LANDINGS_WINDOW_HOURS, RECENT_FLIGHTS_WINDOW_DAYS,
                new CriteriaDto(CRITERIA_RECENT_LANDINGS, CRITERIA_NIGHT_TAKEOFFS, CRITERIA_NIGHT_LANDINGS, CRITERIA_RECENT_FLIGHTS,
                        CRITERIA_RECENT_FLIGHT_HOURS,
                        PilotPay.NO_TYPE_CURRENCY_PERCENT, PilotPay.NO_NIGHT_CURRENCY_PERCENT, PilotPay.CANNOT_CARRY_PASSENGERS_PERCENT),
                experienceLevelDtos(), types);
    }

    /**
     * The parameters of the pay of the pilot on a type of aircraft now: the currencies and the experience level by the logbook. A type which is not in
     * the logbook gives nothing (no currency, level 0). The flight is not manual, that is set when the flight is started manually.
     */
    public static PilotPayParams payParams(final World world, final int personId, final String aircraftTypeIcao) {
        final PilotPayParams params = PilotPayParams.none();
        buildQualifications(world, personId, world.getWorldTime()).getTypes().stream()
                .filter(t -> t.getAircraftType().equals(aircraftTypeIcao))
                .findFirst()
                .ifPresent(t -> {
                    params.setTypeCurrency(t.isTypeCurrent());
                    params.setNightCurrency(t.isNightFlying());
                    params.setCanCarryPassengers(t.isCanCarryPassengers());
                    params.setExperienceLevel(t.getExperienceLevel());
                });
        return params;
    }

    private static List<ExperienceLevelDto> experienceLevelDtos() {
        final List<ExperienceLevelDto> levels = new ArrayList<>();
        for (int i = 0; i < EXPERIENCE_LEVELS.length; i++) {
            levels.add(new ExperienceLevelDto(i + 1, EXPERIENCE_LEVELS[i][0], EXPERIENCE_LEVELS[i][1]));
        }
        return levels;
    }

    private static class TypeAccumulator {
        final String type;
        int flights;
        int recentFlights;
        int landings;
        int recentLandings;
        int nightTakeoffs;
        int nightLandings;
        int recentNightTakeoffs;
        int recentNightLandings;
        float flightTime;
        float recentFlightTime;
        // the time of the landing (blocks on) of the last flight on the type
        int lastFlightTime;

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
        // the balance of the bank account of the pilot, null if the pilot has no account
        private Long bankBalance;
        private AwakeDto awake;
    }

    @Data
    @AllArgsConstructor
    public static class AwakeDto {
        // minutes the pilot can stay awake and active; negative when overtired; while the pilot sleeps it is the credit at the moment of falling asleep
        private int creditMinutes;
        private boolean sleeping;
        // minutes of the sleep so far, zero when awake
        private int sleepMinutes;
        // the credit if the pilot wakes up now (it is more than creditMinutes only after two hours of sleep), null when awake
        private Integer creditAfterWakeMinutes;
    }

    @Data
    @AllArgsConstructor
    public static class QualificationsDto {
        // the totals of the whole logbook
        private LogbookTotalsDto totals;
        // the windows of the "recent" numbers of the types: landings (hours) and flights (days)
        private int recentLandingsWindowHours;
        private int recentFlightsWindowDays;
        // what the criteria of the types need
        private CriteriaDto criteria;
        // what the experience levels 1 to 5 need
        private List<ExperienceLevelDto> experienceLevels;
        private List<TypeCurrencyDto> types;
    }

    @Data
    @AllArgsConstructor
    public static class ExperienceLevelDto {
        private int level;
        // flights (segments) and hours on the type, both are needed
        private int flights;
        private int hours;
    }

    @Data
    @AllArgsConstructor
    public static class CriteriaDto {
        // landings in the recent window: can carry passengers
        private int recentLandings;
        // night takeoffs and night landings in the recent window: night flying
        private int nightTakeoffs;
        private int nightLandings;
        // flights and hours in the last 12 months: type currency (both are needed)
        private int recentFlights;
        private int recentFlightHours;
        // what is taken off the pay (in percents) when a criterion is not met, see PilotPay: the type currency (the flight pay and the income share), the night
        // flying and the passengers (the flight pay)
        private int typeCurrencyPenaltyPercent;
        private int nightFlyingPenaltyPercent;
        private int canCarryPassengersPenaltyPercent;
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
        // night takeoffs and landings in the recent window
        private int recentNightTakeoffs;
        private int recentNightLandings;
        // night takeoffs and landings, all of them
        private int nightTakeoffs;
        private int nightLandings;
        // flights (segments) and hours in the last 12 months
        private int recentFlights;
        private float recentFlightTime;
        private int flights;
        // hours
        private float flightTime;
        // the criteria: the totals of the type do not take part in them
        private boolean canCarryPassengers;
        private boolean nightFlying;
        private boolean typeCurrent;
        // the experience level on the type, 0 to 5, by the flights and hours of all the logbook on the type
        private int experienceLevel;
        // the base share of the income of a mission (in percents) of this level, before the deductions
        private float baseIncomeSharePercent;
        // the time of the landing of the last flight on the type (world time, seconds), the page sorts the types by it
        private int lastFlightTime;
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
