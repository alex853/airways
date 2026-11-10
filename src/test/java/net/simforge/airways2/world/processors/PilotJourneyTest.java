package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PilotRest;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.BankTransactions;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import net.simforge.airways2.world.datamodel.EventLog;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The journey of a pilot (travel by airline, docs/pilot-airline-travel.md): the pilot is a journey of one person which looks for a flight every 10-15 minutes, never gives up,
 * pays nothing yet and brings the pilot to the city of its destination.
 * <p>
 * The geography is a line along the equator: the origin city is at the longitude 0, the hub city at 10, the target city at 30.
 */
public class PilotJourneyTest {
    private World world;
    private Countries.Country country;
    private AircraftTypes.AircraftType aircraftType;
    private Cities.City origin;
    private Cities.City target;
    private Airports.Airport originAirport;
    private Persons.Person pilot;
    private BankAccounts.BankAccount pilotAccount;
    private int aircraftCounter;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        country = world.countries().create("TT", "Testland");
        aircraftType = world.aircraftTypes().create("A320", "320");

        origin = city("Origin", 0);
        target = city("Target", 30);
        originAirport = airport("OOOO", origin, 0);
        pilot = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1, Persons.LocationType.City, origin.getId(), 0, "AA", 0);
        // the bank: the system accounts (the 2nd is the passengers) and the account of the pilot with 1000
        world.bankAccounts().create(BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK, true, world.getWorldTime());
        world.bankAccounts().create(BankAccounts.Kind.System, BankAccounts.SYSTEM_PASSENGERS, true, world.getWorldTime());
        pilotAccount = world.bankAccounts().create(BankAccounts.Kind.Person, pilot.getId(), false, world.getWorldTime());
        pilot.setBankAccountId(pilotAccount.getId());
        world.bankControl().transfer(BankAccounts.SystemPassengersAccountId, pilotAccount.getId(), 1000, BankTransactions.Type.PilotFlightPay);
    }

    private Cities.City city(final String name, final int longitude) {
        return world.cities().create(country.getId(), name, 0, longitude, 1_000_000);
    }

    private Airports.Airport airport(final String icao, final Cities.City city, final int longitude) {
        final Airports.Airport airport = world.airports().create(0, longitude, icao.substring(1), icao, icao);
        world.airport2city().create(airport.getId(), city.getId());
        airport.setHub(icao.startsWith("EG"));
        return airport;
    }

    private TransportFlights.Flight flightTo(final Airports.Airport to, final int departureInMinutes) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(aircraftType, "A-" + (1000 + aircraftCounter++), originAirport);
        final int departureTime = world.getWorldTime() + departureInMinutes * Time.ONE_MINUTE;
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(aircraft, originAirport, to, departureTime, departureTime + 2 * Time.ONE_HOUR);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        mission.setUserId(2);
        return world.transportFlightControl().createTransportFlight(mission);
    }

    private Journeys.Journey start() {
        return world.personControl().startAirlineTravel(pilot, target.getId());
    }

    // ---- the start

    @Test
    public void theStartMakesTheJourneyOfThePilotAndThePilotTravels() {
        final Journeys.Journey journey = start();

        assertTrue(journey.isPilotJourney());
        assertEquals(Journeys.Status.LookingForTickets, journey.getStatus());
        assertEquals(1, journey.getGroupSize());
        assertEquals(origin.getId(), journey.getLocationCityId());
        assertEquals(target.getId(), journey.getToCityId());
        assertEquals(world.getWorldTime(), journey.getHeartbeatTime());
        assertEquals(Persons.State.Travelling, pilot.getState());
        assertEquals(journey.getId(), pilot.getPilotJourneyId());
        assertEquals(pilot.getId(), world.persons().byPilotJourneyId(journey.getId()).orElseThrow().getId());
        // the pilot is not at the place of the arrival: the heartbeat of the person is not the arrival (the journey brings the pilot)
        assertEquals(0, pilot.getHeartbeatTime());
        assertEquals(origin.getId(), pilot.getLocationId());
    }

    @Test
    public void theStartIsWrittenToTheHistoryOfThePilot() {
        start();

        final var events = world.eventLog().byObject(EventLog.ObjectType.Person.code(), pilot.getId())
                .filter(e -> e.getType() == EventLog.EventType.PersonTravelStarted).toList();
        assertEquals(1, events.size());
        assertEquals(3, events.get(0).getObject2Id());
        assertEquals(target.getId(), events.get(0).getObject3Id());
        assertEquals(origin.getId(), events.get(0).getObject4Id());
    }

    @Test
    public void onlyAFreePilotInACityGoesToAnotherCityFarEnough() {
        pilot.setState(Persons.State.Resting);
        assertThrows(IllegalStateException.class, this::start);
        pilot.setState(Persons.State.Idle);

        pilot.setLocation(Persons.LocationType.Airport, originAirport.getId());
        assertThrows(IllegalStateException.class, this::start);
        pilot.setLocation(Persons.LocationType.City, origin.getId());

        assertThrows(IllegalArgumentException.class, () -> world.personControl().startAirlineTravel(pilot, origin.getId()));

        final Cities.City near = world.cities().create(country.getId(), "Near", 0, 0.5, 1000);
        assertThrows(IllegalStateException.class, () -> world.personControl().startAirlineTravel(pilot, near.getId()));

        assertEquals(Persons.State.Idle, pilot.getState());
        assertEquals(0, pilot.getPilotJourneyId());
    }

    @Test
    public void aPilotWithoutABankAccountDoesNotStart() {
        pilot.setBankAccountId(0);

        assertThrows(IllegalStateException.class, this::start);

        assertEquals(Persons.State.Idle, pilot.getState());
        assertEquals(0, pilot.getPilotJourneyId());
    }

    // ---- the search

    @Test
    public void withNoFlightsTheJourneySearchesEveryTenToFifteenMinutesAndNeverGivesUp() {
        final Journeys.Journey journey = start();

        for (int i = 0; i < 10; i++) {
            JourneyProcessor.process(world);
            assertEquals(Journeys.Status.LookingForTickets, journey.getStatus());
            assertEquals(0, journey.getAttemptCounter());
            final int delay = journey.getHeartbeatTime() - world.getWorldTime();
            assertTrue(delay >= JourneyControl.PILOT_SEARCH_MIN_INTERVAL && delay <= JourneyControl.PILOT_SEARCH_MAX_INTERVAL, "delay " + delay);
            journey.setHeartbeatTime(world.getWorldTime());
        }
        assertEquals(Persons.State.Travelling, pilot.getState());
    }

    @Test
    public void aFlightToAHubIsTakenAndItsPriceIsTheIncomeOfTheFlight() {
        final Airports.Airport hub = airport("EGLL", city("Hub", 10), 10);
        final TransportFlights.Flight flight = flightTo(hub, 60);
        final Journeys.Journey journey = start();
        final int seatsBefore = flight.getRemainedTickets().get(net.simforge.airways2.tools.CabinLayout.Service.Y);

        JourneyProcessor.process(world);

        assertEquals(Journeys.Status.WaitingForCheckIn, journey.getStatus());
        assertEquals(flight.getId(), journey.getTransportFlight1Id());
        // the price is fixed at the booking, as for the others (by the distance of the flight), it is the income of the flight; the pilot pays when it gets off
        assertTrue(journey.getPrice1() > 0);
        assertEquals(journey.getPrice1(), world.transportFlightControl().getIncome(flight));
        assertEquals(1000, pilotAccount.getBalance());
        // the pilot takes one seat
        assertEquals(seatsBefore - 1, flight.getRemainedTickets().get(net.simforge.airways2.tools.CabinLayout.Service.Y));
    }

    @Test
    public void aDirectFlightIsTaken() {
        final Airports.Airport targetAirport = airport("TTTT", target, 30);
        final TransportFlights.Flight flight = flightTo(targetAirport, 60);
        final Journeys.Journey journey = start();

        JourneyProcessor.process(world);

        assertEquals(Journeys.Status.WaitingForCheckIn, journey.getStatus());
        assertEquals(flight.getId(), journey.getTransportFlight1Id());
        assertTrue(journey.getPrice1() > 0);
    }

    // ---- the arrival

    @Test
    public void inTheCityOfTheDestinationTheJourneyIsFinishedAndThePilotIsFreeThere() {
        final Journeys.Journey journey = start();
        journey.setStatus(Journeys.Status.ItinerariesDone);
        journey.setLocationCityId(target.getId());
        journey.setHeartbeatTime(world.getWorldTime());

        JourneyProcessor.process(world);

        assertEquals(Journeys.Status.Finished, journey.getStatus());
        assertEquals(Persons.State.Idle, pilot.getState());
        assertEquals(Persons.LocationType.City, pilot.getLocationType());
        assertEquals(target.getId(), pilot.getLocationId());
        assertEquals(0, pilot.getPilotJourneyId());
        assertTrue(world.persons().byPilotJourneyId(journey.getId()).isEmpty());
        // the pilot is free: it is going to fall asleep by itself
        assertTrue(pilot.getHeartbeatTime() > world.getWorldTime());
        final var arrival = world.eventLog().byObject(EventLog.ObjectType.Person.code(), pilot.getId())
                .filter(e -> e.getType() == EventLog.EventType.PersonArrivedToCity).toList();
        assertEquals(1, arrival.size());
        assertEquals(target.getId(), arrival.get(0).getObject2Id());
    }

    @Test
    public void notInTheCityOfTheDestinationTheJourneySearchesAgainFromWhereItIs() {
        final Cities.City hubCity = city("Hub", 10);
        final Journeys.Journey journey = start();
        journey.setStatus(Journeys.Status.ItinerariesDone);
        journey.setLocationCityId(hubCity.getId());
        journey.setHeartbeatTime(world.getWorldTime());

        JourneyProcessor.process(world);

        assertEquals(Journeys.Status.LookingForTickets, journey.getStatus());
        assertEquals(Persons.State.Travelling, pilot.getState());
        assertEquals(journey.getId(), pilot.getPilotJourneyId());
        final int delay = journey.getHeartbeatTime() - world.getWorldTime();
        assertTrue(delay >= JourneyControl.PILOT_SEARCH_MIN_INTERVAL && delay <= JourneyControl.PILOT_SEARCH_MAX_INTERVAL, "delay " + delay);
    }

    @Test
    public void gettingOffAFlightGivesTheAwakeTimeFromTwoToTwelveHours() {
        final Journeys.Journey journey = start();

        world.personControl().onPilotJourneyDeboarded(pilot, journey, 0, 0);

        final int awake = pilot.getAwakeCreditMinutes();
        assertTrue(awake >= PilotRest.AIRLINE_DEBOARD_MIN_MINUTES && awake <= PilotRest.AIRLINE_DEBOARD_MAX_MINUTES, "awake " + awake);
        assertEquals(world.getWorldTime(), pilot.getCreditTime());
    }

    // ---- the payment: the pilot pays the price of a flight when it gets off, to the account of the passengers

    @Test
    public void gettingOffAFlightThePilotPaysThePriceToThePassengers() {
        final Journeys.Journey journey = start();
        final long passengersBefore = world.bankAccounts().byId(BankAccounts.SystemPassengersAccountId).orElseThrow().getBalance();

        world.personControl().onPilotJourneyDeboarded(pilot, journey, 300, 0);

        assertEquals(700, pilotAccount.getBalance());
        assertEquals(passengersBefore + 300, world.bankAccounts().byId(BankAccounts.SystemPassengersAccountId).orElseThrow().getBalance());
    }

    @Test
    public void aPilotWhoHasLessThanThePricePaysWhatItHasAndGoesOn() {
        final Journeys.Journey journey = start();

        world.personControl().onPilotJourneyDeboarded(pilot, journey, 1500, 0);

        assertEquals(0, pilotAccount.getBalance());
        assertEquals(Persons.State.Travelling, pilot.getState());
    }

    @Test
    public void theDeboardingOfTheJourneyTakesThePriceOfItsFlightFromThePilot() {
        final Airports.Airport targetAirport = airport("TTTT", target, 30);
        final TransportFlights.Flight flight = flightTo(targetAirport, 60);
        final Journeys.Journey journey = start();
        JourneyProcessor.process(world);
        final int price = journey.getPrice1();
        journey.setStatus(Journeys.Status.WaitingForDeboarding);
        flight.setStatus(TransportFlights.Status.Deboarding);

        world.journeyControl().deboard(journey);

        assertEquals(1000 - price, pilotAccount.getBalance());
        assertTrue(pilot.getAwakeCreditMinutes() >= PilotRest.AIRLINE_DEBOARD_MIN_MINUTES);
    }

    // ---- the history of the pilot: what is written to the event log and how the page tells it

    private java.util.List<String> history() {
        return net.simforge.airways2.app.user.PilotController.buildLocationHistory(world, pilot).stream()
                .map(net.simforge.airways2.app.user.PilotController.LocationEventDto::getText).toList();
    }

    @Test
    public void theHistoryTellsTheSearchTheFlightTheMoneyTheGettingOffAndTheFailedFlight() {
        final Airports.Airport hub = airport("EGLL", city("Hub", 10), 10);
        final TransportFlights.Flight flight = flightTo(hub, 60);
        final Journeys.Journey journey = start();
        JourneyProcessor.process(world);
        final int price = journey.getPrice1();

        journey.setStatus(Journeys.Status.WaitingForDeboarding);
        flight.setStatus(TransportFlights.Status.Deboarding);
        world.journeyControl().deboard(journey);

        // the newest first
        final java.util.List<String> texts = history();
        assertEquals("Paid " + price + " for the ticket", texts.get(0));
        assertEquals("Got off the flight at EGLL", texts.get(1));
        assertEquals("Found a flight OOOO to EGLL, the ticket is " + price, texts.get(2));
        assertEquals("Looking for a flight from Origin towards Target", texts.get(3));
        assertEquals("Started travelling by airline from Origin to Target", texts.get(4));
    }

    @Test
    public void theCheckInAndTheBoardingAreInTheHistory() {
        final Airports.Airport hub = airport("EGLL", city("Hub", 10), 10);
        final TransportFlights.Flight flight = flightTo(hub, 60);
        final Journeys.Journey journey = start();
        JourneyProcessor.process(world);
        flight.setStatus(TransportFlights.Status.CheckIn);

        world.journeyControl().checkin(journey);
        world.journeyControl().board(journey);

        assertEquals("Boarded the flight OOOO to EGLL", history().get(0));
        assertEquals("Checked in for the flight OOOO to EGLL", history().get(1));
    }

    @Test
    public void atAStopoverThePilotWaitsForTheSecondFlightAndItIsInTheHistory() {
        final Cities.City hubCity = city("Hub", 10);
        final Airports.Airport hub = airport("EGLL", hubCity, 10);
        final Airports.Airport targetAirport = airport("TTTT", target, 30);
        final TransportFlights.Flight first = flightTo(hub, 60);
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(aircraftType, "A-2000", hub);
        final int departure = world.getWorldTime() + 8 * Time.ONE_HOUR;
        final FlightMissions.Mission secondMission = world.flightMissions().createDispatchedMission(aircraft, hub, targetAirport, departure, departure + 2 * Time.ONE_HOUR);
        secondMission.setCharacterMode(FlightMissions.CharacterMode.PC);
        secondMission.setUserId(2);
        final TransportFlights.Flight second = world.transportFlightControl().createTransportFlight(secondMission);
        final Journeys.Journey journey = start();
        world.journeyControl().bookStopoverFlightsJourneyNoChecks(journey, first, second);
        world.flightMissions().byId(first.getFlightMissionId()).orElseThrow().setActualLandingAirportId(hub.getId());
        journey.setStatus(Journeys.Status.JustArrived);

        world.journeyControl().justArrived(journey);

        assertEquals("Waiting at EGLL for the next flight to TTTT", history().get(0));
        assertEquals(Journeys.Status.WaitingForCheckIn, journey.getStatus());
    }

    @Test
    public void aLandingWhichIsNotTheDestinationStartsTheSearchAgainAndIsInTheHistory() {
        final Cities.City hubCity = city("Hub", 10);
        final Journeys.Journey journey = start();
        journey.setStatus(Journeys.Status.ItinerariesDone);
        journey.setLocationCityId(hubCity.getId());
        journey.setHeartbeatTime(world.getWorldTime());

        JourneyProcessor.process(world);

        assertEquals("Looking for a flight from Hub towards Target", history().get(0));
    }

    @Test
    public void aFlightWhichTheAccountCannotPayIsSkippedAndToldOnceAnHour() {
        final Airports.Airport hub = airport("EGLL", city("Hub", 10), 10);
        flightTo(hub, 60);
        world.bankControl().transfer(pilotAccount.getId(), BankAccounts.SystemPassengersAccountId, 1000, BankTransactions.Type.Travel);
        final Journeys.Journey journey = start();

        JourneyProcessor.process(world);
        journey.setHeartbeatTime(world.getWorldTime());
        JourneyProcessor.process(world);

        assertEquals(Journeys.Status.LookingForTickets, journey.getStatus());
        assertEquals(0, journey.getTransportFlight1Id());
        assertTrue(history().get(0).startsWith("Could not book a flight OOOO to EGLL: not enough money for the ticket, "), history().get(0));
        assertEquals(1, history().stream().filter(t -> t.startsWith("Could not book")).count());
    }

    @Test
    public void aMissedFlightIsInTheHistory() {
        final Airports.Airport hub = airport("EGLL", city("Hub", 10), 10);
        final TransportFlights.Flight flight = flightTo(hub, 60);
        final Journeys.Journey journey = start();
        JourneyProcessor.process(world);
        assertEquals(flight.getId(), journey.getTransportFlight1Id());

        world.journeyControl().tooLateToBoard(journey);

        assertEquals("The flight OOOO to EGLL did not take place", history().get(0));
    }

    // ---- the lost flight and the lost pilot

    @Test
    public void aJourneyWhichHasLostItsFlightGoesOnLookingForTickets() {
        final Journeys.Journey journey = start();
        journey.setStatus(Journeys.Status.WaitingForCheckIn);
        journey.setTransportFlight1Id(999_999);
        journey.setHeartbeatTime(world.getWorldTime());

        JourneyProcessor.process(world);

        assertEquals(Journeys.Status.LookingForTickets, journey.getStatus());
        assertEquals(0, journey.getTransportFlight1Id());
        assertEquals(origin.getId(), journey.getLocationCityId());
        assertEquals(Persons.State.Travelling, pilot.getState());
    }

    @Test
    public void aJourneyOfAPilotWhoIsNotThereIsOrphaned() {
        final Journeys.Journey journey = world.journeyControl().createPilotJourney(origin.getId(), target.getId());

        JourneyProcessor.process(world);

        assertEquals(Journeys.Status.Orphaned, journey.getStatus());
    }

    @Test
    public void theJourneyOfAPilotDoesNotMoveTheFlowsOfTheCities() {
        world.city2cityFlows().createInactive(origin.getId(), target.getId());
        final float before = world.city2cityFlows().getFromCityIdToCityId(origin.getId(), target.getId()).orElseThrow().getSuccessRate();

        world.c2cFlowControl().updateSuccessRate(start(), -0.5f);

        assertEquals(before, world.city2cityFlows().getFromCityIdToCityId(origin.getId(), target.getId()).orElseThrow().getSuccessRate());
        assertFalse(Float.isNaN(before));
    }
}
