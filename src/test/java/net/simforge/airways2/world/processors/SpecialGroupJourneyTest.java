package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The special group (docs/special-group-journeys.md): an ordinary journey by the search and the bookings, but immortal. Instead of CouldNotFindTickets it chooses another random city,
 * instead of the return trip it goes to a random city, it is never Finished.
 * <p>
 * The geography is a line along the equator: the cities at the longitudes 0, 30, 60 and 90 (1800 miles between the neighbours), each has an airport.
 */
public class SpecialGroupJourneyTest {
    private World world;
    private AircraftTypes.AircraftType aircraftType;
    private Cities.City[] cities;
    private Airports.Airport[] airports;
    private int aircraftCounter;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        final Countries.Country country = world.countries().create("TT", "Testland");
        aircraftType = world.aircraftTypes().create("A320", "320");
        cities = new Cities.City[4];
        airports = new Airports.Airport[4];
        for (int i = 0; i < 4; i++) {
            cities[i] = world.cities().create(country.getId(), "City" + i, 0, i * 30, 1_000_000);
            final String icao = "TTT" + i;
            airports[i] = world.airports().create(0, i * 30, icao.substring(1), icao, icao);
            world.airport2city().create(airports[i].getId(), cities[i].getId());
        }
    }

    // a special group in the city 0 which goes to the city 1
    private Journeys.Journey group() {
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.LookingForTickets, cities[0].getId(), cities[1].getId(), 6, CabinLayout.Service.J);
        journey.setLocationCityId(cities[0].getId());
        journey.setLastKnownLocationCityId(cities[0].getId());
        journey.setSpecialGroup(true);
        journey.setHeartbeatTime(world.getWorldTime());
        return journey;
    }

    private void process(final Journeys.Journey journey) {
        journey.setHeartbeatTime(world.getWorldTime());
        JourneyProcessor.process(world);
    }

    private TransportFlights.Flight flight(final Airports.Airport from, final Airports.Airport to, final int departureInMinutes) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(aircraftType, "A-" + (1000 + aircraftCounter++), from);
        final int departureTime = world.getWorldTime() + departureInMinutes * Time.ONE_MINUTE;
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(aircraft, from, to, departureTime, departureTime + 2 * Time.ONE_HOUR);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        mission.setUserId(2);
        return world.transportFlightControl().createTransportFlight(mission);
    }

    // ---- the flag

    @Test
    public void theFlagIsAnotherBitOfTheStatusByteAndTheStatusIsIntact() {
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.LookingForTickets, cities[0].getId(), cities[1].getId(), 6, CabinLayout.Service.J);
        assertFalse(journey.isSpecialGroup());

        journey.setSpecialGroup(true);
        journey.setStatus(Journeys.Status.OnBoard);
        journey.setReturningBack(true);

        assertTrue(journey.isSpecialGroup());
        assertFalse(journey.isPilotJourney());
        assertEquals(Journeys.Status.OnBoard, journey.getStatus());
        assertTrue(journey.isReturningBack());
        journey.setSpecialGroup(false);
        assertFalse(journey.isSpecialGroup());
        assertTrue(journey.isReturningBack());
    }

    @Test
    public void aNewJourneyIsNotASpecialGroupEvenOnTheRecordOfADeletedOne() {
        final Journeys.Journey journey = group();
        world.journeys().deleteById(journey.getId());

        final Journeys.Journey next = world.journeys().create(Journeys.Status.LookingForTickets, cities[0].getId(), cities[1].getId(), 1, CabinLayout.Service.Y);

        assertFalse(next.isSpecialGroup());
    }

    // ---- no CouldNotFindTickets: another city

    @Test
    public void whenThereAreNoTicketsTheGroupChoosesAnotherCityInsteadOfCouldNotFindTickets() {
        final Journeys.Journey journey = group();

        // three attempts as of any journey, and the fourth one is the end of an ordinary journey
        for (int i = 0; i < 4; i++) {
            process(journey);
        }

        assertEquals(Journeys.Status.LookingForTickets, journey.getStatus());
        assertEquals(0, journey.getAttemptCounter());
        assertNotEquals(cities[1].getId(), journey.getToCityId());
        assertNotEquals(cities[0].getId(), journey.getToCityId());
        assertEquals(cities[0].getId(), journey.getFromCityId());
        assertEquals(cities[0].getId(), journey.getLocationCityId());
        assertFalse(journey.isReturningBack());
        // the search is soon
        final int wait = journey.getHeartbeatTime() - world.getWorldTime();
        assertTrue(wait >= 10 * Time.ONE_MINUTE && wait <= 2 * Time.ONE_HOUR, "wait " + wait);
    }

    @Test
    public void anOrdinaryJourneyWithNoTicketsStillEndsAsCouldNotFindTickets() {
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.LookingForTickets, cities[0].getId(), cities[1].getId(), 6, CabinLayout.Service.J);
        journey.setLocationCityId(cities[0].getId());

        for (int i = 0; i < 4; i++) {
            process(journey);
        }

        assertEquals(Journeys.Status.CouldNotFindTickets, journey.getStatus());
    }

    @Test
    public void theRandomCityIsNeverTheCityWhereTheGroupIs() {
        for (int i = 0; i < 50; i++) {
            final int cityId = world.journeyControl().randomCityOtherThan(cities[2].getId());
            assertNotEquals(cities[2].getId(), cityId);
        }
    }

    // ---- no return trip, no Finished

    @Test
    public void atTheDestinationTheGroupSleepsForDaysAndIsNotFinished() {
        final Journeys.Journey journey = group();
        journey.setStatus(Journeys.Status.ItinerariesDone);
        journey.setLocationCityId(cities[1].getId());

        process(journey);

        assertEquals(Journeys.Status.SpendingTheirTime, journey.getStatus());
        final int sleep = journey.getHeartbeatTime() - world.getWorldTime();
        assertTrue(sleep >= Time.ONE_DAY && sleep <= 7 * Time.ONE_DAY, "sleep " + sleep);
    }

    @Test
    public void afterTheSleepTheGroupGoesToARandomCityNotBackAndNotOnTheReturnDirection() {
        final Journeys.Journey journey = group();
        journey.setStatus(Journeys.Status.SpendingTheirTime);
        journey.setLocationCityId(cities[1].getId());

        process(journey);

        assertEquals(Journeys.Status.LookingForTickets, journey.getStatus());
        assertFalse(journey.isReturningBack());
        assertEquals(cities[1].getId(), journey.getFromCityId());
        assertEquals(cities[1].getId(), journey.getLocationCityId());
        assertNotEquals(cities[1].getId(), journey.getToCityId());
        assertEquals(0, journey.getAttemptCounter());
    }

    @Test
    public void aGroupWhichIsMarkedWhileReturningBackNeverFinishes() {
        final Journeys.Journey journey = group();
        journey.setReturningBack(true);
        journey.setStatus(Journeys.Status.ItinerariesDone);
        journey.setLocationCityId(cities[1].getId());

        process(journey);

        assertEquals(Journeys.Status.SpendingTheirTime, journey.getStatus());
        world.journeyControl().goToRandomCity(journey, "test");
        assertFalse(journey.isReturningBack());
    }

    @Test
    public void finishOfAGroupSendsItToSleepInstead() {
        final Journeys.Journey journey = group();
        journey.setStatus(Journeys.Status.ItinerariesDone);
        journey.setLocationCityId(cities[1].getId());

        world.journeyControl().finish(journey);

        assertEquals(Journeys.Status.SpendingTheirTime, journey.getStatus());
    }

    @Test
    public void aLostFlightDoesNotKillTheGroupItLooksForTicketsAgainFromTheLastKnownCity() {
        final Journeys.Journey journey = group();
        journey.setStatus(Journeys.Status.WaitingForCheckIn);

        world.journeyControl().orphaned(journey);

        assertEquals(Journeys.Status.LookingForTickets, journey.getStatus());
        assertEquals(0, journey.getTransportFlight1Id());
        assertEquals(cities[0].getId(), journey.getLocationCityId());
    }

    @Test
    public void aTerminalStatusOfAGroupIsBroughtBackToTheSearchNotCleanedUp() {
        for (final Journeys.Status terminal : List.of(Journeys.Status.Finished, Journeys.Status.CouldNotFindTickets, Journeys.Status.Orphaned)) {
            final Journeys.Journey journey = group();
            journey.setStatus(terminal);
            journey.setLocationCityId(cities[1].getId());
            journey.setToCityId(cities[1].getId());

            process(journey);

            assertTrue(world.journeys().byId(journey.getId()).isPresent(), terminal.name());
            assertEquals(Journeys.Status.LookingForTickets, journey.getStatus(), terminal.name());
            // it is in the city of the destination, so the destination is another one
            assertNotEquals(cities[1].getId(), journey.getToCityId(), terminal.name());
        }
    }

    // ---- an ordinary journey for the search and the bookings

    @Test
    public void theGroupBooksADirectFlightAsAnyJourney() {
        final TransportFlights.Flight flight = flight(airports[0], airports[1], 60);
        final Journeys.Journey journey = group();
        journey.setGroupSize(3);
        final int seatsBefore = flight.getRemainedTickets().get(CabinLayout.Service.J);

        process(journey);

        assertEquals(Journeys.Status.WaitingForCheckIn, journey.getStatus());
        assertEquals(flight.getId(), journey.getTransportFlight1Id());
        assertEquals(seatsBefore - 3, flight.getRemainedTickets().get(CabinLayout.Service.J));
    }

    // ---- the size and the class

    @Test
    public void theSizeAndTheClassAreSetWhileTheGroupHoldsNoSeats() {
        final Journeys.Journey journey = group();

        world.journeyControl().setSpecialGroupParams(journey, 7, CabinLayout.Service.F);

        assertEquals(7, journey.getGroupSize());
        assertEquals(CabinLayout.Service.F, journey.getPreferredCabinService());
        assertEquals(CabinLayout.Service.F, journey.getBookedCabinService());

        journey.setStatus(Journeys.Status.SpendingTheirTime);
        world.journeyControl().setSpecialGroupParams(journey, 5, CabinLayout.Service.J);
        assertEquals(5, journey.getGroupSize());
    }

    @Test
    public void theSizeAndTheClassAreNotChangedWhenTheGroupHoldsSeatsOrIsNotAGroup() {
        final Journeys.Journey journey = group();
        journey.setStatus(Journeys.Status.WaitingForBoarding);
        assertThrows(IllegalStateException.class, () -> world.journeyControl().setSpecialGroupParams(journey, 7, CabinLayout.Service.F));
        assertEquals(6, journey.getGroupSize());

        final Journeys.Journey ordinary = world.journeys().create(Journeys.Status.LookingForTickets, cities[0].getId(), cities[1].getId(), 6, CabinLayout.Service.J);
        assertThrows(IllegalStateException.class, () -> world.journeyControl().setSpecialGroupParams(ordinary, 7, CabinLayout.Service.F));
    }

    @Test
    public void theSizeIsFromOneToTheLimit() {
        final Journeys.Journey journey = group();
        assertThrows(IllegalArgumentException.class, () -> world.journeyControl().setSpecialGroupParams(journey, 0, CabinLayout.Service.F));
        assertThrows(IllegalArgumentException.class,
                () -> world.journeyControl().setSpecialGroupParams(journey, JourneyControl.SPECIAL_GROUP_MAX_SIZE + 1, CabinLayout.Service.F));
        world.journeyControl().setSpecialGroupParams(journey, JourneyControl.SPECIAL_GROUP_MAX_SIZE, CabinLayout.Service.Y);
        assertEquals(JourneyControl.SPECIAL_GROUP_MAX_SIZE, journey.getGroupSize());
    }

    // ---- the flag is set by the admin endpoint (JourneyController) through JourneyControl.setSpecialGroup

    @Test
    public void theFlagIsSetAndClearedByTheAdminAndTheReturnTripIsDropped() {
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.SpendingTheirTime, cities[0].getId(), cities[1].getId(), 6, CabinLayout.Service.J);
        journey.setReturningBack(true);

        world.journeyControl().setSpecialGroup(journey, true);

        assertTrue(journey.isSpecialGroup());
        assertFalse(journey.isReturningBack());
        // idempotent
        world.journeyControl().setSpecialGroup(journey, true);
        assertTrue(journey.isSpecialGroup());

        world.journeyControl().setSpecialGroup(journey, false);
        assertFalse(journey.isSpecialGroup());
        assertEquals(Journeys.Status.SpendingTheirTime, journey.getStatus());
    }
}
