package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.ShadowJetPricing;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The search of tickets of a journey (JourneyProcessor.lookingForTickets): a flight is found by its status, and a journey which has no direct flight and no
 * stopover route takes a flight to a hub which is closer to its destination (2026-10-30).
 * <p>
 * The geography is a line along the equator: the origin city is at the longitude 0, the hub cities at 10 and 15, the target city at 30, a city on the other side at -10.
 */
public class JourneyHubSearchTest {
    private World world;
    private Countries.Country country;
    private AircraftTypes.AircraftType aircraftType;
    private Cities.City origin;
    private Cities.City target;
    private Airports.Airport originAirport;
    private int aircraftCounter;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        country = world.countries().create("TT", "Testland");
        aircraftType = world.aircraftTypes().create("A320", "320");

        origin = city("Origin", 0);
        target = city("Target", 30);
        originAirport = airport("OOOO", origin, 0);
    }

    private Cities.City city(final String name, final int longitude) {
        return world.cities().create(country.getId(), name, 0, longitude, 1_000_000);
    }

    private Airports.Airport airport(final String icao, final Cities.City city, final int longitude) {
        final Airports.Airport airport = world.airports().create(0, longitude, icao.substring(1), icao, icao);
        world.airport2city().create(airport.getId(), city.getId());
        return airport;
    }

    // a flight which departs from the origin airport in the given minutes from now
    private TransportFlights.Flight flightTo(final Airports.Airport to, final int departureInMinutes) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(aircraftType, "A-" + (1000 + aircraftCounter++), originAirport);
        final int departureTime = world.getWorldTime() + departureInMinutes * Time.ONE_MINUTE;
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(aircraft, originAirport, to, departureTime, departureTime + 2 * Time.ONE_HOUR);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        return world.transportFlightControl().createTransportFlight(mission);
    }

    // a journey from the origin city to the target one wakes up and looks for tickets
    private Journeys.Journey wakeUpJourney() {
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.LookingForTickets, origin.getId(), target.getId(), 2, CabinLayout.Service.Y);
        journey.setLocationCityId(origin.getId());
        journey.setLastKnownLocationCityId(origin.getId());
        journey.setHeartbeatTime(world.getWorldTime());
        JourneyProcessor.process(world);
        return journey;
    }

    private void assertBookedTo(final Journeys.Journey journey, final TransportFlights.Flight flight) {
        assertEquals(Journeys.Status.WaitingForCheckIn, journey.getStatus());
        assertEquals(flight.getId(), journey.getTransportFlight1Id());
    }

    private void assertNotBooked(final Journeys.Journey journey) {
        assertEquals(Journeys.Status.LookingForTickets, journey.getStatus());
        assertEquals(0, journey.getTransportFlight1Id());
        assertEquals(1, journey.getAttemptCounter());
        assertTrue(journey.getHeartbeatTime() > world.getWorldTime());
    }

    @Test
    public void aFlightToAHubCloserToTheTargetIsTaken() {
        final Airports.Airport hub = airport("EGLL", city("Hub", 10), 10);
        final TransportFlights.Flight flight = flightTo(hub, 60);

        final Journeys.Journey journey = wakeUpJourney();

        assertBookedTo(journey, flight);
    }

    @Test
    public void ofTwoHubsTheOneWhichCutsTheDistanceMostIsTaken() {
        final Airports.Airport nearHub = airport("EGLL", city("Near hub", 10), 10);
        final Airports.Airport farHub = airport("EGKK", city("Far hub", 15), 15);
        flightTo(nearHub, 60);
        final TransportFlights.Flight better = flightTo(farHub, 90);

        final Journeys.Journey journey = wakeUpJourney();

        assertBookedTo(journey, better);
    }

    @Test
    public void ofEqualHubsTheEarlierFlightIsTaken() {
        final Airports.Airport hub = airport("EGLL", city("Hub", 10), 10);
        flightTo(hub, 120);
        final TransportFlights.Flight earlier = flightTo(hub, 60);
        flightTo(hub, 180);

        final Journeys.Journey journey = wakeUpJourney();

        assertBookedTo(journey, earlier);
    }

    @Test
    public void aHubWhichIsFurtherFromTheTargetIsNotTaken() {
        final Airports.Airport wrongSideHub = airport("EGLL", city("Wrong side hub", -10), -10);
        flightTo(wrongSideHub, 60);

        assertNotBooked(wakeUpJourney());
    }

    @Test
    public void anAirportWhichIsNotAHubIsNotTaken() {
        final Airports.Airport notAHub = airport("XXXX", city("Not a hub", 10), 10);
        flightTo(notAHub, 60);

        assertNotBooked(wakeUpJourney());
    }

    // the order of the search is: a hub flight, a direct flight, a stopover route
    @Test
    public void aHubFlightIsTakenBeforeADirectFlight() {
        final Airports.Airport hub = airport("EGLL", city("Hub", 10), 10);
        final TransportFlights.Flight hubFlight = flightTo(hub, 60);
        final Airports.Airport targetAirport = airport("TTTT", target, 30);
        flightTo(targetAirport, 120);

        final Journeys.Journey journey = wakeUpJourney();

        assertBookedTo(journey, hubFlight);
    }

    @Test
    public void aDirectFlightIsTakenWhenThereIsNoHubFlight() {
        final Airports.Airport targetAirport = airport("TTTT", target, 30);
        final TransportFlights.Flight direct = flightTo(targetAirport, 120);

        assertBookedTo(wakeUpJourney(), direct);
    }

    // the routes of two flights with a stopover work again (2026-10-30): every flight of the route has its own price by its own distance (price1 and price2), and each of
    // the flights of the pilots gets its price as the income
    @Test
    public void aStopoverRouteIsBookedAndEveryFlightHasItsOwnPriceAndIncome() {
        final Airports.Airport stopover = airport("XXXX", city("Stopover", 15), 15); // not a hub: the hub flight is not the way
        final Airports.Airport targetAirport = airport("TTTT", target, 30);
        final TransportFlights.Flight first = flightTo(stopover, 60);
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(aircraftType, "A-7777", stopover);
        final int departureTime = world.getWorldTime() + 8 * Time.ONE_HOUR; // later than the arrival of the first flight and the reserve of the connection
        final FlightMissions.Mission secondMission = world.flightMissions().createDispatchedMission(aircraft, stopover, targetAirport, departureTime, departureTime + 2 * Time.ONE_HOUR);
        secondMission.setCharacterMode(FlightMissions.CharacterMode.PC);
        final TransportFlights.Flight second = world.transportFlightControl().createTransportFlight(secondMission);

        final Journeys.Journey journey = wakeUpJourney(); // 2 passengers, Y

        assertEquals(Journeys.Status.WaitingForCheckIn, journey.getStatus());
        assertEquals(first.getId(), journey.getTransportFlight1Id());
        assertEquals(second.getId(), journey.getTransportFlight2Id());
        // every pair of airports has its own price: OOOO - XXXX is 15 degrees, XXXX - TTTT is 15 degrees too, so the prices are the same here, and not zero
        final double distance = net.simforge.commons.misc.Geo.distance(originAirport.getCoords(), stopover.getCoords());
        final int expected = ShadowJetPricing.price(distance, 2, CabinLayout.Service.Y);
        assertTrue(expected > 0);
        assertEquals(expected, journey.getPrice1());
        assertEquals(expected, journey.getPrice2());
        assertEquals(expected, world.transportFlightControl().getIncome(first));
        assertEquals(expected, world.transportFlightControl().getIncome(second));
    }

    // the price goes with the flight: when the journey comes to the stopover, price2 becomes price1 and the first one is paid
    @Test
    public void thePriceOfTheSecondFlightBecomesTheFirstWhenTheJourneyArrivesToTheStopover() {
        final Airports.Airport stopover = airport("XXXX", city("Stopover", 15), 15);
        final Airports.Airport targetAirport = airport("TTTT", target, 30);
        final TransportFlights.Flight first = flightTo(stopover, 60);
        world.flightMissions().byId(first.getFlightMissionId()).orElseThrow().setActualLandingAirportId(stopover.getId());
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(aircraftType, "A-7777", stopover);
        final int departureTime = world.getWorldTime() + 8 * Time.ONE_HOUR;
        final FlightMissions.Mission secondMission = world.flightMissions().createDispatchedMission(aircraft, stopover, targetAirport, departureTime, departureTime + 2 * Time.ONE_HOUR);
        secondMission.setCharacterMode(FlightMissions.CharacterMode.PC);
        final TransportFlights.Flight second = world.transportFlightControl().createTransportFlight(secondMission);
        final Journeys.Journey journey = wakeUpJourney();
        final int price2 = journey.getPrice2();
        assertTrue(price2 > 0);

        journey.setStatus(Journeys.Status.JustArrived);
        world.journeyControl().justArrived(journey);

        assertEquals(second.getId(), journey.getTransportFlight1Id());
        assertEquals(0, journey.getTransportFlight2Id());
        assertEquals(price2, journey.getPrice1());
        assertEquals(0, journey.getPrice2());
    }

    @Test
    public void aFlightWithoutSeatsOfTheClassIsNotTaken() {
        final Airports.Airport hub = airport("EGLL", city("Hub", 10), 10);
        final TransportFlights.Flight flight = flightTo(hub, 60);
        flight.setRemainedTickets(CabinLayout.Y(1)); // the group is of two

        assertNotBooked(wakeUpJourney());
    }

    // the flights are found by the status, not by the planned departure: a flight which stands at the gate after its planned time still takes the tickets
    @Test
    public void aFlightIsFoundWhateverItsPlannedDeparture() {
        final Airports.Airport targetAirport = airport("TTTT", target, 30);
        final TransportFlights.Flight soon = flightTo(targetAirport, 5);

        assertBookedTo(wakeUpJourney(), soon);
    }

    @Test
    public void aFlightWhichBoardingIsOverIsNotFound() {
        final Airports.Airport targetAirport = airport("TTTT", target, 30);
        final TransportFlights.Flight flight = flightTo(targetAirport, 60);
        flight.setStatus(TransportFlights.Status.WaitingForDeparture);

        assertNotBooked(wakeUpJourney());
    }

    @Test
    public void aJourneyWhichLandedAtAHubGoesOnFromThere() {
        // the second step of the way: the journey is at the hub and looks for a flight from there, the next hub is closer to the target still
        final Cities.City hubCity = city("Hub", 10);
        final Airports.Airport hubAirport = airport("EGLL", hubCity, 10);
        final Airports.Airport nextHub = airport("EGKK", city("Next hub", 20), 20);
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(aircraftType, "A-9999", hubAirport);
        final int departureTime = world.getWorldTime() + 60 * Time.ONE_MINUTE;
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(aircraft, hubAirport, nextHub, departureTime, departureTime + 2 * Time.ONE_HOUR);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        final TransportFlights.Flight flight = world.transportFlightControl().createTransportFlight(mission);

        final Journeys.Journey journey = world.journeys().create(Journeys.Status.LookingForTickets, origin.getId(), target.getId(), 2, CabinLayout.Service.Y);
        journey.setLocationCityId(hubCity.getId());
        journey.setLastKnownLocationCityId(hubCity.getId());
        journey.setHeartbeatTime(world.getWorldTime());
        JourneyProcessor.process(world);

        assertBookedTo(journey, flight);
    }

    // a journey with a stopover is reset: the second flight has not been boarded, so its price is not the income of that flight any more
    @Test
    public void aStopoverJourneyWhichIsResetTakesTheIncomeOfTheSecondFlightOff() {
        final Airports.Airport stopover = airport("XXXX", city("Stopover", 15), 15);
        final Airports.Airport targetAirport = airport("TTTT", target, 30);
        flightTo(stopover, 60);
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(aircraftType, "A-7777", stopover);
        final int departureTime = world.getWorldTime() + 8 * Time.ONE_HOUR;
        final FlightMissions.Mission secondMission = world.flightMissions().createDispatchedMission(aircraft, stopover, targetAirport, departureTime, departureTime + 2 * Time.ONE_HOUR);
        secondMission.setCharacterMode(FlightMissions.CharacterMode.PC);
        final TransportFlights.Flight second = world.transportFlightControl().createTransportFlight(secondMission);
        final Journeys.Journey journey = wakeUpJourney();
        assertTrue(world.transportFlightControl().getIncome(second) > 0);
        final int remainedBefore = second.getRemainedTickets().getTotal();

        world.journeyControl().resetJourneyForcefully(journey);

        assertEquals(0, journey.getTransportFlight2Id());
        assertEquals(0, journey.getPrice2());
        assertEquals(0, world.transportFlightControl().getIncome(second));
        assertEquals(remainedBefore + 2, second.getRemainedTickets().getTotal()); // the 2 seats of the journey are free again
    }
}
