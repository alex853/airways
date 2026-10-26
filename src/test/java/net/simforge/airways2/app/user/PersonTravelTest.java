package net.simforge.airways2.app.user;

import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PilotTravel;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.BankTransactions;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.processors.PersonControl;
import net.simforge.airways2.world.processors.PersonProcessor;
import net.simforge.commons.misc.Geo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A free pilot moves from an airport or a city to an airport or a city: the warp drive from anywhere to anywhere, Mach 3 from an airport to an airport, by ground
 * from anywhere to anywhere, by airline from a city to a city (not made yet).
 */
class PersonTravelTest {
    private World world;
    private Airports.Airport oakland;
    private Airports.Airport london;
    private Airports.Airport sanFrancisco;
    // about 1300 miles from Oakland: the longest trip by ground is 48 hours, about 1440 miles
    private Airports.Airport dallas;
    private Cities.City oaklandCity;
    private Cities.City fresno;
    private Persons.Person person;
    private BankAccounts.BankAccount pilotAccount;
    private BankAccounts.BankAccount bank;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        oakland = world.airports().create(37.72, -122.22, "OAK", "KOAK", "Oakland");
        london = world.airports().create(51.47, -0.45, "LHR", "EGLL", "Heathrow");
        sanFrancisco = world.airports().create(37.62, -122.38, "SFO", "KSFO", "San Francisco");
        dallas = world.airports().create(32.90, -97.04, "DFW", "KDFW", "Dallas Fort Worth");
        final Countries.Country country = world.countries().create("US", "United States");
        oaklandCity = world.cities().create(country.getId(), "Oakland", 37.80, -122.27, 400_000);
        fresno = world.cities().create(country.getId(), "Fresno", 36.74, -119.78, 500_000);
        world.airport2city().create(oakland.getId(), oaklandCity.getId());
        world.airport2city().create(sanFrancisco.getId(), oaklandCity.getId());

        person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1, Persons.LocationType.Airport, oakland.getId(), 0, "AK", 0);
        bank = world.bankAccounts().create(BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK, true, world.getWorldTime());
        pilotAccount = world.bankAccounts().create(BankAccounts.Kind.Person, person.getId(), false, world.getWorldTime());
        person.setBankAccountId(pilotAccount.getId());
        final BankAccounts.BankAccount operator = world.bankAccounts().create(BankAccounts.Kind.Operator, 3, true, world.getWorldTime());
        world.bankControl().transfer(operator.getId(), pilotAccount.getId(), 1_000_000, BankTransactions.Type.PilotFlightPay);
    }

    private static PersonControl.Place place(final Airports.Airport airport) {
        return new PersonControl.Place(Persons.LocationType.Airport, airport.getId(), airport.getIcao());
    }

    private static PersonControl.Place place(final Cities.City city) {
        return new PersonControl.Place(Persons.LocationType.City, city.getId(), city.getName());
    }

    private double distance(final Airports.Airport from, final Airports.Airport to) {
        return Geo.distance(from.getCoords(), to.getCoords());
    }

    private double distance(final Airports.Airport from, final Cities.City to) {
        return Geo.distance(from.getCoords(), to.getCoords());
    }

    private double distance(final Cities.City from, final Cities.City to) {
        return Geo.distance(from.getCoords(), to.getCoords());
    }

    // the pilot is in the city now (the place is set by hand: the tests of the ways do not need the trip to it)
    private void pilotIsIn(final Cities.City city) {
        person.setLocation(Persons.LocationType.City, city.getId());
    }

    private void arrive() {
        world.setWorldTime(person.getHeartbeatTime());
        PersonProcessor.process(world);
    }

    // ---- the quotes

    @Test
    void mach3CostsTenThousandForFiveHundredMilesAndTakesTheTimeOfThreeMach() {
        final PilotTravel.Quote quote = PilotTravel.quote(PilotTravel.Method.Mach3, 500);

        assertEquals(10_000, quote.price());
        assertEquals(18, quote.durationMinutes());
    }

    @Test
    void theGroundGoesThirtyMilesAnHour() {
        final PilotTravel.Quote quote = PilotTravel.quote(PilotTravel.Method.Ground, 300);

        assertEquals(10 * 60, quote.durationMinutes());
        assertEquals(600, quote.price());
    }

    @Test
    void theShortestTripByGroundTakesAQuarterOfAnHourAndCostsSomething() {
        final PilotTravel.Quote quote = PilotTravel.quote(PilotTravel.Method.Ground, 2);

        assertEquals(PilotTravel.GROUND_MIN_MINUTES, quote.durationMinutes());
        assertEquals(PilotTravel.GROUND_MIN_PRICE, quote.price());
    }

    @Test
    void theWarpDriveIsAtOnceAndFree() {
        assertEquals(new PilotTravel.Quote(0, 0), PilotTravel.quote(PilotTravel.Method.WarpDrive, 8000));
    }

    // ---- the warp drive: from anywhere to anywhere

    @Test
    void theWarpDriveMovesAirportToAirportAtOnce() {
        person.setWarpDrive(true);

        world.personControl().travel(person, PilotTravel.Method.WarpDrive, place(london), distance(oakland, london));

        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(Persons.LocationType.Airport, person.getLocationType());
        assertEquals(london.getId(), person.getLocationId());
        assertEquals(1_000_000, pilotAccount.getBalance());
    }

    @Test
    void theWarpDriveMovesAirportToCityAndCityToCityAndCityToAirport() {
        person.setWarpDrive(true);

        world.personControl().travel(person, PilotTravel.Method.WarpDrive, place(fresno), distance(oakland, fresno));
        assertEquals(Persons.LocationType.City, person.getLocationType());
        assertEquals(fresno.getId(), person.getLocationId());

        world.personControl().travel(person, PilotTravel.Method.WarpDrive, place(oaklandCity), distance(fresno, oaklandCity));
        assertEquals(oaklandCity.getId(), person.getLocationId());

        world.personControl().travel(person, PilotTravel.Method.WarpDrive, place(london), distance(oaklandCity, fresno));
        assertEquals(Persons.LocationType.Airport, person.getLocationType());
        assertEquals(london.getId(), person.getLocationId());
        assertEquals(Persons.State.Idle, person.getState());
    }

    @Test
    void theWarpDriveIsRefusedWithoutTheFlag() {
        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> world.personControl().travel(person, PilotTravel.Method.WarpDrive, place(london), distance(oakland, london)));

        assertEquals("This way is not available", e.getMessage());
        assertEquals(oakland.getId(), person.getLocationId());
    }

    // ---- Mach 3: only from an airport to an airport

    @Test
    void aFlightOfMach3IsPaidAndTakesTime() {
        final double distance = distance(oakland, london);
        final PilotTravel.Quote quote = PilotTravel.quote(PilotTravel.Method.Mach3, distance);

        world.personControl().travel(person, PilotTravel.Method.Mach3, place(london), distance);

        assertEquals(Persons.State.Travelling, person.getState());
        // while the person is Travelling the location is the place of the arrival
        assertEquals(Persons.LocationType.Airport, person.getLocationType());
        assertEquals(london.getId(), person.getLocationId());
        assertEquals(1_000_000 - quote.price(), pilotAccount.getBalance());
        assertEquals(quote.price(), bank.getBalance());
        assertEquals(world.getWorldTime() + quote.durationMinutes() * Time.ONE_MINUTE, person.getHeartbeatTime());
        assertTrue(world.persons().checkHeartbeatIndex().isEmpty());
    }

    @Test
    void theClockBringsThePilotToThePlaceAndTheFallingAsleepIsScheduled() {
        world.personControl().travel(person, PilotTravel.Method.Mach3, place(london), distance(oakland, london));
        final int arrival = person.getHeartbeatTime();

        world.setWorldTime(arrival - Time.ONE_MINUTE);
        PersonProcessor.process(world);
        assertEquals(Persons.State.Travelling, person.getState());

        arrive();

        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(Persons.LocationType.Airport, person.getLocationType());
        assertEquals(london.getId(), person.getLocationId());
        // the pilot is free again: it is going to fall asleep by itself
        assertTrue(person.getHeartbeatTime() > world.getWorldTime());
    }

    @Test
    void mach3IsNotFromACityNorToACity() {
        assertThrows(IllegalStateException.class, () -> world.personControl().travel(person, PilotTravel.Method.Mach3, place(fresno), distance(oakland, fresno)));
        pilotIsIn(oaklandCity);
        assertThrows(IllegalStateException.class, () -> world.personControl().travel(person, PilotTravel.Method.Mach3, place(london), 5000));
        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(1_000_000, pilotAccount.getBalance());
    }

    @Test
    void mach3IsNotForADistanceUnderFiveHundredMiles() {
        // Oakland and San Francisco are a few miles apart
        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> world.personControl().travel(person, PilotTravel.Method.Mach3, place(sanFrancisco), distance(oakland, sanFrancisco)));

        assertEquals("Too close, from 500 miles", e.getMessage());
        assertEquals(1_000_000, pilotAccount.getBalance());
    }

    // ---- by ground: from anywhere to anywhere

    @Test
    void theGroundTakesDaysFromAirportToAirportAndNothingHappensBeforeThat() {
        world.personControl().travel(person, PilotTravel.Method.Ground, place(dallas), distance(oakland, dallas));
        final int minutes = (person.getHeartbeatTime() - world.getWorldTime()) / Time.ONE_MINUTE;

        // about 1300 miles at 30 miles an hour: more than 40 hours, not more than 48
        assertTrue(minutes > 40 * 60 && minutes <= 48 * 60, "minutes " + minutes);
        world.setWorldTime(world.getWorldTime() + 24 * 60 * Time.ONE_MINUTE);
        PersonProcessor.process(world);
        assertEquals(Persons.State.Travelling, person.getState());
    }

    @Test
    void aTripByGroundOfMoreThanFortyEightHoursIsRefused() {
        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> world.personControl().travel(person, PilotTravel.Method.Ground, place(london), distance(oakland, london)));

        assertTrue(e.getMessage().startsWith("Too far for a trip by ground"), e.getMessage());
        assertTrue(e.getMessage().contains("48 hours"));
        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(1_000_000, pilotAccount.getBalance());
    }

    @Test
    void fortyEightHoursIsAllowedAndAMinuteMoreIsNot() {
        // 1440 miles at 30 miles an hour are exactly 48 hours
        assertEquals(48 * 60, PilotTravel.quote(PilotTravel.Method.Ground, 1440).durationMinutes());
        assertNull(PilotTravel.problem(PilotTravel.Method.Ground, 1440));
        // a mile more is 48 hours and two minutes
        assertEquals(48 * 60 + 2, PilotTravel.quote(PilotTravel.Method.Ground, 1441).durationMinutes());
        assertTrue(PilotTravel.problem(PilotTravel.Method.Ground, 1441).startsWith("Too far"));
    }

    @Test
    void theWarpDriveAndMach3AreNotLimitedByTheHoursOfTheRoad() {
        assertNull(PilotTravel.problem(PilotTravel.Method.WarpDrive, 9000));
        assertNull(PilotTravel.problem(PilotTravel.Method.Mach3, 9000));
    }

    @Test
    void byGroundFromTheAirportToItsCityAndBackToAnotherAirportOfTheCity() {
        final double toCity = distance(oakland, oaklandCity);
        world.personControl().travel(person, PilotTravel.Method.Ground, place(oaklandCity), toCity);

        assertEquals(Persons.State.Travelling, person.getState());
        assertEquals(Persons.LocationType.City, person.getLocationType());
        assertEquals(oaklandCity.getId(), person.getLocationId());
        assertEquals(1_000_000 - PilotTravel.quote(PilotTravel.Method.Ground, toCity).price(), pilotAccount.getBalance());
        arrive();
        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(Persons.LocationType.City, person.getLocationType());

        world.personControl().travel(person, PilotTravel.Method.Ground, place(sanFrancisco), Geo.distance(oaklandCity.getCoords(), sanFrancisco.getCoords()));
        arrive();
        assertEquals(Persons.LocationType.Airport, person.getLocationType());
        assertEquals(sanFrancisco.getId(), person.getLocationId());
    }

    @Test
    void byGroundFromCityToCity() {
        pilotIsIn(oaklandCity);

        world.personControl().travel(person, PilotTravel.Method.Ground, place(fresno), distance(oaklandCity, fresno));
        arrive();

        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(Persons.LocationType.City, person.getLocationType());
        assertEquals(fresno.getId(), person.getLocationId());
    }

    @Test
    void byGroundToACityWhichTheAirportDoesNotServeIsAllowed() {
        world.personControl().travel(person, PilotTravel.Method.Ground, place(fresno), distance(oakland, fresno));

        assertEquals(Persons.State.Travelling, person.getState());
        assertEquals(fresno.getId(), person.getLocationId());
    }

    @Test
    void theMoneyLeavesTheAccountAtOnceWhenTheTravelStarts() {
        final long before = pilotAccount.getBalance();
        final long price = PilotTravel.quote(PilotTravel.Method.Ground, distance(oakland, dallas)).price();

        world.personControl().travel(person, PilotTravel.Method.Ground, place(dallas), distance(oakland, dallas));

        // the pilot is still on the way and has paid already
        assertEquals(Persons.State.Travelling, person.getState());
        assertEquals(before - price, pilotAccount.getBalance());
    }

    // ---- the airline is not made yet

    @Test
    void theAirlineIsNotAWayYet() {
        pilotIsIn(oaklandCity);

        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> world.personControl().travel(person, PilotTravel.Method.Airline, place(fresno), distance(oaklandCity, fresno)));

        assertEquals("Coming soon", e.getMessage());
        assertEquals(Persons.State.Idle, person.getState());
    }

    // ---- who can travel

    @Test
    void aPilotWhoHasNotEnoughMoneyDoesNotGo() {
        final Persons.Person poor = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 2, Persons.LocationType.Airport, oakland.getId(), 0, "BB", 0);
        poor.setBankAccountId(world.bankAccounts().create(BankAccounts.Kind.Person, poor.getId(), false, world.getWorldTime()).getId());

        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> world.personControl().travel(poor, PilotTravel.Method.Mach3, place(london), distance(oakland, london)));

        assertEquals("Not enough money", e.getMessage());
        assertEquals("Not enough money", world.personControl().travelProblem(poor, PilotTravel.Method.Mach3, place(london), distance(oakland, london)));
        assertEquals(Persons.State.Idle, poor.getState());
        assertEquals(oakland.getId(), poor.getLocationId());
    }

    @Test
    void onlyAFreePilotInAnAirportOrInACityCanTravel() {
        person.setState(Persons.State.Resting);
        assertThrows(IllegalStateException.class, () -> world.personControl().travel(person, PilotTravel.Method.Ground, place(london), distance(oakland, london)));

        person.setState(Persons.State.Idle);
        person.setLocation(Persons.LocationType.None, 0);
        assertThrows(IllegalStateException.class, () -> world.personControl().travel(person, PilotTravel.Method.Ground, place(london), distance(oakland, london)));
    }

    @Test
    void aPilotDoesNotTravelToThePlaceWhereItIs() {
        assertThrows(IllegalArgumentException.class, () -> world.personControl().travel(person, PilotTravel.Method.Ground, place(oakland), 0));
        pilotIsIn(oaklandCity);
        assertThrows(IllegalArgumentException.class, () -> world.personControl().travel(person, PilotTravel.Method.Ground, place(oaklandCity), 0));
    }

    // ---- the menu: the ways of the place

    private static List<String> methodNames(final PilotController.TravelMethodsDto menu) {
        return menu.getMethods().stream().map(PilotController.TravelMethodDto::getMethod).collect(Collectors.toList());
    }

    @Test
    void theMenuOfAnAirportHasMach3AndGround() {
        final PilotController.TravelMethodsDto menu = PilotController.buildTravelMethods(world, person);

        assertEquals("airport", menu.getLocation());
        assertEquals("KOAK", menu.getPlaceName());
        assertEquals(List.of("Mach3", "Ground"), methodNames(menu));
        assertTrue(menu.getMethods().stream().allMatch(PilotController.TravelMethodDto::isAvailable));
    }

    @Test
    void theMenuOfACityHasTheAirlineWhichCannotBeChosenAndGround() {
        pilotIsIn(oaklandCity);

        final PilotController.TravelMethodsDto menu = PilotController.buildTravelMethods(world, person);

        assertEquals("city", menu.getLocation());
        assertEquals(List.of("Airline", "Ground"), methodNames(menu));
        assertFalse(menu.getMethods().get(0).isAvailable());
        assertTrue(menu.getMethods().get(1).isAvailable());
    }

    @Test
    void theWarpDriveIsInTheMenuOnlyOfThePilotWhoHasIt() {
        assertFalse(methodNames(PilotController.buildTravelMethods(world, person)).contains("WarpDrive"));

        person.setWarpDrive(true);

        assertEquals(List.of("Mach3", "Ground", "WarpDrive"), methodNames(PilotController.buildTravelMethods(world, person)));
        pilotIsIn(oaklandCity);
        assertEquals(List.of("Airline", "Ground", "WarpDrive"), methodNames(PilotController.buildTravelMethods(world, person)));
    }

    @Test
    void theMenuIsRefusedToAPilotWhoCannotTravel() {
        person.setState(Persons.State.Resting);

        assertThrows(ResponseStatusException.class, () -> PilotController.buildTravelMethods(world, person));
    }

    // ---- the quick places (commute)

    @Test
    void thePlacesOfAnAirportAreTheCitiesWhichItServes() {
        final PilotController.TravelPlacesDto places = PilotController.buildTravelPlaces(world, person);

        assertEquals("city", places.getKind());
        assertEquals(List.of("Oakland"), places.getPlaces().stream().map(PilotController.TravelPlaceDto::getTitle).collect(Collectors.toList()));
        assertEquals("city", places.getPlaces().get(0).getKind());
        assertTrue(places.getPlaces().get(0).isAvailable());
        assertTrue(places.getPlaces().get(0).getPrice() >= PilotTravel.GROUND_MIN_PRICE);
    }

    @Test
    void thePlacesOfACityAreTheAirportsWhichServeItNearestFirst() {
        pilotIsIn(oaklandCity);

        final PilotController.TravelPlacesDto places = PilotController.buildTravelPlaces(world, person);

        assertEquals("airport", places.getKind());
        assertEquals(List.of("KOAK", "KSFO"), places.getPlaces().stream().map(PilotController.TravelPlaceDto::getId).collect(Collectors.toList()));
        assertEquals("Oakland", places.getPlaces().get(0).getSubtitle());
    }

    // ---- the search of the place

    @Test
    void theCodeOfAnAirportIsFoundFirst() {
        final PilotController.TravelFindDto found = PilotController.findTravelPlaces(world, person, "Ground", " egll ");

        assertNull(found.getMessage());
        assertEquals(1, found.getMatches().size());
        assertEquals("airport", found.getMatches().get(0).getKind());
        assertEquals("EGLL", found.getMatches().get(0).getId());
        assertEquals("Heathrow", found.getMatches().get(0).getSubtitle());
        assertTrue(found.getMatches().get(0).getDistanceNm() > 4000);
    }

    @Test
    void ifThereIsNoSuchAirportTheNameOfACityIsSearched() {
        final PilotController.TravelFindDto found = PilotController.findTravelPlaces(world, person, "Ground", "fresno");

        assertEquals(1, found.getMatches().size());
        assertEquals("city", found.getMatches().get(0).getKind());
        assertEquals("Fresno", found.getMatches().get(0).getTitle());
        assertEquals("United States", found.getMatches().get(0).getSubtitle());
        assertEquals(Id.encode(fresno.getId()), found.getMatches().get(0).getId());
    }

    @Test
    void severalCitiesWithTheSameNameAreAllOffered() {
        final Countries.Country canada = world.countries().create("CA", "Canada");
        world.cities().create(canada.getId(), "Fresno", 50.0, -100.0, 1000);

        final PilotController.TravelFindDto found = PilotController.findTravelPlaces(world, person, "Ground", "Fresno");

        assertEquals(2, found.getMatches().size());
        // the bigger one first
        assertEquals("United States", found.getMatches().get(0).getSubtitle());
        assertEquals("Canada", found.getMatches().get(1).getSubtitle());
    }

    @Test
    void theBeginningOfTheNameOfACityIsEnough() {
        final PilotController.TravelFindDto found = PilotController.findTravelPlaces(world, person, "Ground", "Fres");

        assertEquals(List.of("Fresno"), found.getMatches().stream().map(PilotController.TravelPlaceDto::getTitle).collect(Collectors.toList()));
    }

    @Test
    void mach3ReadsOnlyTheCodeOfAnAirport() {
        final PilotController.TravelFindDto city = PilotController.findTravelPlaces(world, person, "Mach3", "Fresno");
        assertTrue(city.getMatches().isEmpty());
        assertTrue(city.getMessage().contains("between airports"));

        final PilotController.TravelFindDto airport = PilotController.findTravelPlaces(world, person, "Mach3", "EGLL");
        assertEquals(1, airport.getMatches().size());
        assertTrue(airport.getMatches().get(0).isAvailable());
    }

    @Test
    void aPlaceWhichCannotBeReachedSaysWhy() {
        // the place where the pilot is, a too close airport for Mach 3, a way which the pilot does not have
        assertEquals("The pilot is at KOAK already", PilotController.findTravelPlaces(world, person, "Ground", "KOAK").getMatches().get(0).getProblem());
        assertEquals("Too close, from 500 miles", PilotController.findTravelPlaces(world, person, "Mach3", "KSFO").getMatches().get(0).getProblem());
        assertEquals("This way is not available", PilotController.findTravelPlaces(world, person, "WarpDrive", "EGLL").getMatches().get(0).getProblem());
    }

    @Test
    void nothingFoundAndNothingTypedAreSaidInWords() {
        assertTrue(PilotController.findTravelPlaces(world, person, "Ground", "Nowhereville").getMessage().contains("Nowhereville"));
        assertTrue(PilotController.findTravelPlaces(world, person, "Ground", "  ").getMessage().startsWith("Type"));
    }

    // ---- the start by the kind and the id of the place

    @Test
    void startingTheTravelToACityByItsId() {
        PilotController.startTravel(world, person, "Ground", "city", Id.encode(oaklandCity.getId()));

        assertEquals(Persons.State.Travelling, person.getState());
        assertEquals(Persons.LocationType.City, person.getLocationType());
        assertEquals(oaklandCity.getId(), person.getLocationId());
    }

    @Test
    void startingTheTravelToAnAirportByItsCode() {
        PilotController.startTravel(world, person, "Mach3", "airport", "egll");

        assertEquals(Persons.State.Travelling, person.getState());
        assertEquals(london.getId(), person.getLocationId());
    }

    @Test
    void aWrongStartIsRefused() {
        assertThrows(ResponseStatusException.class, () -> PilotController.startTravel(world, person, "Teleport", "airport", "EGLL"));
        assertThrows(ResponseStatusException.class, () -> PilotController.startTravel(world, person, "Ground", "planet", "EGLL"));
        assertThrows(ResponseStatusException.class, () -> PilotController.startTravel(world, person, "Ground", "airport", "ZZZZ"));
        assertThrows(ResponseStatusException.class, () -> PilotController.startTravel(world, person, "Ground", "city", "!!!"));
        assertThrows(ResponseStatusException.class, () -> PilotController.startTravel(world, person, "Warp" + "Drive", "airport", "EGLL"));
        assertEquals(Persons.State.Idle, person.getState());
    }

    // ---- what the page of the pilot knows

    @Test
    void thePageKnowsWhereTheTravellingPilotGoes() {
        world.personControl().travel(person, PilotTravel.Method.Mach3, place(london), distance(oakland, london));

        final PilotController.PilotDto pilot = PilotController.buildPilot(world, person);

        assertEquals("Travelling", pilot.getState());
        assertEquals("EGLL", pilot.getDestinationIcao());
        // the place of the arrival is not where the pilot is
        assertNull(pilot.getAirportIcao());
        assertNull(pilot.getDestinationCity());
    }

    @Test
    void thePageKnowsWhichCityThePilotGoesTo() {
        world.personControl().travel(person, PilotTravel.Method.Ground, place(oaklandCity), distance(oakland, oaklandCity));

        final PilotController.PilotDto pilot = PilotController.buildPilot(world, person);

        assertEquals("Oakland", pilot.getDestinationCity());
        assertNull(pilot.getCityName());
        assertNull(pilot.getDestinationIcao());
        assertNull(pilot.getAirportIcao());
    }

    // ---- the awake time on the way: a trip which uses it up gives a random rest, an overtired pilot is not rested by a trip

    private int creditAtArrival() {
        world.setWorldTime(person.getHeartbeatTime());
        PersonProcessor.process(world);
        return person.getAwakeCreditMinutes();
    }

    @Test
    void aTripWhichUsesUpTheAwakeTimeGivesARandomRestOfTwoToTenHours() {
        // 2 hours of the awake time, the trip to Fresno by ground takes more than 5 hours
        person.setAwakeCredit(2 * 60, world.getWorldTime());
        world.personControl().travel(person, PilotTravel.Method.Ground, place(fresno), distance(oakland, fresno));
        assertTrue(PilotTravel.quote(PilotTravel.Method.Ground, distance(oakland, fresno)).durationMinutes() > 2 * 60);

        final int credit = creditAtArrival();

        assertEquals(Persons.State.Idle, person.getState());
        assertTrue(credit >= net.simforge.airways2.world.computations.PilotRest.TRIP_REST_MIN_MINUTES && credit <= net.simforge.airways2.world.computations.PilotRest.TRIP_REST_MAX_MINUTES, "credit " + credit);
        assertEquals(world.getWorldTime(), person.getCreditTime());
        // the falling asleep by itself is scheduled again from the new awake time
        assertTrue(person.getHeartbeatTime() > world.getWorldTime());
    }

    @Test
    void aTripWithinTheAwakeTimeJustMakesItSmaller() {
        person.setAwakeCredit(20 * 60, world.getWorldTime());
        final int minutes = PilotTravel.quote(PilotTravel.Method.Ground, distance(oakland, fresno)).durationMinutes();
        world.personControl().travel(person, PilotTravel.Method.Ground, place(fresno), distance(oakland, fresno));

        world.setWorldTime(person.getHeartbeatTime());
        PersonProcessor.process(world);

        final int credit = net.simforge.airways2.world.computations.PilotRest.creditNow(person.getAwakeCreditMinutes(), person.getCreditTime(), world.getWorldTime());
        assertEquals(20 * 60 - minutes, credit);
        assertEquals(Persons.State.Idle, person.getState());
        assertTrue(person.getHeartbeatTime() > world.getWorldTime());
    }

    @Test
    void anOvertiredPilotIsNotRestedByATrip() {
        // one hour of the debt at the departure
        person.setAwakeCredit(-60, world.getWorldTime());
        final int minutes = PilotTravel.quote(PilotTravel.Method.Ground, distance(oakland, fresno)).durationMinutes();
        world.personControl().travel(person, PilotTravel.Method.Ground, place(fresno), distance(oakland, fresno));

        world.setWorldTime(person.getHeartbeatTime());
        PersonProcessor.process(world);

        final int credit = net.simforge.airways2.world.computations.PilotRest.creditNow(person.getAwakeCreditMinutes(), person.getCreditTime(), world.getWorldTime());
        assertEquals(-60 - minutes, credit);
    }

    @Test
    void theWarpDriveDoesNotTouchTheAwakeTime() {
        person.setWarpDrive(true);
        person.setAwakeCredit(30, world.getWorldTime());

        world.personControl().travel(person, PilotTravel.Method.WarpDrive, place(london), distance(oakland, london));

        assertEquals(30, person.getAwakeCreditMinutes());
        assertEquals(Persons.State.Idle, person.getState());
    }
}
