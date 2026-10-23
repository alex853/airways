package net.simforge.airways2.app.user;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.processors.PersonProcessor;
import net.simforge.airways2.world.computations.PilotTravel;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.BankTransactions;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A free pilot at an airport moves to another airport by itself: the warp drive at once, Mach 3 and the ground take their time and cost money.
 */
class PersonTravelTest {
    private World world;
    private Airports.Airport oakland;
    private Airports.Airport london;
    private Persons.Person person;
    private BankAccounts.BankAccount pilotAccount;
    private BankAccounts.BankAccount bank;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        oakland = world.airports().create(37.72, -122.22, "OAK", "KOAK", "Oakland");
        london = world.airports().create(51.47, -0.45, "LHR", "EGLL", "Heathrow");
        person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1, Persons.LocationType.Airport, oakland.getId(), 0, "AK", 0);
        bank = world.bankAccounts().create(BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK, true, world.getWorldTime());
        pilotAccount = world.bankAccounts().create(BankAccounts.Kind.Person, person.getId(), false, world.getWorldTime());
        person.setBankAccountId(pilotAccount.getId());
        final BankAccounts.BankAccount operator = world.bankAccounts().create(BankAccounts.Kind.Operator, 3, true, world.getWorldTime());
        world.bankControl().transfer(operator.getId(), pilotAccount.getId(), 1_000_000, BankTransactions.Type.PilotFlightPay);
    }

    private double distance() {
        return net.simforge.commons.misc.Geo.distance(oakland.getCoords(), london.getCoords());
    }

    private void minutesPass(final int minutes) {
        world.setWorldTime(world.getWorldTime() + minutes * Time.ONE_MINUTE);
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
    void theWarpDriveIsAtOnceAndFree() {
        assertEquals(new PilotTravel.Quote(0, 0), PilotTravel.quote(PilotTravel.Method.WarpDrive, 8000));
    }

    @Test
    void aShortWayTakesAMinuteAtLeast() {
        assertEquals(1, PilotTravel.quote(PilotTravel.Method.Mach3, 1).durationMinutes());
    }

    // ---- the travel

    @Test
    void theWarpDriveMovesAFreePilotAtOnce() {
        person.setWarpDrive(true);

        world.personControl().travel(person, PilotTravel.Method.WarpDrive, london, distance());

        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(Persons.LocationType.Airport, person.getLocationType());
        assertEquals(london.getId(), person.getLocationId());
        assertEquals(1_000_000, pilotAccount.getBalance());
    }

    @Test
    void theWarpDriveIsRefusedWithoutTheFlag() {
        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> world.personControl().travel(person, PilotTravel.Method.WarpDrive, london, distance()));

        assertEquals("This way is not available", e.getMessage());
        assertEquals(oakland.getId(), person.getLocationId());
    }

    @Test
    void aFlightOfMach3IsPaidAndTakesTime() {
        final double distance = distance();
        final PilotTravel.Quote quote = PilotTravel.quote(PilotTravel.Method.Mach3, distance);

        world.personControl().travel(person, PilotTravel.Method.Mach3, london, distance);

        assertEquals(Persons.State.Travelling, person.getState());
        assertEquals(Persons.LocationType.Transit, person.getLocationType());
        assertEquals(london.getId(), person.getLocationId());
        assertEquals(1_000_000 - quote.price(), pilotAccount.getBalance());
        assertEquals(quote.price(), bank.getBalance());
        assertEquals(world.getWorldTime() + quote.durationMinutes() * Time.ONE_MINUTE, person.getHeartbeatTime());
        assertTrue(world.persons().checkHeartbeatIndex().isEmpty());
    }

    @Test
    void theClockBringsThePilotToTheAirportAndTheFallingAsleepIsScheduled() {
        world.personControl().travel(person, PilotTravel.Method.Mach3, london, distance());
        final int arrival = person.getHeartbeatTime();

        world.setWorldTime(arrival - Time.ONE_MINUTE);
        PersonProcessor.process(world);
        assertEquals(Persons.State.Travelling, person.getState());

        world.setWorldTime(arrival);
        PersonProcessor.process(world);

        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(Persons.LocationType.Airport, person.getLocationType());
        assertEquals(london.getId(), person.getLocationId());
        // the pilot is free again: it is going to fall asleep by itself
        assertTrue(person.getHeartbeatTime() > world.getWorldTime());
    }

    @Test
    void theGroundTakesDaysAndNothingHappensBeforeThat() {
        world.personControl().travel(person, PilotTravel.Method.Ground, london, distance());
        final int minutes = (person.getHeartbeatTime() - world.getWorldTime()) / Time.ONE_MINUTE;

        // about 4600 miles at 30 miles an hour: more than six days
        assertTrue(minutes > 3 * 24 * 60, "minutes " + minutes);
        minutesPass(24 * 60);
        PersonProcessor.process(world);
        assertEquals(Persons.State.Travelling, person.getState());
    }

    @Test
    void thePageKnowsWhereTheTravellingPilotGoes() {
        world.personControl().travel(person, PilotTravel.Method.Mach3, london, distance());

        final PilotController.PilotDto pilot = PilotController.buildPilot(world, person);

        assertEquals("Travelling", pilot.getState());
        assertEquals("EGLL", pilot.getDestinationIcao());
        assertEquals(null, pilot.getAirportIcao());
    }

    @Test
    void theMoneyLeavesTheAccountAtOnceWhenTheTravelStarts() {
        final long before = pilotAccount.getBalance();
        final long price = PilotTravel.quote(PilotTravel.Method.Ground, distance()).price();

        world.personControl().travel(person, PilotTravel.Method.Ground, london, distance());

        // the pilot is still on the way and has paid already
        assertEquals(Persons.State.Travelling, person.getState());
        assertEquals(before - price, pilotAccount.getBalance());
    }

    @Test
    void aPilotWhoHasNotEnoughMoneyDoesNotGo() {
        final Persons.Person poor = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 2, Persons.LocationType.Airport, oakland.getId(), 0, "BB", 0);
        poor.setBankAccountId(world.bankAccounts().create(BankAccounts.Kind.Person, poor.getId(), false, world.getWorldTime()).getId());

        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> world.personControl().travel(poor, PilotTravel.Method.Mach3, london, distance()));

        assertEquals("Not enough money", e.getMessage());
        assertEquals(Persons.State.Idle, poor.getState());
        assertEquals(oakland.getId(), poor.getLocationId());
    }

    @Test
    void onlyAFreePilotAtAnAirportCanTravel() {
        person.setState(Persons.State.Resting);
        assertThrows(IllegalStateException.class, () -> world.personControl().travel(person, PilotTravel.Method.Ground, london, distance()));

        person.setState(Persons.State.Idle);
        person.setLocation(Persons.LocationType.None, 0);
        assertThrows(IllegalStateException.class, () -> world.personControl().travel(person, PilotTravel.Method.Ground, london, distance()));
    }

    @Test
    void aPilotDoesNotTravelToTheAirportWhereItIs() {
        assertThrows(IllegalArgumentException.class, () -> world.personControl().travel(person, PilotTravel.Method.Ground, oakland, 0));
    }

    // ---- the options of the page

    @Test
    void theWarpDriveIsNotAmongTheOptionsOfAPilotWhoDoesNotHaveIt() {
        final PilotController.TravelOptionsDto options = PilotController.buildTravelOptions(world, person, "egll");

        assertEquals(List.of("Mach3", "Ground"), options.getOptions().stream().map(PilotController.TravelOptionDto::getMethod).collect(Collectors.toList()));
        assertEquals("EGLL", options.getAirportIcao());
        assertTrue(options.getDistanceNm() > 4000);
        assertTrue(options.getOptions().stream().allMatch(PilotController.TravelOptionDto::isAvailable));
    }

    @Test
    void theWarpDriveIsAmongTheOptionsOfThePilotWhoHasIt() {
        person.setWarpDrive(true);

        final PilotController.TravelOptionsDto options = PilotController.buildTravelOptions(world, person, "EGLL");

        assertEquals(List.of("WarpDrive", "Mach3", "Ground"), options.getOptions().stream().map(PilotController.TravelOptionDto::getMethod).collect(Collectors.toList()));
        assertEquals(0, options.getOptions().get(0).getDurationMinutes());
        assertEquals(0, options.getOptions().get(0).getPrice());
    }

    @Test
    void anOptionWhichCannotBePaidSaysWhy() {
        final Persons.Person poor = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 2, Persons.LocationType.Airport, oakland.getId(), 0, "BB", 0);
        poor.setBankAccountId(world.bankAccounts().create(BankAccounts.Kind.Person, poor.getId(), false, world.getWorldTime()).getId());

        final PilotController.TravelOptionDto mach3 = PilotController.buildTravelOptions(world, poor, "EGLL").getOptions().get(0);

        assertFalse(mach3.isAvailable());
        assertEquals("Not enough money", mach3.getProblem());
    }

    @Test
    void anUnknownAirportIsNotFoundAndASleepingPilotCannotAsk() {
        assertThrows(ResponseStatusException.class, () -> PilotController.buildTravelOptions(world, person, "ZZZZ"));

        person.setState(Persons.State.Resting);
        assertThrows(ResponseStatusException.class, () -> PilotController.buildTravelOptions(world, person, "EGLL"));
    }

    @Test
    void startingTheTravelByTheNameOfTheWay() {
        PilotController.startTravel(world, person, "EGLL", "Mach3");

        assertEquals(Persons.State.Travelling, person.getState());
        assertNotNull(world.persons().byId(person.getId()));
        assertThrows(ResponseStatusException.class, () -> PilotController.startTravel(world, person, "EGLL", "Teleport"));
    }
}
