package net.simforge.airways2.app.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.userstorage.User;
import net.simforge.airways2.app.userstorage.UserStorage;
import net.simforge.airways2.tools.Avatars;
import net.simforge.airways2.tools.TimeTools;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.computations.FlightTimeline;
import net.simforge.airways2.world.computations.PilotDuty;
import net.simforge.airways2.world.computations.PilotPay;
import net.simforge.airways2.world.computations.PilotRest;
import net.simforge.airways2.world.computations.PilotTravel;
import net.simforge.airways2.world.computations.PilotPayParams;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.PilotLogbook;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.BankTransactions;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.processors.PersonControl;
import net.simforge.commons.misc.Geo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
     * The pictures the pilot can choose from, and the one which is shown now. The list is given only to the pilot who asks for it (to choose), the pages
     * do not have it.
     */
    @GetMapping("/avatars")
    public AvatarsDto getAvatars(@RequestAttribute("userId") final int userId) {
        return worldBean.read(world -> new AvatarsDto(Avatars.allowedFiles(), shownAvatarImage(personOf(world, userId))));
    }

    /**
     * The pilot chooses the picture by its file name from the list. A name which is not in the list, or is forbidden, is refused. The answer is the pilot as
     * GET /pilot gives it.
     */
    @PostMapping("/avatar")
    public PilotDto chooseAvatar(@RequestAttribute("userId") final int userId,
                                 @RequestParam("image") final String image) {
        return worldBean.modifySync(world -> {
            final Persons.Person person = personOf(world, userId);
            chooseAvatar(world, person, image);
            return buildPilot(world, person);
        });
    }

    static void chooseAvatar(final World world, final Persons.Person person, final String image) {
        final int avatarId = Avatars.idOfAllowedFile(image)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no such picture"));
        world.personControl().setAvatar(person, avatarId, image);
    }

    /**
     * The pilot types its initials: up to two characters, only English letters (upper and lower case) and digits, 62 characters in all; empty is allowed.
     * What does not fit is refused (400), nothing is cut or changed. The answer is the pilot as GET /pilot gives it.
     */
    @PostMapping("/initials")
    public PilotDto chooseInitials(@RequestAttribute("userId") final int userId,
                                   @RequestParam(name = "initials", required = false, defaultValue = "") final String initials) {
        return worldBean.modifySync(world -> {
            final Persons.Person person = personOf(world, userId);
            chooseInitials(world, person, initials);
            return buildPilot(world, person);
        });
    }

    private static final Pattern INITIALS_PATTERN = Pattern.compile("[A-Za-z0-9]{0,2}");

    static void chooseInitials(final World world, final Persons.Person person, final String initials) {
        if (initials == null || !INITIALS_PATTERN.matcher(initials).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Initials are up to two English letters or digits");
        }
        world.personControl().setInitials(person, initials);
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

    /**
     * The menu of the window of the travel: the ways of the pilot from the place where it is (an airport or a city). From an airport Mach 3 and by ground, from a
     * city by airline (not made yet, it is shown and cannot be chosen) and by ground, and the warp drive from anywhere to anywhere for the pilot who has it: the
     * pilot who has not got the warp drive is not told about it at all.
     */
    @GetMapping("/travel/methods")
    public TravelMethodsDto getTravelMethods(@RequestAttribute("userId") final int userId) {
        return worldBean.read(world -> buildTravelMethods(world, personOf(world, userId)));
    }

    /**
     * The places which the pilot can reach in one click ("commute", it is a trip by ground): from an airport the cities which it serves, from a city the airports
     * which serve it (sorted by the distance).
     */
    @GetMapping("/travel/places")
    public TravelPlacesDto getTravelPlaces(@RequestAttribute("userId") final int userId) {
        return worldBean.read(world -> buildTravelPlaces(world, personOf(world, userId)));
    }

    /**
     * Finds where the pilot wants to go: the text is read as the ICAO code of an airport first, and if there is no such airport, as the name of a city (the
     * cities with that name, then the cities which start with it, then the cities which have it inside). Mach 3 is for airports only, so for it only the code is
     * read. Each place comes with the distance, the time and the money of the way.
     */
    @GetMapping("/travel/find")
    public TravelFindDto findTravelPlaces(@RequestAttribute("userId") final int userId,
                                          @RequestParam("method") final String method,
                                          @RequestParam("query") final String query) {
        return worldBean.read(world -> findTravelPlaces(world, personOf(world, userId), method, query));
    }

    /**
     * The pilot goes to the place (the kind is airport with the ICAO code as the id, or city with the id of the city from the list of the places): the way is paid,
     * the pilot is on the way for its time (or at the place at once). The answer is the pilot as GET /pilot gives it.
     */
    @PostMapping("/travel/go")
    public PilotDto travel(@RequestAttribute("userId") final int userId,
                           @RequestParam("method") final String method,
                           @RequestParam("kind") final String kind,
                           @RequestParam("id") final String id) {
        return worldBean.modifySync(world -> {
            final Persons.Person person = personOf(world, userId);
            startTravel(world, person, method, kind, id);
            return buildPilot(world, person);
        });
    }

    private static PersonControl.Place freePlace(final World world, final Persons.Person person) {
        if (person.getState() != Persons.State.Idle) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a free pilot at an airport or in a city can travel");
        }
        return world.personControl().currentPlace(person)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Only a free pilot at an airport or in a city can travel"));
    }

    private static PilotTravel.Method parseMethod(final String name) {
        return Arrays.stream(PilotTravel.Method.values()).filter(m -> m.name().equals(name)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown way: " + name));
    }

    private static Geo.Coords coordsOf(final World world, final PersonControl.Place place) {
        if (place.type() == Persons.LocationType.Airport) {
            return world.airports().byId(place.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no such airport")).getCoords();
        }
        return world.cities().byId(place.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no such city")).getCoords();
    }

    private static double distanceNm(final World world, final PersonControl.Place from, final PersonControl.Place to) {
        return Geo.distance(coordsOf(world, from), coordsOf(world, to));
    }

    static TravelMethodsDto buildTravelMethods(final World world, final Persons.Person person) {
        final PersonControl.Place here = freePlace(world, person);
        final boolean atAirport = here.type() == Persons.LocationType.Airport;
        final List<TravelMethodDto> methods = new ArrayList<>();
        if (!atAirport) {
            // the journeys go from a city to a city, so the airline is for a city; it is not made yet
            methods.add(new TravelMethodDto(PilotTravel.Method.Airline.name(), PilotTravel.Method.Airline.title(),
                    "Coming soon, from " + PilotTravel.AIRLINE_MIN_DISTANCE_NM + " miles, reasonable prices", false));
        }
        if (atAirport) {
            methods.add(new TravelMethodDto(PilotTravel.Method.Mach3.name(), PilotTravel.Method.Mach3.title(),
                    "Very expensive, only to an airport, from " + PilotTravel.MACH3_MIN_DISTANCE_NM + " miles", true));
        }
        methods.add(new TravelMethodDto(PilotTravel.Method.Ground.name(), PilotTravel.Method.Ground.title(),
                "To anywhere, " + PilotTravel.GROUND_KNOTS + " miles an hour, not more than " + (PilotTravel.GROUND_MAX_MINUTES / 60) + " hours", true));
        if (person.isWarpDrive()) {
            methods.add(new TravelMethodDto(PilotTravel.Method.WarpDrive.name(), PilotTravel.Method.WarpDrive.title(), "To anywhere, at once", true));
        }
        return new TravelMethodsDto(atAirport ? "airport" : "city", here.name(), methods);
    }

    static TravelPlacesDto buildTravelPlaces(final World world, final Persons.Person person) {
        final PersonControl.Place here = freePlace(world, person);
        final List<TravelPlaceDto> places = new ArrayList<>();
        if (here.type() == Persons.LocationType.Airport) {
            world.airport2city().allByAirportId(here.id())
                    .map(link -> world.cities().byId(link.getCityId()).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .forEach(city -> places.add(placeDto(world, person, here, new PersonControl.Place(Persons.LocationType.City, city.getId(), city.getName()),
                            PilotTravel.Method.Ground, null)));
        } else {
            world.airport2city().allByCityId(here.id())
                    .map(link -> world.airports().byId(link.getAirportId()).orElse(null))
                    .filter(a -> a != null && !a.isExcluded())
                    .forEach(airport -> places.add(placeDto(world, person, here, new PersonControl.Place(Persons.LocationType.Airport, airport.getId(), airport.getIcao()),
                            PilotTravel.Method.Ground, airport.getName())));
        }
        places.sort(java.util.Comparator.comparingDouble(TravelPlaceDto::getDistanceNm));
        return new TravelPlacesDto(here.type() == Persons.LocationType.Airport ? "city" : "airport", places);
    }

    // the place with the distance, the time, the money and the reason why the pilot cannot go there this way (null when it can)
    private static TravelPlaceDto placeDto(final World world, final Persons.Person person, final PersonControl.Place from, final PersonControl.Place to,
                                           final PilotTravel.Method method, final String subtitle) {
        final double distance = distanceNm(world, from, to);
        final PilotTravel.Quote quote = PilotTravel.quote(method, distance);
        final String problem = world.personControl().travelProblem(person, method, to, distance);
        final boolean airport = to.type() == Persons.LocationType.Airport;
        return new TravelPlaceDto(airport ? "airport" : "city", airport ? to.name() : Id.encode(to.id()), to.name(), subtitle, distance,
                quote.durationMinutes(), quote.price(), problem == null, problem);
    }

    static TravelFindDto findTravelPlaces(final World world, final Persons.Person person, final String methodName, final String query) {
        final PilotTravel.Method method = parseMethod(methodName);
        final PersonControl.Place here = freePlace(world, person);
        final String text = query == null ? "" : query.trim();
        final List<TravelPlaceDto> matches = new ArrayList<>();
        if (text.isEmpty()) {
            return new TravelFindDto("Type the name of a city or the ICAO code of an airport", matches);
        }

        // the code of an airport first
        final Airports.Airport airport = text.length() == 4 ? world.airports().byIcao(text.toUpperCase()).orElse(null) : null;
        if (airport != null) {
            matches.add(placeDto(world, person, here, new PersonControl.Place(Persons.LocationType.Airport, airport.getId(), airport.getIcao()), method, airport.getName()));
            return new TravelFindDto(null, matches);
        }
        if (method == PilotTravel.Method.Mach3) {
            return new TravelFindDto("Mach 3 flights are between airports: there is no airport with the code " + text.toUpperCase(), matches);
        }

        // then the name of a city: the same name, then the names which start with the text, then the names which have it inside
        final String lower = text.toLowerCase();
        List<Cities.City> found = world.cities().all().filter(c -> c.getName().equalsIgnoreCase(text)).toList();
        if (found.isEmpty()) {
            found = world.cities().all().filter(c -> c.getName().toLowerCase().startsWith(lower)).toList();
        }
        if (found.isEmpty()) {
            found = world.cities().all().filter(c -> c.getName().toLowerCase().contains(lower)).toList();
        }
        found.stream()
                .sorted(java.util.Comparator.comparingInt(Cities.City::getPopulation).reversed())
                .limit(8)
                .forEach(city -> matches.add(placeDto(world, person, here, new PersonControl.Place(Persons.LocationType.City, city.getId(), city.getName()), method,
                        world.countries().byId(city.getCountryId()).map(c -> c.getName()).orElse(null))));
        return new TravelFindDto(matches.isEmpty() ? "There is no airport or city like " + text : null, matches);
    }

    static void startTravel(final World world, final Persons.Person person, final String methodName, final String kind, final String id) {
        final PilotTravel.Method method = parseMethod(methodName);
        final PersonControl.Place here = freePlace(world, person);
        final PersonControl.Place destination;
        if ("airport".equals(kind)) {
            final Airports.Airport airport = findAirport(world, id);
            destination = new PersonControl.Place(Persons.LocationType.Airport, airport.getId(), airport.getIcao());
        } else if ("city".equals(kind)) {
            final Cities.City city;
            try {
                city = world.cities().byId(Id.decode(id)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no such city"));
            } catch (final ResponseStatusException e) {
                throw e;
            } catch (final RuntimeException e) {
                // the text is not an id of a city (Id.decode fails in different ways on a wrong text)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no such city");
            }
            destination = new PersonControl.Place(Persons.LocationType.City, city.getId(), city.getName());
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The kind of the place is airport or city");
        }
        try {
            world.personControl().travel(person, method, destination, distanceNm(world, here, destination));
        } catch (final IllegalStateException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private static Airports.Airport findAirport(final World world, final String icao) {
        final String code = icao == null ? "" : icao.trim().toUpperCase();
        return world.airports().byIcao(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no airport " + code));
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
     * The duty of the pilot in the last 24 hours: the finished flights of the logbook (from the start of the flight to its finish; an entry written before
     * the duty was kept has the time of the blocks with the preflight before and the postflight after) and the flight in progress, which counts from its
     * start up to now. The limit is told with it, the start of a flight over it is refused (FlightDashboardController.pilotLimitChecks).
     */
    static DutyDto buildDuty(final World world, final Persons.Person person) {
        final int now = world.getWorldTime();
        final int windowStart = now - PilotDuty.WINDOW_SECONDS;
        final List<int[]> periods = new ArrayList<>();

        world.pilotLogbook().filter(world.pilotLogbook().byPersonId(person.getId())).forEach(entry -> {
            final int start;
            final int end;
            if (entry.getDutyStartTime() > 0 && entry.getDutyEndTime() > 0) {
                start = entry.getDutyStartTime();
                end = entry.getDutyEndTime();
            } else {
                start = entry.getBlocksOffTime() - FlightTimeline.START_TO_BLOCKS_OFF_MINUTES * Time.ONE_MINUTE;
                end = entry.getBlocksOnTime() + FlightTimeline.POSTFLIGHT_MINUTES * Time.ONE_MINUTE;
            }
            if (end > windowStart) {
                periods.add(new int[]{start, end});
            }
        });

        boolean onDuty = false;
        if (person.getState() == Persons.State.OnDuty) {
            onDuty = true;
            int start = person.getDutyStartTime();
            if (start <= 0) {
                // the flight was started before the start was kept: the preflight starts before the planned blocks off
                start = world.flightMissions().allByUserId(person.getUserId())
                        .filter(fm -> switch (fm.getStatus()) {
                            case Preflight, Departure, Flying, Arrival, Postflight -> true;
                            default -> false;
                        })
                        .mapToInt(fm -> fm.getPlannedDepartureWorldTime() - FlightTimeline.START_TO_BLOCKS_OFF_MINUTES * Time.ONE_MINUTE)
                        .min().orElse(now);
            }
            periods.add(new int[]{Math.min(start, now), now});
        }

        return new DutyDto(PilotDuty.dutyMinutesInWindow(periods, now), PilotDuty.LIMIT_MINUTES, onDuty);
    }

    /**
     * The flight time of the pilot in the last 30 and 365 days (sliding windows): the flights of the logbook from blocks off to blocks on, and the flight in
     * progress from its blocks off up to now. The limits are told with it, the start of a flight over them is refused (FlightDashboardController.pilotLimitChecks).
     */
    static FlightTimeDto buildFlightTime(final World world, final Persons.Person person) {
        final int now = world.getWorldTime();
        final int windowStart = now - PilotDuty.WINDOW_365_DAYS_SECONDS;
        final List<int[]> periods = new ArrayList<>();

        world.pilotLogbook().filter(world.pilotLogbook().byPersonId(person.getId())).forEach(entry -> {
            if (entry.getBlocksOnTime() > windowStart) {
                periods.add(new int[]{entry.getBlocksOffTime(), entry.getBlocksOnTime()});
            }
        });

        // the flight in progress: from the actual blocks off (zero until it happens) up to now or to the actual blocks on
        if (person.getState() == Persons.State.OnDuty) {
            world.flightMissions().allByUserId(person.getUserId())
                    .filter(fm -> switch (fm.getStatus()) {
                        case Departure, Flying, Arrival, Postflight -> true;
                        default -> false;
                    })
                    .filter(fm -> fm.getActualDepartureWorldTime() > 0)
                    .forEach(fm -> periods.add(new int[]{fm.getActualDepartureWorldTime(),
                            fm.getActualArrivalWorldTime() > 0 ? fm.getActualArrivalWorldTime() : now}));
        }

        return new FlightTimeDto(
                PilotDuty.minutesInWindow(periods, now, PilotDuty.WINDOW_30_DAYS_SECONDS), PilotDuty.LIMIT_30_DAYS_MINUTES,
                PilotDuty.minutesInWindow(periods, now, PilotDuty.WINDOW_365_DAYS_SECONDS), PilotDuty.LIMIT_365_DAYS_MINUTES);
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
            final int after = PilotRest.creditAfterSleep(stored, sleepMinutes);
            return new AwakeDto(stored, true, sleepMinutes, person.getCreditTime(), PilotRest.expectedAfterWakeUp(after), PilotRest.maxAfterWakeUp(after));
        }
        final int current = started ? PilotRest.creditNow(stored, person.getCreditTime(), now) : stored;
        return new AwakeDto(current, false, 0, null, null, null);
    }

    static PilotDto buildPilot(final World world, final Persons.Person person) {
        String airportIcao = null;
        String airportName = null;
        String cityName = null;
        String destinationIcao = null;
        String destinationCity = null;
        // a person who is Travelling has the place of the arrival as its location: that is not where the pilot is, it is where the pilot is going
        final boolean travelling = person.getState() == Persons.State.Travelling;
        switch (person.getLocationType()) {
            case Airport -> {
                if (travelling) {
                    destinationIcao = world.airports().getIcao(person.getLocationId()).orElse(null);
                } else {
                    airportIcao = world.airports().getIcao(person.getLocationId()).orElse(null);
                    airportName = world.airports().byId(person.getLocationId()).map(a -> a.getName()).orElse(null);
                }
            }
            case City -> {
                final String name = world.cities().byId(person.getLocationId()).map(c -> c.getName()).orElse(null);
                if (travelling) {
                    destinationCity = name;
                } else {
                    cityName = name;
                }
            }
            default -> { }
        }

        // the money of the pilot: the balance of the bank account the person refers to, null while the person has no account
        final Long bankBalance = person.getBankAccountId() > 0
                ? world.bankAccounts().byId(person.getBankAccountId()).map(BankAccounts.BankAccount::getBalance).orElse(null)
                : null;

        return new PilotDto(person.getInitials(), shownAvatarImage(person), person.getState().name(), person.getLocationType().name(), airportIcao, airportName, cityName, bankBalance,
                buildAwake(world, person), buildDuty(world, person), buildFlightTime(world, person), destinationIcao, destinationCity,
                world.personControl().bookingAirports(person).stream().map(Airports.Airport::getIcao).toList(),
                new SettingsDto(person.isAutoFinish(), person.isWarpDrive() ? Boolean.TRUE : null));
    }

    /**
     * The last operations of the bank account of the pilot, the latest first: the time (UTC, to the minute), what for, the amount (minus when the pilot paid,
     * plus when the pilot got the money) and the balance after the operation.
     */
    @GetMapping("/bank")
    public BankDto getBank(@RequestAttribute("userId") final int userId) {
        return worldBean.read(world -> buildBank(world, personOf(world, userId)));
    }

    static final int BANK_OPERATIONS = 10;

    static BankDto buildBank(final World world, final Persons.Person person) {
        final int accountId = person.getBankAccountId();
        final BankAccounts.BankAccount account = accountId > 0 ? world.bankAccounts().byId(accountId).orElse(null) : null;
        if (account == null) {
            return new BankDto(null, List.of());
        }
        // the balance after an operation is not stored: the latest operation ends with the balance of now, every earlier one with the balance before the next one
        final List<BankTransactions.BankTransaction> latest = world.bankTransactions().filter(world.bankTransactions().byAccountId(accountId))
                .filter(t -> t.getType() != BankTransactions.Type.BalanceCheckpoint)
                .sorted(Comparator.comparingInt(BankTransactions.BankTransaction::getId).reversed())
                .limit(BANK_OPERATIONS)
                .toList();
        final List<BankOperationDto> operations = new ArrayList<>();
        long balance = account.getBalance();
        for (final BankTransactions.BankTransaction t : latest) {
            final long amount = t.getToAccountId() == accountId ? t.getAmount() : -t.getAmount();
            operations.add(new BankOperationDto(Time.toLdt(t.getTime()).truncatedTo(ChronoUnit.MINUTES).toString(), bankOperationTitle(t.getType()), amount, balance));
            balance -= amount;
        }
        return new BankDto(account.getBalance(), operations);
    }

    private static String bankOperationTitle(final BankTransactions.Type type) {
        return switch (type) {
            case Travel -> "Travel";
            case PilotMeals -> "Meals";
            case PilotLodging -> "Night";
            case PilotFlightPay -> "Flight pay";
            case PilotIncomeShare -> "Income share";
            case JourneyPayment -> "Journey payment";
            case BalanceCheckpoint -> "Balance check";
        };
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

    /**
     * The picture the pages show for the person: the file name of the icon (the pages put the address of the icons before it), never the list of the
     * pictures. While the person has no avatar (avatarId 0, it is not changed) or the picture is forbidden, it is one of two pilots by the parity of the
     * id of the person, see Avatars.
     */
    static String shownAvatarImage(final Persons.Person person) {
        return Avatars.shownFile(person.getId(), person.getAvatarId());
    }

    private static String avatarImageOf(final World world, final int personId) {
        return world.persons().byId(personId).map(PilotController::shownAvatarImage).orElse(null);
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
                        a.lastFlightTime))
                .toList();

        return new QualificationsDto(avatarImageOf(world, personId), buildLogbook(world, personId).getTotals(), RECENT_LANDINGS_WINDOW_HOURS, RECENT_FLIGHTS_WINDOW_DAYS,
                new CriteriaDto(CRITERIA_RECENT_LANDINGS, CRITERIA_NIGHT_TAKEOFFS, CRITERIA_NIGHT_LANDINGS, CRITERIA_RECENT_FLIGHTS,
                        CRITERIA_RECENT_FLIGHT_HOURS),
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

        return new LogbookDto(avatarImageOf(world, personId), new LogbookTotalsDto(entries.size(), round1(flightTime), round1(nightTime), round1(ifrTime),
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
        // the picture of the pilot: the file name of the icon, for instance user_pilot.png (a person without an avatar gets one of two pilots by the id of the person)
        private String avatarImage;
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
        private DutyDto duty;
        private FlightTimeDto flightTime;
        // the airport the pilot is travelling to (by itself, see PilotTravel), null when the pilot is not on such a way
        private String destinationIcao;
        // the city the pilot is travelling to from an airport (by ground), null when the pilot is not on such a way
        private String destinationCity;
        // the airports where the pilot may book an aircraft: the airport of the pilot, or the airports linked to its city; empty when the pilot is on the way or nowhere
        private List<String> bookingAirports;
        // the settings of the pilot (the page of the pilot shows them, the user cannot change them yet)
        private SettingsDto settings;
    }

    @Data
    @AllArgsConstructor
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public static class SettingsDto {
        // a flight of the pilot is finished by itself when it has come to its end
        private boolean autoFinish;
        // true when the pilot has the warp drive; for the others there is no such field at all
        private Boolean warpDrive;
    }

    @Data
    @AllArgsConstructor
    public static class FlightTimeDto {
        // minutes of flight time (blocks off to blocks on) in the last 30 days and the most of it
        private int last30DaysMinutes;
        private int limit30DaysMinutes;
        // the same for the last 365 days
        private int last365DaysMinutes;
        private int limit365DaysMinutes;
    }

    @Data
    @AllArgsConstructor
    public static class DutyDto {
        // minutes of duty in the last 24 hours (the sliding window, not the calendar day)
        private int last24HoursMinutes;
        // the most of duty in 24 hours, minutes
        private int limitMinutes;
        // true if a flight is in progress and its time up to now is in the number
        private boolean onDuty;
    }

    @Data
    @AllArgsConstructor
    public static class AvatarsDto {
        // the file names of the pictures which can be chosen, in the order of the list
        private List<String> images;
        // the picture which is shown now
        private String current;
    }

    @Data
    @AllArgsConstructor
    public static class AwakeDto {
        // minutes the pilot can stay awake and active; negative when overtired; while the pilot sleeps it is the credit at the moment of falling asleep
        private int creditMinutes;
        private boolean sleeping;
        // minutes of the sleep so far, zero when awake
        private int sleepMinutes;
        // when the pilot fell asleep, epoch seconds (UTC), null when awake
        private Integer sleepingSince;
        // the credit if the pilot wakes up now (it is more than creditMinutes only after two hours of sleep), null when awake
        private Integer creditAfterWakeMinutes;
        // the most the credit can be after the wake up (the random cap, 22 hours at most), null when awake
        private Integer creditAfterWakeMaxMinutes;
    }

    @Data
    @AllArgsConstructor
    public static class TravelMethodsDto {
        // where the pilot is: airport or city
        private String location;
        private String placeName;
        private List<TravelMethodDto> methods;
    }

    @Data
    @AllArgsConstructor
    public static class TravelMethodDto {
        // what to send as the method
        private String method;
        private String title;
        // the line under the title, what the way is
        private String note;
        // false: the way is shown, but cannot be chosen (not made yet)
        private boolean available;
    }

    @Data
    @AllArgsConstructor
    public static class TravelFindDto {
        // why there is nothing to show (or what to type), null when there are places
        private String message;
        private List<TravelPlaceDto> matches;
    }

    @Data
    @AllArgsConstructor
    public static class TravelPlacesDto {
        // "city": the places are the cities of the airport where the pilot is; "airport": the airports of the city where the pilot is
        private String kind;
        private List<TravelPlaceDto> places;
    }

    @Data
    @AllArgsConstructor
    public static class TravelPlaceDto {
        // airport or city
        private String kind;
        // what to send to POST /pilot/travel/go: the ICAO of an airport or the id of a city
        private String id;
        private String title;
        // the name of the airport, null for a city
        private String subtitle;
        private double distanceNm;
        private int durationMinutes;
        private long price;
        private boolean available;
        // why not, null when available
        private String problem;
    }

    @Data
    @AllArgsConstructor
    public static class QualificationsDto {
        // the picture of the pilot for the header of the page, see PilotDto
        private String avatarImage;
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
        // the time of the landing of the last flight on the type (world time, seconds), the page sorts the types by it
        private int lastFlightTime;
    }

    @Data
    @AllArgsConstructor
    public static class BankDto {
        // the balance now, null while the pilot has no account
        private Long balance;
        private List<BankOperationDto> operations;
    }

    @Data
    @AllArgsConstructor
    public static class BankOperationDto {
        // UTC to the minute, 2026-10-27T08:35
        private String time;
        private String type;
        // minus when the pilot paid
        private long amount;
        // the balance of the account after the operation
        private long balance;
    }

    @Data
    @AllArgsConstructor
    public static class LogbookDto {
        // the picture of the pilot for the header of the page, see PilotDto
        private String avatarImage;
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
