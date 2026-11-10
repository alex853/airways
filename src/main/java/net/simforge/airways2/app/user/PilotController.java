package net.simforge.airways2.app.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.pblc.CalendarKeys;
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
import net.simforge.airways2.world.datamodel.EventLog;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;
import net.simforge.airways2.world.processors.AirlineTravelEstimate;
import net.simforge.airways2.world.processors.FlightMissionHelper;
import net.simforge.airways2.world.processors.RosterForecast;
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
import java.util.Optional;
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
     * How the duty time is made: the flights and the gaps on a ribbon of 24 hours (Details of the duty tile).
     */
    @GetMapping("/duty-details")
    public DutyDetailsDto getDutyDetails(@RequestAttribute("userId") final int userId) {
        return worldBean.read(world -> buildDutyDetails(world, personOf(world, userId)));
    }

    /**
     * The history of the places of the pilot (History of the Location tile): the travels it has started and where it has arrived, the newest first.
     */
    @GetMapping("/location-history")
    public List<LocationEventDto> getLocationHistory(@RequestAttribute("userId") final int userId) {
        return worldBean.read(world -> buildLocationHistory(world, personOf(world, userId)));
    }

    /**
     * The path of the calendar link of the pilot (docs/calendar.md): the page adds the address of the server and gives it to the pilot to subscribe to the flights in a
     * calendar. 503 while the admin has not generated the secret of the server.
     */
    @GetMapping("/calendar-link")
    public Map<String, String> getCalendarLink(@RequestAttribute("userId") final int userId) {
        final String slug = CalendarKeys.getInstance().slugOf(userId);
        if (slug == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "the calendar is not set up yet");
        }
        return Map.of("path", "/calendar/" + slug + ".ics");
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
            // the journeys go from a city to a city, so the airline is for a city; the window shows the estimate (the flights, the price, the time), the start is not open yet
            methods.add(new TravelMethodDto(PilotTravel.Method.Airline.name(), PilotTravel.Method.Airline.title(),
                    "From " + PilotTravel.AIRLINE_MIN_DISTANCE_NM + " miles, reasonable prices: the estimate of the flights, the price and the time (the start is not open yet)", true));
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
        return placeDto(world, person, from, to, method, subtitle, null);
    }

    // the estimator is for the airline only (it reads the frequencies of the flights once for all the places of the answer)
    private static TravelPlaceDto placeDto(final World world, final Persons.Person person, final PersonControl.Place from, final PersonControl.Place to,
                                           final PilotTravel.Method method, final String subtitle, final AirlineTravelEstimate estimator) {
        final double distance = distanceNm(world, from, to);
        if (method == PilotTravel.Method.Airline) {
            return airlinePlaceDto(world, person, from, to, subtitle, distance, estimator != null ? estimator : new AirlineTravelEstimate(world));
        }
        final PilotTravel.Quote quote = PilotTravel.quote(method, distance);
        final String problem = world.personControl().travelProblem(person, method, to, distance);
        final boolean airport = to.type() == Persons.LocationType.Airport;
        return new TravelPlaceDto(airport ? "airport" : "city", airport ? to.name() : Id.encode(to.id()), to.name(), subtitle, distance,
                quote.durationMinutes(), quote.price(), problem == null, problem, null);
    }

    // The estimate of a travel by airline to the city (nothing is booked): the flights, the price, the days. The pilot can start it when there is no problem: the problem tells why it
    // cannot be done at all (not a city, too close, no airports in the city); no flights yet is a note, not a problem.
    private static TravelPlaceDto airlinePlaceDto(final World world, final Persons.Person person, final PersonControl.Place from, final PersonControl.Place to, final String subtitle,
                                                  final double distance, final AirlineTravelEstimate estimator) {
        if (from.type() != Persons.LocationType.City || to.type() != Persons.LocationType.City) {
            return new TravelPlaceDto(to.type() == Persons.LocationType.Airport ? "airport" : "city", to.type() == Persons.LocationType.Airport ? to.name() : Id.encode(to.id()), to.name(),
                    subtitle, distance, 0, 0, false, "The airline goes from a city to a city", null);
        }
        // the variants are sorted by the frequency of the route, the first one is the most frequent: the fields of the estimate and the money are of it
        final List<AirlineTravelEstimate.Result> variants = estimator.variants(from.id(), to.id());
        final AirlineTravelEstimate.Result estimate = variants.get(0);
        // a route which is not found yet does not forbid the travel (the journey keeps looking for flights): it is a note of the estimate, not the problem of the place
        final String problem = estimate.blocking() ? estimate.problem() : null;
        final String note = estimate.blocking() ? null : estimate.problem();
        final List<AirlineLegDto> legs = legsOf(estimate);
        final List<AirlineRouteDto> routes = new ArrayList<>();
        if (estimate.found()) {
            for (int i = 0; i < variants.size(); i++) {
                final AirlineTravelEstimate.Result variant = variants.get(i);
                routes.add(new AirlineRouteDto(variant.title(), i == 0, legsOf(variant), variant.price(), variant.minDays(), variant.maxDays()));
            }
        }
        final long balance = world.bankAccounts().byId(person.getBankAccountId()).map(BankAccounts.BankAccount::getBalance).orElse(0L);
        // there is no reserve and no check of the money (decided 2026-11-10): the real costs may be more than the estimate, the page only advises to have twice the tickets
        final AirlineEstimateDto airline = new AirlineEstimateDto(estimate.found(), legs, estimate.price(), balance,
                estimate.minDays(), estimate.maxDays(), routes, note);
        return new TravelPlaceDto("city", Id.encode(to.id()), to.name(), subtitle, distance, estimate.minDays() * 24 * 60, estimate.price(), problem == null, problem, airline);
    }

    private static List<AirlineLegDto> legsOf(final AirlineTravelEstimate.Result result) {
        return result.legs().stream()
                .map(leg -> new AirlineLegDto(leg.fromIcao(), leg.toIcao(), leg.distanceNm(), leg.flightsPerWeek(), leg.price()))
                .toList();
    }

    static TravelFindDto findTravelPlaces(final World world, final Persons.Person person, final String methodName, final String query) {
        final PilotTravel.Method method = parseMethod(methodName);
        final PersonControl.Place here = freePlace(world, person);
        final String text = query == null ? "" : query.trim();
        final List<TravelPlaceDto> matches = new ArrayList<>();
        if (text.isEmpty()) {
            return new TravelFindDto("Type the name of a city or the ICAO code of an airport", matches);
        }

        if (method == PilotTravel.Method.Airline) {
            return findAirlineCities(world, person, here, text);
        }

        // the code of an airport first
        final Airports.Airport airport = text.length() == 4 ? world.airports().byIcao(text.toUpperCase()).filter(a -> !a.isExcluded()).orElse(null) : null;
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

    // by airline the places are the cities only (the journeys go from a city to a city): the same search by the name as for the other ways, one estimator for all the cities found
    private static TravelFindDto findAirlineCities(final World world, final Persons.Person person, final PersonControl.Place here, final String text) {
        final String lower = text.toLowerCase();
        List<Cities.City> found = world.cities().all().filter(c -> c.getName().equalsIgnoreCase(text)).toList();
        if (found.isEmpty()) {
            found = world.cities().all().filter(c -> c.getName().toLowerCase().startsWith(lower)).toList();
        }
        if (found.isEmpty()) {
            found = world.cities().all().filter(c -> c.getName().toLowerCase().contains(lower)).toList();
        }
        final AirlineTravelEstimate estimator = new AirlineTravelEstimate(world);
        final List<TravelPlaceDto> matches = new ArrayList<>();
        found.stream()
                .sorted(java.util.Comparator.comparingInt(Cities.City::getPopulation).reversed())
                .limit(8)
                .forEach(city -> matches.add(placeDto(world, person, here, new PersonControl.Place(Persons.LocationType.City, city.getId(), city.getName()), PilotTravel.Method.Airline,
                        world.countries().byId(city.getCountryId()).map(c -> c.getName()).orElse(null), estimator)));
        if (matches.isEmpty()) {
            return new TravelFindDto(text.length() == 4 && world.airports().byIcao(text.toUpperCase()).isPresent()
                    ? "The airline goes from a city to a city: type the name of a city, not the code of an airport"
                    : "There is no city like " + text, matches);
        }
        return new TravelFindDto(null, matches);
    }

    static void startTravel(final World world, final Persons.Person person, final String methodName, final String kind, final String id) {
        final PilotTravel.Method method = parseMethod(methodName);
        final PersonControl.Place here = freePlace(world, person);
        if (method == PilotTravel.Method.Airline) {
            startAirlineTravel(world, person, here, kind, id);
            return;
        }
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

    // The travel by airline: from a city to a city, the journey of the pilot looks for the flights (PersonControl.startAirlineTravel). What cannot change by itself is refused here with the
    // reason of the estimate (no such city, the same city, too close, no airports in the city); no flights yet is not a reason.
    private static void startAirlineTravel(final World world, final Persons.Person person, final PersonControl.Place here, final String kind, final String id) {
        if (!"city".equals(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The airline goes to a city");
        }
        if (here.type() != Persons.LocationType.City) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The airline goes from a city to a city");
        }
        final int toCityId;
        try {
            toCityId = Id.decode(id);
        } catch (final RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no such city");
        }
        final AirlineTravelEstimate.Result estimate = new AirlineTravelEstimate(world).estimate(here.id(), toCityId);
        if (estimate.blocking()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, estimate.problem());
        }
        try {
            world.personControl().startAirlineTravel(person, toCityId);
        } catch (final IllegalStateException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private static Airports.Airport findAirport(final World world, final String icao) {
        final String code = icao == null ? "" : icao.trim().toUpperCase();
        return world.airports().byIcao(code)
                .filter(airport -> !airport.isExcluded())
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
        final List<DutyPeriod> found = dutyPeriods(world, person, now, now - PilotDuty.WINDOW_SECONDS);
        final List<int[]> periods = found.stream().map(p -> new int[]{p.start(), p.end()}).toList();
        return new DutyDto(PilotDuty.dutyMinutesInWindow(periods, now), PilotDuty.LIMIT_MINUTES, person.getState() == Persons.State.OnDuty);
    }

    /**
     * A period of duty (world time): from Start flight to Finish flight; route is null for a flight in progress which is not known.
     */
    private record DutyPeriod(int start, int end, String route) {
    }

    /**
     * The periods of the duty of the pilot which end after {@code from}: the finished flights of the logbook (with the kept duty, or the blocks with the preflight and
     * the postflight for an entry written before it was kept) and the flight in progress from its start up to now.
     */
    private static List<DutyPeriod> dutyPeriods(final World world, final Persons.Person person, final int now, final int from) {
        final List<DutyPeriod> periods = new ArrayList<>();

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
            if (end > from) {
                periods.add(new DutyPeriod(start, end, world.airports().getIcao(entry.getFromAirportId()).orElse("?")
                        + " - " + world.airports().getIcao(entry.getToAirportId()).orElse("?")));
            }
        });

        if (person.getState() == Persons.State.OnDuty) {
            final Optional<FlightMissions.Mission> flight = world.flightMissions().allByUserId(person.getUserId())
                    .filter(fm -> switch (fm.getStatus()) {
                        case Preflight, Departure, Flying, Arrival, Postflight -> true;
                        default -> false;
                    })
                    .min(Comparator.comparingInt(FlightMissions.Mission::getPlannedDepartureWorldTime));
            int start = person.getDutyStartTime();
            if (start <= 0) {
                // the flight was started before the start was kept: the preflight starts before the planned blocks off
                start = flight.map(fm -> fm.getPlannedDepartureWorldTime() - FlightTimeline.START_TO_BLOCKS_OFF_MINUTES * Time.ONE_MINUTE).orElse(now);
            }
            periods.add(new DutyPeriod(Math.min(start, now), now, flight.map(fm -> FlightMissionHelper.formatRoute(world, fm.getId())).orElse(null)));
        }
        return periods;
    }

    // how far after now the planned flights are shown on the ribbon of the duty (the right "ear")
    static final int DUTY_DETAILS_AHEAD_SECONDS = Time.ONE_HOUR;
    // how far before the window of 24 hours the flights are told (the left "ear")
    static final int DUTY_DETAILS_BEHIND_SECONDS = Time.ONE_HOUR;

    /**
     * How the duty of the last 24 hours is made (the Details of the tile): the periods of duty (flights from Start flight to Finish flight), the gaps between them which
     * count as duty too (shorter than PilotDuty.CHAIN_GAP_SECONDS), and the planned flights of the next hour. Times are UTC milliseconds, the pieces are not cut at the window:
     * minutesInWindow tells how much of a piece is in the last 24 hours. The numbers are the same as in the tile.
     */
    static DutyDetailsDto buildDutyDetails(final World world, final Persons.Person person) {
        final int now = world.getWorldTime();
        final int windowStart = now - PilotDuty.WINDOW_SECONDS;
        final List<DutyPeriod> periods = new ArrayList<>(dutyPeriods(world, person, now, windowStart - DUTY_DETAILS_BEHIND_SECONDS));
        periods.sort(Comparator.comparingInt(DutyPeriod::start));

        final List<DutySegmentDto> segments = new ArrayList<>();
        long unionSeconds = 0;
        int reached = 0;
        final List<int[]> inWindow = new ArrayList<>();
        for (final DutyPeriod period : periods) {
            if (period.end() <= windowStart) {
                // over before the window (the left ear of the ribbon): shown, but it is not counted and does not join the flights of the window, as in the tile
                segments.add(segment("flight", period.start(), period.end(), period.route(), now, windowStart));
                continue;
            }
            inWindow.add(new int[]{period.start(), period.end()});
            if (reached != 0 && period.start() > reached && period.start() - reached < PilotDuty.CHAIN_GAP_SECONDS) {
                // the pilot did not go off duty between the flights: the gap is duty
                segments.add(segment("gap", reached, period.start(), null, now, windowStart));
            }
            segments.add(segment("flight", period.start(), period.end(), period.route(), now, windowStart));
            final int from = Math.max(Math.max(period.start(), reached), windowStart);
            final int to = Math.min(period.end(), now);
            if (to > from) {
                unionSeconds += to - from;
            }
            reached = Math.max(reached, period.end());
        }

        final int totalMinutes = PilotDuty.dutyMinutesInWindow(inWindow, now);
        final int flightsMinutes = (int) Math.min(unionSeconds / 60, totalMinutes);

        // the flights of the roster which have not started: the preflight of a flight begins before its departure, the postflight ends after its arrival
        final List<DutySegmentDto> planned = new ArrayList<>();
        for (final RosterForecast.Entry entry : RosterForecast.of(world, person.getUserId())) {
            if (entry.expectedDeparture() == 0) {
                continue;
            }
            // a flight which is started (Start flight is done, the pilot is on duty, the preflight goes on) is the flight in progress of the periods above (up to now), it is not a
            // planned flight: only the rest of it is shown, from now to the arrival and the postflight
            final boolean started = switch (entry.mission().getStatus()) {
                case Preflight, Departure, Flying, Arrival, Postflight -> true;
                default -> false;
            };
            if (!started && entry.mission().getActualDepartureWorldTime() != 0) {
                continue;
            }
            final int start = started ? now : entry.expectedDeparture() - FlightTimeline.START_TO_BLOCKS_OFF_MINUTES * Time.ONE_MINUTE;
            final int end = entry.expectedArrival() + FlightTimeline.POSTFLIGHT_MINUTES * Time.ONE_MINUTE;
            if (end > now && start <= now + DUTY_DETAILS_AHEAD_SECONDS) {
                planned.add(new DutySegmentDto("planned", start * 1000L, end * 1000L, FlightMissionHelper.formatRoute(world, entry.mission().getId()), 0));
            }
        }

        return new DutyDetailsDto(now * 1000L, windowStart * 1000L, totalMinutes, PilotDuty.LIMIT_MINUTES, flightsMinutes, Math.max(0, totalMinutes - flightsMinutes),
                PilotDuty.CHAIN_GAP_SECONDS / 60, FlightTimeline.START_TO_BLOCKS_OFF_MINUTES, FlightTimeline.POSTFLIGHT_MINUTES,
                person.getState() == Persons.State.OnDuty, segments, planned);
    }

    // how many events the history of the places tells
    static final int LOCATION_HISTORY_LIMIT = 50;

    /**
     * The events of the travel of the pilot from the event log (PersonControl.travel and arrive, object1 is the person), the newest first, up to LOCATION_HISTORY_LIMIT. The text
     * is made here, the page only shows it.
     */
    public static List<LocationEventDto> buildLocationHistory(final World world, final Persons.Person person) {
        final int personCode = EventLog.ObjectType.Person.code();
        final List<EventLog.Event> events = world.eventLog().byObject(personCode, person.getId())
                .filter(e -> e.getObject1TypeRaw() == personCode && e.getObject1Id() == person.getId())
                .filter(e -> switch (e.getType() != null ? e.getType() : EventLog.EventType.PersonBecameIdle) {
                    case PersonTravelStarted, PersonArrivedToCity, PersonArrivedToAirport, PersonFlewFlight,
                         PersonSearchStarted, PersonFoundFlight, PersonCouldNotAffordFlight, PersonDeboarded, PersonPaidTicket, PersonFlightFailed,
                         PersonCheckedIn, PersonBoarded, PersonWaitingNextFlight -> true;
                    default -> false;
                })
                .sorted(Comparator.comparingInt(EventLog.Event::getTime).thenComparingInt(EventLog.Event::getId).reversed())
                .limit(LOCATION_HISTORY_LIMIT)
                .toList();

        final List<LocationEventDto> history = new ArrayList<>();
        for (final EventLog.Event event : events) {
            switch (event.getType()) {
                case PersonTravelStarted -> {
                    final PilotTravel.Method method = PilotTravel.Method.byCode(event.getObject2Id());
                    final String from = event.getObject4TypeRaw() != 0 ? eventPlaceName(world, event.getObject4TypeRaw(), event.getObject4Id()) : null;
                    history.add(new LocationEventDto(event.getTime() * 1000L, "started",
                            "Started travelling " + travelWay(method) + (from != null ? " from " + from : "") + " to " + eventPlaceName(world, event.getObject3TypeRaw(), event.getObject3Id())));
                }
                case PersonArrivedToCity -> history.add(new LocationEventDto(event.getTime() * 1000L, "arrived", "Arrived in " + eventPlaceName(world, event.getObject2TypeRaw(), event.getObject2Id())));
                case PersonArrivedToAirport -> history.add(new LocationEventDto(event.getTime() * 1000L, "arrived", "Arrived at " + eventPlaceName(world, event.getObject2TypeRaw(), event.getObject2Id())));
                case PersonFlewFlight -> history.add(new LocationEventDto(event.getTime() * 1000L, "flew",
                        "Flew from " + eventPlaceName(world, event.getObject2TypeRaw(), event.getObject2Id()) + " to " + eventPlaceName(world, event.getObject3TypeRaw(), event.getObject3Id())));
                case PersonSearchStarted -> history.add(new LocationEventDto(event.getTime() * 1000L, "search",
                        "Looking for a flight from " + eventPlaceName(world, event.getObject2TypeRaw(), event.getObject2Id()) + " towards " + eventPlaceName(world, event.getObject3TypeRaw(), event.getObject3Id())));
                case PersonFoundFlight -> history.add(new LocationEventDto(event.getTime() * 1000L, "found",
                        "Found a flight " + eventPlaceName(world, event.getObject2TypeRaw(), event.getObject2Id()) + " to " + eventPlaceName(world, event.getObject3TypeRaw(), event.getObject3Id())
                                + ", the ticket is " + event.getObject4Id()));
                case PersonCouldNotAffordFlight -> history.add(new LocationEventDto(event.getTime() * 1000L, "money",
                        "Could not book a flight " + eventPlaceName(world, event.getObject2TypeRaw(), event.getObject2Id()) + " to " + eventPlaceName(world, event.getObject3TypeRaw(), event.getObject3Id())
                                + ": not enough money for the ticket, " + event.getObject4Id()));
                case PersonDeboarded -> history.add(new LocationEventDto(event.getTime() * 1000L, "deboarded", "Got off the flight at " + eventPlaceName(world, event.getObject2TypeRaw(), event.getObject2Id())));
                case PersonPaidTicket -> history.add(new LocationEventDto(event.getTime() * 1000L, "paid",
                        event.getObject2Id() == event.getObject3Id() ? "Paid " + event.getObject2Id() + " for the ticket"
                                : "Paid " + event.getObject2Id() + " of " + event.getObject3Id() + " for the ticket (the account had no more)"));
                case PersonCheckedIn -> history.add(new LocationEventDto(event.getTime() * 1000L, "checkin",
                        "Checked in for the flight " + eventPlaceName(world, event.getObject2TypeRaw(), event.getObject2Id()) + " to " + eventPlaceName(world, event.getObject3TypeRaw(), event.getObject3Id())));
                case PersonBoarded -> history.add(new LocationEventDto(event.getTime() * 1000L, "boarded",
                        "Boarded the flight " + eventPlaceName(world, event.getObject2TypeRaw(), event.getObject2Id()) + " to " + eventPlaceName(world, event.getObject3TypeRaw(), event.getObject3Id())));
                case PersonWaitingNextFlight -> history.add(new LocationEventDto(event.getTime() * 1000L, "waiting",
                        "Waiting at " + eventPlaceName(world, event.getObject2TypeRaw(), event.getObject2Id()) + " for the next flight to " + eventPlaceName(world, event.getObject3TypeRaw(), event.getObject3Id())));
                case PersonFlightFailed -> history.add(new LocationEventDto(event.getTime() * 1000L, "failed",
                        "The flight " + eventPlaceName(world, event.getObject2TypeRaw(), event.getObject2Id()) + " to " + eventPlaceName(world, event.getObject3TypeRaw(), event.getObject3Id()) + " did not take place"));
                default -> { }
            }
        }
        return history;
    }

    private static String travelWay(final PilotTravel.Method method) {
        if (method == null) {
            return "";
        }
        return switch (method) {
            case WarpDrive -> "by warp drive";
            case Mach3 -> "at Mach 3";
            case Airline -> "by airline";
            case Ground -> "by ground";
        };
    }

    // the name of an airport (its ICAO code) or a city of an event of the log, "?" if it is not there any more
    private static String eventPlaceName(final World world, final int objectTypeRaw, final int id) {
        if (objectTypeRaw == EventLog.ObjectType.Airport.code()) {
            return world.airports().getIcao(id).orElse("?");
        }
        if (objectTypeRaw == EventLog.ObjectType.City.code()) {
            return world.cities().byId(id).map(Cities.City::getName).orElse("?");
        }
        return "?";
    }

    /**
     * One line of the history of the places: time is UTC milliseconds, type is started or arrived.
     */
    @Data
    @AllArgsConstructor
    public static class LocationEventDto {
        private long time;
        private String type;
        private String text;
    }

    private static DutySegmentDto segment(final String type, final int start, final int end, final String route, final int now, final int windowStart) {
        final int from = Math.max(start, windowStart);
        final int to = Math.min(end, now);
        return new DutySegmentDto(type, start * 1000L, end * 1000L, route, to > from ? (to - from) / 60 : 0);
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

        // The pilot who travels by airline: where it is now is told by its journey (the city where it waits, or the flight it is on), not by the place of the arrival
        final AirlineTravelDto airlineTravel = buildAirlineTravel(world, person);
        if (airlineTravel != null) {
            airportIcao = null;
            airportName = null;
            destinationIcao = null;
            destinationCity = null;
            cityName = airlineTravel.getCityNow();
            if (cityName == null) {
                destinationIcao = airlineTravel.getFlightTo();
            }
        }

        // the money of the pilot: the balance of the bank account the person refers to, null while the person has no account
        final Long bankBalance = person.getBankAccountId() > 0
                ? world.bankAccounts().byId(person.getBankAccountId()).map(BankAccounts.BankAccount::getBalance).orElse(null)
                : null;

        return new PilotDto(person.getInitials(), shownAvatarImage(person), person.getState().name(), person.getLocationType().name(), airportIcao, airportName, cityName, bankBalance,
                buildAwake(world, person), buildDuty(world, person), buildFlightTime(world, person), destinationIcao, destinationCity,
                world.personControl().bookingAirports(person).stream().map(Airports.Airport::getIcao).toList(),
                new SettingsDto(person.isAutoFinish(), person.isWarpDrive() ? Boolean.TRUE : null), airlineTravel);
    }

    /**
     * The travel of the pilot by airline for the tiles of the page, null when the pilot does not travel so: where to and from where, where the pilot is now, what the journey does now
     * (the status and what it means), the flight it has found, the ticket. The texts are made here, the page only shows them.
     */
    static AirlineTravelDto buildAirlineTravel(final World world, final Persons.Person person) {
        if (person.getPilotJourneyId() == 0) {
            return null;
        }
        final Journeys.Journey journey = world.journeys().byId(person.getPilotJourneyId()).orElse(null);
        if (journey == null) {
            return null;
        }
        final String toCity = world.cities().byId(journey.getToCityId()).map(Cities.City::getName).orElse("?");
        final String fromCity = world.cities().byId(journey.getFromCityId()).map(Cities.City::getName).orElse("?");
        final String cityNow = journey.getLocationCityId() > 0 ? world.cities().byId(journey.getLocationCityId()).map(Cities.City::getName).orElse(null) : null;

        String flightFrom = null;
        String flightTo = null;
        Long departure = null;
        Integer price = null;
        final TransportFlights.Flight flight = journey.getTransportFlight1Id() > 0 ? world.transportFlights().byId(journey.getTransportFlight1Id()).orElse(null) : null;
        final FlightMissions.Mission mission = flight != null ? world.flightMissions().byId(flight.getFlightMissionId()).orElse(null) : null;
        if (mission != null) {
            flightFrom = world.airports().getIcao(mission.getDepartureAirportId()).orElse("?");
            flightTo = world.airports().getIcao(mission.getActualLandingAirportId() > 0 ? mission.getActualLandingAirportId() : mission.getDestinationAirportId()).orElse("?");
            departure = (long) mission.getPlannedDepartureWorldTime() * 1000L;
            price = journey.getPrice1();
        }
        final String route = flightFrom != null ? flightFrom + " \u2192 " + flightTo : null;

        final String status;
        final String hint;
        Long nextSearch = null;
        switch (journey.getStatus()) {
            case LookingForTickets -> {
                status = "Looking for a flight";
                hint = "From " + (cityNow != null ? cityNow : fromCity);
                nextSearch = journey.getHeartbeatTime() > 0 ? (long) journey.getHeartbeatTime() * 1000L : null;
            }
            case WaitingForCheckIn -> {
                status = "Flight found";
                hint = "Waiting for the check-in";
            }
            case WaitingForBoarding -> {
                status = "Checked in";
                hint = "Waiting for the boarding";
            }
            case OnBoard -> {
                status = "On board";
                hint = "The flight is on the way";
            }
            case WaitingForDeboarding -> {
                status = "Landing";
                hint = "Getting off the flight";
            }
            case JustArrived, ItinerariesDone -> {
                status = "Landed";
                hint = "Deciding what is next";
            }
            case TooLateToBoard -> {
                status = "Missed the flight";
                hint = "Looking for another one";
            }
            default -> {
                status = journey.getStatus().name();
                hint = "";
            }
        }
        return new AirlineTravelDto(toCity, fromCity, cityNow, status, hint, route, flightFrom, flightTo, departure, price, nextSearch);
    }

    /**
     * The travel of the pilot by airline, for the tiles. cityNow is the city where the pilot waits (null while it is in a flight or at the airport of a flight); the flight is
     * the one the journey has booked or is on, with the planned departure (UTC ms) and the ticket; nextSearchTime (UTC ms) is when the journey looks for a flight next.
     */
    @Data
    @AllArgsConstructor
    public static class AirlineTravelDto {
        private String toCity;
        private String fromCity;
        private String cityNow;
        // what the journey does now and what it means
        private String status;
        private String statusHint;
        // the flight: "EGPH -> EDDF" and its airports, null when there is none
        private String route;
        private String flightFrom;
        private String flightTo;
        private Long departureTime;
        private Integer price;
        private Long nextSearchTime;
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
            case StartMoney -> "Start money";
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
    // The window of the landing statistics: the diverts and the cancelled flights, the numbers which the sanctions look at
    static final int LANDING_STATS_WINDOW_DAYS = 30;

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
                experienceLevelDtos(), types, buildLandingStats(world, personId, worldTime));
    }

    // The flags of the logbook entries (docs/diversions-and-alternates.md, section 7) counted over the last days; an entry counts for the time of its landing, that is the blocks on
    static LandingStatsDto buildLandingStats(final World world, final int personId, final int worldTime) {
        final int from = worldTime - LANDING_STATS_WINDOW_DAYS * 24 * 60 * 60;
        int declaredDiverts = 0;
        int unplannedDiverts = 0;
        int cancelledFlights = 0;
        final List<PilotLogbook.Entry> entries = world.pilotLogbook().filter(world.pilotLogbook().byPersonId(personId)).toList();
        for (final PilotLogbook.Entry entry : entries) {
            if (entry.getBlocksOnTime() < from) {
                continue;
            }
            // diverted without the unplanned flag is a declared divert; an unplanned landing has both flags
            if (entry.isUnplannedLanding()) {
                unplannedDiverts++;
            } else if (entry.isDiverted()) {
                declaredDiverts++;
            }
            if (entry.isTransportationCancelled()) {
                cancelledFlights++;
            }
        }
        return new LandingStatsDto(LANDING_STATS_WINDOW_DAYS, declaredDiverts, unplannedDiverts, cancelledFlights);
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
        // the travel by airline, null when the pilot does not travel so
        private AirlineTravelDto airlineTravel;
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

    /**
     * The Details of the duty tile (UTC milliseconds, minutes). flightsMinutes + gapsMinutes = totalMinutes: the minutes of the flights in the last 24 hours (the overlaps
     * counted once) and the minutes of the gaps between flights which count as duty. gapLimitMinutes: a gap shorter than it counts. preflightMinutes, postflightMinutes: what
     * a flight adds before the blocks off and after the blocks on (for the planned flights and the old entries of the logbook).
     */
    @Data
    @AllArgsConstructor
    public static class DutyDetailsDto {
        private long now;
        private long windowStart;
        private int totalMinutes;
        private int limitMinutes;
        private int flightsMinutes;
        private int gapsMinutes;
        private int gapLimitMinutes;
        private int preflightMinutes;
        private int postflightMinutes;
        private boolean onDuty;
        // the periods of duty and the gaps which count, in the order of the time
        private List<DutySegmentDto> segments;
        // the flights of the roster which have not started, the next hour
        private List<DutySegmentDto> planned;
    }

    /**
     * type: flight, gap or planned; route is null for a gap; minutesInWindow: how much of the piece is in the last 24 hours.
     */
    @Data
    @AllArgsConstructor
    public static class DutySegmentDto {
        private String type;
        private long from;
        private long to;
        private String route;
        private int minutesInWindow;
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
        // the estimate of a travel by airline (the flights, the price, the days), null for the other ways
        private AirlineEstimateDto airline;
    }

    /**
     * The estimate of a travel by airline: nothing is booked. found is false when there is no route (the problem of the place says why), the days are from-to for all the flights.
     */
    @Data
    @AllArgsConstructor
    public static class AirlineEstimateDto {
        private boolean found;
        private List<AirlineLegDto> legs;
        // the tickets of all the flights
        private long price;
        // the money on the account: the page advises to have twice the tickets, nothing is checked
        private long balance;
        private int minDays;
        private int maxDays;
        // the routes, the most frequent first (it is the same as the fields above): a direct flight, through a hub, a stopover
        private List<AirlineRouteDto> routes;
        // why there is no route yet (no flights in the last days), null when there is one; it does not forbid the travel, the journey keeps looking for flights
        private String note;
    }

    /**
     * One route of the estimate: how it is called (a direct flight, through a hub, a stopover), whether it is the first (the most frequent) one, its flights, the tickets and the days.
     */
    @Data
    @AllArgsConstructor
    public static class AirlineRouteDto {
        private String title;
        private boolean mostLikely;
        private List<AirlineLegDto> legs;
        private long price;
        private int minDays;
        private int maxDays;
    }

    @Data
    @AllArgsConstructor
    public static class AirlineLegDto {
        private String from;
        private String to;
        private double distanceNm;
        // how many flights there were between the two airports in the days of the flight stats
        private int flightsPerWeek;
        private long price;
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
        // the diverts and the cancelled flights of the last days
        private LandingStatsDto landingStats;
    }

    @Data
    @AllArgsConstructor
    public static class LandingStatsDto {
        // the window in days
        private int windowDays;
        // the landings at the airport which the pilot declared as the divert, and at any other airport which is not the destination
        private int declaredDiverts;
        private int unplannedDiverts;
        // the flights whose transportation the pilot cancelled
        private int cancelledFlights;
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
