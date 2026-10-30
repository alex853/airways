package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.ShadowJetPricing;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlightExtras;
import net.simforge.airways2.world.datamodel.TransportFlights;
import net.simforge.commons.misc.Geo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The income of a transport flight (the sum of the price of its journeys, TransportFlightExtras): every booking adds the price of the journey, the end of the boarding
 * counts it again from the journeys which are on board; only the flights of the pilots (PC) have it.
 */
class TransportFlightIncomeTest {
    private World world;
    private Cities.City cityA;
    private Cities.City cityB;
    private Aircrafts.Aircraft aircraft;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private TransportFlightControl tfControl;
    private PaxManager paxManager;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final Countries.Country country = world.countries().create("TT", "Testland");
        cityA = world.cities().create(country.getId(), "Alpha City", 0, 0, 1_000_000);
        cityB = world.cities().create(country.getId(), "Bravo City", 0, 10, 1_000_000);
        airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        airportB = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", airportA);
        tfControl = world.transportFlightControl();
        paxManager = world.paxManager();
    }

    // a transport flight of a mission of a pilot (PC) or of an NPC, in the check-in
    private TransportFlights.Flight transportFlight(final FlightMissions.CharacterMode mode) {
        final int departure = Time.START_TIME_EPOCH_SECONDS + 2 * Time.ONE_HOUR;
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, departure, departure + 2 * Time.ONE_HOUR);
        mission.setCharacterMode(mode);
        final TransportFlights.Flight flight = tfControl.createTransportFlight(mission);
        tfControl.startCheckIn(flight);
        return flight;
    }

    // a journey with a price which is booked to the flight, checked in
    private Journeys.Journey journey(final TransportFlights.Flight flight, final int groupSize, final int price) {
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.LookingForTickets, cityA.getId(), cityB.getId(), groupSize, CabinLayout.Service.Y);
        journey.setLocationCityId(cityA.getId());
        journey.setPrice(price);
        world.journeyControl().bookDirectFlightJourneyNoChecks(journey, flight);
        world.journeyControl().waitForCheckin(journey);
        world.journeyControl().checkin(journey);
        return journey;
    }

    private int income(final TransportFlights.Flight flight) {
        return tfControl.getIncome(flight);
    }

    @Test
    void everyBookingAddsThePriceOfTheJourneyToTheIncomeOfAFlightOfAPilot() {
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.PC);

        journey(flight, 10, 1_000);
        assertEquals(1_000, income(flight));
        journey(flight, 5, 2_500);
        assertEquals(3_500, income(flight));
    }

    @Test
    void theIncomeIsInTheExtraRecordOfTheFlight() {
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.PC);

        journey(flight, 10, 1_000);

        assertEquals(1_000, world.transportFlightExtras().byTransportFlightId(flight.getId()).orElseThrow().getIncome());
    }

    @Test
    void aFlightOfAnNpcHasNoIncomeAndNoRecord() {
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.NPC);

        journey(flight, 10, 1_000);

        assertEquals(0, income(flight));
        assertTrue(world.transportFlightExtras().byTransportFlightId(flight.getId()).isEmpty());
        assertEquals(0, world.transportFlightExtras().all().count());
    }

    @Test
    void journeysWithoutPriceMakeNoRecord() {
        // the journeys of ShadowJet have no price for now
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.PC);

        journey(flight, 10, 0);
        journey(flight, 20, 0);

        assertEquals(0, income(flight));
        assertTrue(world.transportFlightExtras().byTransportFlightId(flight.getId()).isEmpty());
    }

    @Test
    void theIncomeGoesDownWhenAJourneyLeavesTheFlightAndNotBelowZero() {
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.PC);
        journey(flight, 10, 1_000);

        tfControl.subtractIncome(flight, 400);
        assertEquals(600, income(flight));
        tfControl.subtractIncome(flight, 5_000);
        assertEquals(0, income(flight));
    }

    @Test
    void theIncomeWhichDoesNotFitIntoThreeBytesIsTheBiggestOneWhichFits() {
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.PC);

        journey(flight, 10, Journeys.MAX_PRICE);
        journey(flight, 10, Journeys.MAX_PRICE);

        assertEquals(TransportFlightExtras.MAX_INCOME, income(flight));
    }

    @Test
    void whenTheBoardingIsOverTheIncomeIsWhatReallyBoarded() {
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.PC);
        final List<Journeys.Journey> journeys = new ArrayList<>();
        journeys.add(journey(flight, 30, 1_000));
        journeys.add(journey(flight, 30, 2_000));
        journeys.add(journey(flight, 30, 4_000));
        // the bookings have added everything
        assertEquals(7_000, income(flight));

        tfControl.startBoarding(flight);
        // a minute of the boarding: only a part of the journeys is on board (15 passengers a minute)
        final int finishTime = world.getWorldTime() + 60;
        while (world.getWorldTime() < finishTime) {
            world.setWorldTime(world.getWorldTime() + 10);
            paxManager.boarding(flight).proceed();
        }
        tfControl.waitForDeparture(flight);

        final int boarded = journeys.stream()
                .filter(j -> j.getStatus() == Journeys.Status.OnBoard)
                .mapToInt(Journeys.Journey::getPrice)
                .sum();
        assertTrue(boarded < 7_000, "not everybody has boarded, the boarded: " + boarded);
        assertEquals(boarded, income(flight));
    }

    @Test
    void whenEverybodyBoardedTheIncomeIsTheSumOfThePrices() {
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.PC);
        journey(flight, 15, 1_000);
        journey(flight, 15, 2_000);

        tfControl.startBoarding(flight);
        for (int i = 0; i < 40 && paxManager.boarding(flight) != null && flight.getStatus() == TransportFlights.Status.Boarding; i++) {
            world.setWorldTime(world.getWorldTime() + 10);
            paxManager.boarding(flight).proceed();
            if (paxManager.areAllBoarded(flight)) {
                break;
            }
        }
        tfControl.waitForDeparture(flight);

        assertEquals(3_000, income(flight));
    }

    // ---- the price of a ShadowJet journey is set when it is booked to a flight of a pilot

    private static final int SHADOW_JET = net.simforge.airways2.worldbuilder.World25.ShadowJetOperatorId;
    // the airports are 10 degrees of the longitude apart on the equator, 600 nautical miles or so
    private double distanceNm() {
        return Geo.distance(airportA.getCoords(), airportB.getCoords());
    }

    private Journeys.Journey freshJourney(final int groupSize, final CabinLayout.Service service) {
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.LookingForTickets, cityA.getId(), cityB.getId(), groupSize, service);
        journey.setLocationCityId(cityA.getId());
        return journey;
    }

    @Test
    void aJourneyBookedToAShadowJetFlightOfAPilotGetsThePriceByTheDistanceThePassengersAndTheClass() {
        aircraft.setAircraftOperatorId(SHADOW_JET);
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.PC);
        final Journeys.Journey journey = freshJourney(10, CabinLayout.Service.J);

        world.journeyControl().bookDirectFlightJourneyNoChecks(journey, CabinLayout.Service.J, flight);

        final int expected = ShadowJetPricing.price(distanceNm(), 10, CabinLayout.Service.J);
        assertTrue(expected > 0);
        assertEquals(expected, journey.getPrice());
        assertEquals(expected, income(flight));
    }

    @Test
    void thePriceIsByTheActualClassNotByThePreferredOne() {
        aircraft.setAircraftOperatorId(SHADOW_JET);
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.PC);
        // the journey prefers economy, but the seats it got are business (an upgrade)
        final Journeys.Journey upgraded = freshJourney(5, CabinLayout.Service.Y);
        // and the other prefers business, but got economy (a downgrade)
        final Journeys.Journey downgraded = freshJourney(5, CabinLayout.Service.J);

        world.journeyControl().bookDirectFlightJourneyNoChecks(upgraded, CabinLayout.Service.J, flight);
        world.journeyControl().bookDirectFlightJourneyNoChecks(downgraded, CabinLayout.Service.Y, flight);

        assertEquals(ShadowJetPricing.price(distanceNm(), 5, CabinLayout.Service.J), upgraded.getPrice());
        assertEquals(ShadowJetPricing.price(distanceNm(), 5, CabinLayout.Service.Y), downgraded.getPrice());
        assertTrue(upgraded.getPrice() > downgraded.getPrice());
    }

    @Test
    void theIncomeOfAShadowJetFlightIsTheSumOfThePricesOfItsJourneys() {
        aircraft.setAircraftOperatorId(SHADOW_JET);
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.PC);
        final Journeys.Journey first = freshJourney(10, CabinLayout.Service.Y);
        final Journeys.Journey second = freshJourney(4, CabinLayout.Service.J);

        world.journeyControl().bookDirectFlightJourneyNoChecks(first, CabinLayout.Service.Y, flight);
        world.journeyControl().bookDirectFlightJourneyNoChecks(second, CabinLayout.Service.J, flight);

        assertEquals(ShadowJetPricing.price(distanceNm(), 10, CabinLayout.Service.Y) + ShadowJetPricing.price(distanceNm(), 4, CabinLayout.Service.J), income(flight));
    }

    @Test
    void aJourneyOnAShadowJetFlightOfAnNpcHasNoPrice() {
        aircraft.setAircraftOperatorId(SHADOW_JET);
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.NPC);
        final Journeys.Journey journey = freshJourney(10, CabinLayout.Service.Y);

        world.journeyControl().bookDirectFlightJourneyNoChecks(journey, CabinLayout.Service.Y, flight);

        assertEquals(0, journey.getPrice());
        assertEquals(0, income(flight));
        assertEquals(0, world.transportFlightExtras().all().count());
    }

    @Test
    void aJourneyOfBusyBirdsKeepsItsPriceAndItIsTheIncome() {
        // an operator which is not ShadowJet: the price which the journey has (the quote of the business charter) is the income, the formula of ShadowJet does not touch it
        aircraft.setAircraftOperatorId(3);
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.PC);
        final Journeys.Journey journey = freshJourney(4, CabinLayout.Service.J);
        journey.setPrice(55_000);

        world.journeyControl().bookDirectFlightJourneyNoChecks(journey, CabinLayout.Service.J, flight);

        assertEquals(55_000, journey.getPrice());
        assertEquals(55_000, income(flight));
    }

    @Test
    void aShadowJetJourneyWhichIsTooLateToBoardLosesThePriceButTheIncomeIsLeftToTheEndOfTheBoarding() {
        aircraft.setAircraftOperatorId(SHADOW_JET);
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.PC);
        final Journeys.Journey journey = journey(flight, 10, 0);
        final int booked = income(flight);
        assertTrue(journey.getPrice() > 0);
        assertTrue(booked > 0);

        world.journeyControl().tooLateToBoard(journey);

        // the price of the journey is cleared, it does not stay on a flight it is not on
        assertEquals(0, journey.getPrice());
        // the income is not touched by it: it is calculated at the end of the boarding, by the journeys which are on board (there are none)
        assertEquals(booked, income(flight));
        tfControl.recalculateIncome(flight);
        assertEquals(0, income(flight));
    }

    @Test
    void aJourneyWhichIsTooLateAfterTheCalculationOfTheIncomeDoesNotTakeItOffTwice() {
        aircraft.setAircraftOperatorId(SHADOW_JET);
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.PC);
        final Journeys.Journey boarded = journey(flight, 10, 0);
        final Journeys.Journey late = journey(flight, 10, 0);
        // the first journey is on board, the second has not boarded
        boarded.setStatus(Journeys.Status.OnBoard);
        tfControl.recalculateIncome(flight);
        final int boardedPrice = boarded.getPrice();
        assertEquals(boardedPrice, income(flight));

        // the second one is too late after the calculation: it was not in the income, the income stays
        world.journeyControl().tooLateToBoard(late);

        assertEquals(0, late.getPrice());
        assertEquals(boardedPrice, income(flight));
    }

    @Test
    void theEndOfTheBoardingOfAShadowJetFlightCountsTheJourneysWhichBoarded() {
        aircraft.setAircraftOperatorId(SHADOW_JET);
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.PC);
        final List<Journeys.Journey> journeys = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            journeys.add(journey(flight, 30, 0));
        }
        final int booked = income(flight);
        assertEquals(ShadowJetPricing.price(distanceNm(), 30, CabinLayout.Service.Y) * 4, booked);

        tfControl.startBoarding(flight);
        final int finishTime = world.getWorldTime() + 60;
        while (world.getWorldTime() < finishTime) {
            world.setWorldTime(world.getWorldTime() + 10);
            paxManager.boarding(flight).proceed();
        }
        tfControl.waitForDeparture(flight);

        final int boarded = journeys.stream().filter(j -> j.getStatus() == Journeys.Status.OnBoard).mapToInt(Journeys.Journey::getPrice).sum();
        assertTrue(boarded > 0 && boarded < booked, "boarded " + boarded + " of " + booked);
        assertEquals(boarded, income(flight));
    }

    @Test
    void aFlightOfAnNpcSkipsTheEndOfTheBoardingToo() {
        final TransportFlights.Flight flight = transportFlight(FlightMissions.CharacterMode.NPC);
        journey(flight, 15, 1_000);

        tfControl.startBoarding(flight);
        tfControl.waitForDeparture(flight);

        assertEquals(0, income(flight));
        assertEquals(0, world.transportFlightExtras().all().count());
    }
}
