package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PilotPayParams;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.datamodel.PilotContracts;
import net.simforge.airways2.world.datamodel.TransportFlights;
import net.simforge.airways2.worldbuilder.World25;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The booking of a ShadowJet flight by a pilot: the aircraft is reserved, the flight, the contract and the transport flight with the passengers are made, the check-in comes
 * in its own time.
 */
class ShadowJetBookingTest {
    private static final int USER_ID = 7;
    private static final int OTHER_USER_ID = 8;
    private static final long HOUR_MILLIS = 3600_000L;

    private World world;
    private Cities.City cityA;
    private Cities.City cityB;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private Persons.Person pilot;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        // the ShadowJet operator is the second one
        world.aircraftOperators().create("AA", "AAA", "first");
        world.aircraftOperators().create("SJ", "SJT", "ShadowJet");
        assertEquals(World25.ShadowJetOperatorId, 2);

        final Countries.Country country = world.countries().create("TT", "Testland");
        cityA = world.cities().create(country.getId(), "Alpha City", 51.0, 0.0, 1_000_000);
        cityB = world.cities().create(country.getId(), "Bravo City", 52.0, 8.0, 1_000_000);
        airportA = world.airports().create(51.0, 0.0, "AAA", "EGAA", "Alpha");
        airportB = world.airports().create(52.0, 8.0, "BBB", "EDBB", "Bravo");
        world.airport2city().create(airportA.getId(), cityA.getId());
        world.airport2city().create(airportB.getId(), cityB.getId());
        world.aircraftTypes().create("A320", "320");

        pilot = pilot(USER_ID);
    }

    private Persons.Person pilot(final int userId) {
        return world.persons().create(Persons.Type.Pilot, Persons.State.Idle, userId, Persons.LocationType.Airport, airportA.getId(), cityA.getId(), "P" + userId, 0);
    }

    // the passengers who wait for a flight from A to B
    private void passengers(final int journeys, final int groupSize) {
        for (int i = 0; i < journeys; i++) {
            final Journeys.Journey journey = world.journeys().create(Journeys.Status.LookingForTickets, cityA.getId(), cityB.getId(), groupSize, CabinLayout.Service.Y);
            journey.setLocationCityId(cityA.getId());
            journey.setLastKnownLocationCityId(cityA.getId());
        }
    }

    private long inHours(final double hours) {
        return (long) ((world.getWorldTime() / 60 * 60) * 1000L + hours * HOUR_MILLIS);
    }

    private ShadowJetBooking.Result book(final int userId, final Persons.Person person, final String from, final String to, final long departureMillis, final String aircraftRegNo) {
        return ShadowJetBooking.book(world, userId, person.getId(), PilotPayParams.none(), "A320", from, to, departureMillis, aircraftRegNo);
    }

    private ShadowJetBooking.Result book(final long departureMillis) {
        return book(USER_ID, pilot, "EGAA", "EDBB", departureMillis, null);
    }

    private Optional<TransportFlights.Flight> transportFlightOf(final FlightMissions.Mission mission) {
        return world.transportFlights().byFlightMissionId(mission.getId());
    }

    @Test
    void aFlightOfThePilotIsMadeWithTheAircraftAtTheAirportOfTheDeparture() {
        final ShadowJetBooking.Result result = book(inHours(6));

        assertTrue(result.booked(), result.problems().toString());
        final FlightMissions.Mission mission = result.mission();
        assertEquals(FlightMissions.Status.Dispatched, mission.getStatus());
        assertEquals(FlightMissions.CharacterMode.PC, mission.getCharacterMode());
        assertEquals(USER_ID, mission.getUserId());
        assertEquals(airportA.getId(), mission.getDepartureAirportId());
        assertEquals(airportB.getId(), mission.getDestinationAirportId());
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        assertEquals(World25.ShadowJetOperatorId, aircraft.getAircraftOperatorId());
        assertEquals(airportA.getId(), aircraft.getLocationAirportId());
        assertEquals("A320", world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow().getIcao());
    }

    @Test
    void theContractHasTheFlightPayOfShadowJet() {
        final FlightMissions.Mission mission = book(inHours(6)).mission();

        final PilotContracts.Contract contract = world.pilotContracts().byFlightMissionId(mission.getId()).orElseThrow();
        assertEquals(World25.ShadowJetOperatorId, contract.getOperatorId());
        assertEquals(pilot.getId(), contract.getPersonId());
        // v100 an hour of the planned time of the flight
        final int plannedSeconds = mission.getPlannedArrivalWorldTime() - mission.getPlannedDepartureWorldTime();
        assertEquals(Math.round(100 * plannedSeconds / 3600.0), contract.getBaseFlightPay());
    }

    @Test
    void aFlightWhichIsFarAwayHasAScheduledTransportFlightAndThePassengersWaitForTheCheckIn() {
        passengers(40, 4);

        final FlightMissions.Mission mission = book(inHours(6)).mission();

        final TransportFlights.Flight flight = transportFlightOf(mission).orElseThrow();
        // the check-in opens two hours before the departure, it is not open yet
        assertEquals(TransportFlights.Status.Scheduled, flight.getStatus());
        // the passengers are not booked at once: a pack of journeys is woken by the heartbeat by the curve (JourneyWakeCurve), they find the flight and book it by themselves
        assertTrue(world.journeys().all().noneMatch(j -> j.getTransportFlight1Id() == flight.getId()));
        final List<Journeys.Journey> pack = world.journeys().all().filter(j -> j.getHeartbeatTime() > 0).toList();
        assertFalse(pack.isEmpty());

        pack.forEach(j -> j.setHeartbeatTime(world.getWorldTime()));
        JourneyProcessor.process(world);

        final List<Journeys.Journey> booked = world.journeys().all().filter(j -> j.getTransportFlight1Id() == flight.getId()).toList();
        assertFalse(booked.isEmpty());
        assertTrue(booked.stream().allMatch(j -> j.getStatus() == Journeys.Status.WaitingForCheckIn), "everybody waits for the check-in");
        assertTrue(world.transportFlightControl().getIncome(flight) > 0, "the prices of the journeys of a flight of a pilot are its income");
    }

    @Test
    void aFlightWhichIsNearOpensTheCheckInAtOnceAndTheBoardingIsForThePilot() {
        passengers(40, 4);

        final FlightMissions.Mission mission = book(inHours(0.75)).mission();

        final TransportFlights.Flight flight = transportFlightOf(mission).orElseThrow();
        assertEquals(TransportFlights.Status.CheckIn, flight.getStatus());

        // the journeys of the pack wake up and check in at once, the check-in is open
        world.journeys().all().filter(j -> j.getHeartbeatTime() > 0).toList().forEach(j -> j.setHeartbeatTime(world.getWorldTime()));
        JourneyProcessor.process(world);

        assertTrue(world.journeys().all().anyMatch(j -> j.getTransportFlight1Id() == flight.getId() && j.getStatus() == Journeys.Status.WaitingForBoarding),
                "the journeys which have woken up are checked in");
    }

    @Test
    void aFlightWithoutPassengersAvailableStillGetsATransportFlight() {
        final FlightMissions.Mission mission = book(inHours(6)).mission();

        final TransportFlights.Flight flight = transportFlightOf(mission).orElseThrow();
        assertEquals(0, world.transportFlightControl().getIncome(flight));
    }

    @Test
    void theAircraftOfABookedFlightIsReservedAndNobodyElseGetsIt() {
        final FlightMissions.Mission first = book(inHours(6)).mission();

        // another pilot books the same type at the same airport at the same time: the aircraft of the first flight is not free, another one is made
        final Persons.Person otherPilot = pilot(OTHER_USER_ID);
        final ShadowJetBooking.Result second = book(OTHER_USER_ID, otherPilot, "EGAA", "EDBB", inHours(6), null);

        assertTrue(second.booked(), second.problems().toString());
        assertNotEquals(first.getAircraftId(), second.mission().getAircraftId());
        assertEquals(2, world.aircrafts().all().count());
    }

    @Test
    void anAircraftWhichIsFreeAgainIsUsedAgain() {
        final FlightMissions.Mission first = book(inHours(6)).mission();
        first.setStatus(FlightMissions.Status.Cancelled);

        final Persons.Person otherPilot = pilot(OTHER_USER_ID);
        final ShadowJetBooking.Result second = book(OTHER_USER_ID, otherPilot, "EGAA", "EDBB", inHours(6), null);

        assertEquals(first.getAircraftId(), second.mission().getAircraftId());
        assertEquals(1, world.aircrafts().all().count());
    }

    @Test
    void theStateOfThePilotIsNotLookedAt() {
        // a sleeping or travelling or working pilot books as well: it is the pilot's business to be ready when the flight starts
        for (final Persons.State state : Persons.State.values()) {
            final Persons.Person person = world.persons().create(Persons.Type.Pilot, state, 100 + state.code(), Persons.LocationType.Airport, airportA.getId(), cityA.getId(), "AK", 0);

            final ShadowJetBooking.Result result = book(100 + state.code(), person, "EGAA", "EDBB", inHours(6 + 10 * state.code()), null);

            assertTrue(result.booked(), state + ": " + result.problems());
        }
    }

    @Test
    void aDepartureEarlierThanThirtyMinutesFromNowIsRefused() {
        final ShadowJetBooking.Result result = book(inHours(0.2));

        assertFalse(result.booked());
        assertTrue(result.problems().get(0).contains("too soon"), result.problems().toString());
        assertEquals(0, world.flightMissions().all().count());
    }

    @Test
    void aFlightTooCloseToAnotherFlightOfThePilotIsRefused() {
        final FlightMissions.Mission first = book(inHours(6)).mission();
        final long firstArrival = first.getPlannedArrivalWorldTime() * 1000L;

        final ShadowJetBooking.Result tooClose = book(USER_ID, pilot, "EDBB", "EGAA", firstArrival + 10 * 60_000L, null);
        final ShadowJetBooking.Result fine = book(USER_ID, pilot, "EDBB", "EGAA", firstArrival + 30 * 60_000L, null);

        assertFalse(tooClose.booked());
        assertTrue(tooClose.problems().get(0).contains("too close"), tooClose.problems().toString());
        assertTrue(fine.booked(), fine.problems().toString());
    }

    @Test
    void anUnknownTypeAirportOrTheSameAirportIsRefused() {
        assertFalse(ShadowJetBooking.book(world, USER_ID, pilot.getId(), PilotPayParams.none(), "ZZZZ", "EGAA", "EDBB", inHours(6), null).booked());
        assertFalse(book(USER_ID, pilot, "XXXX", "EDBB", inHours(6), null).booked());
        assertFalse(book(USER_ID, pilot, "EGAA", "XXXX", inHours(6), null).booked());
        assertFalse(book(USER_ID, pilot, "EGAA", "EGAA", inHours(6), null).booked());
        assertEquals(0, world.flightMissions().all().count());
        assertEquals(0, world.aircrafts().all().count());
    }

    @Test
    void aFlightGoesOnAfterAFlightOfThePilotOnTheSameAircraft() {
        final FlightMissions.Mission first = book(inHours(6)).mission();
        final String regNo = world.aircrafts().byId(first.getAircraftId()).orElseThrow().getRegNo();
        final long departure = first.getPlannedArrivalWorldTime() * 1000L + 40 * 60_000L;

        final ShadowJetBooking.Result second = book(USER_ID, pilot, "EDBB", "EGAA", departure, regNo);

        assertTrue(second.booked(), second.problems().toString());
        assertEquals(first.getAircraftId(), second.mission().getAircraftId());
        assertEquals(1, world.aircrafts().all().count());
        assertTrue(world.pilotContracts().byFlightMissionId(second.mission().getId()).isPresent());
    }

    // the pilot books the next flight while the previous one is in progress (the preflight, the flying, the postflight): it is still the flight of the aircraft until it is finished
    @Test
    void aFlightGoesOnAfterAFlightWhichIsInProgress() {
        final FlightMissions.Mission first = book(inHours(6)).mission();
        final String regNo = world.aircrafts().byId(first.getAircraftId()).orElseThrow().getRegNo();
        final long departure = first.getPlannedArrivalWorldTime() * 1000L + 40 * 60_000L;

        for (final FlightMissions.Status status : List.of(FlightMissions.Status.Preflight, FlightMissions.Status.Flying, FlightMissions.Status.Postflight)) {
            first.setStatus(status);

            final ShadowJetBooking.Result second = book(USER_ID, pilot, "EDBB", "EGAA", departure, regNo);

            assertTrue(second.booked(), status + ": " + second.problems());
            second.mission().setStatus(FlightMissions.Status.Cancelled); // the next round books it again
        }
    }

    @Test
    void aFinishedFlightIsNotAFlightAheadSoTheAircraftCanNotBeContinued() {
        final FlightMissions.Mission first = book(inHours(6)).mission();
        final String regNo = world.aircrafts().byId(first.getAircraftId()).orElseThrow().getRegNo();
        final long departure = first.getPlannedArrivalWorldTime() * 1000L + 40 * 60_000L;
        first.setStatus(FlightMissions.Status.Finished);

        final ShadowJetBooking.Result result = book(USER_ID, pilot, "EDBB", "EGAA", departure, regNo);

        assertFalse(result.booked());
        assertTrue(result.problems().get(0).contains("not yours"), result.problems().toString());
    }

    @Test
    void theContinuationMustStartWhereTheLastFlightOfTheAircraftEnds() {
        final FlightMissions.Mission first = book(inHours(6)).mission();
        final String regNo = world.aircrafts().byId(first.getAircraftId()).orElseThrow().getRegNo();
        final long departure = first.getPlannedArrivalWorldTime() * 1000L + 40 * 60_000L;

        // the first flight ends at B, but the second one starts at A
        final ShadowJetBooking.Result result = book(USER_ID, pilot, "EGAA", "EDBB", departure, regNo);

        assertFalse(result.booked());
        assertTrue(result.problems().get(0).contains("where the last flight"), result.problems().toString());
    }

    @Test
    void theAircraftOfAnotherPilotCanNotBeContinued() {
        final FlightMissions.Mission first = book(inHours(6)).mission();
        final String regNo = world.aircrafts().byId(first.getAircraftId()).orElseThrow().getRegNo();
        final long departure = first.getPlannedArrivalWorldTime() * 1000L + 40 * 60_000L;
        final Persons.Person otherPilot = pilot(OTHER_USER_ID);

        final ShadowJetBooking.Result result = book(OTHER_USER_ID, otherPilot, "EDBB", "EGAA", departure, regNo);

        assertFalse(result.booked());
        assertTrue(result.problems().get(0).contains("not yours"), result.problems().toString());
    }

    @Test
    void anUnknownAircraftOrAnAircraftWithoutFlightsCanNotBeContinued() {
        assertFalse(book(USER_ID, pilot, "EGAA", "EDBB", inHours(6), "SJ-NONE").booked());
    }

    @Test
    void theFinishedFlightPaysThePilotByTheRatesOfShadowJet() {
        final FlightMissions.Mission mission = book(inHours(6)).mission();
        final PilotContracts.Contract contract = world.pilotContracts().byFlightMissionId(mission.getId()).orElseThrow();
        final TransportFlights.Flight flight = transportFlightOf(mission).orElseThrow();
        // the income of the flight, the pilot has no currency and level 0: 90% of 0.1% of the income, 70% of the flight pay
        world.transportFlightExtras().create(flight.getId()).setIncome(100_000);

        final var salary = world.busyBirdsMissionControl().payPilot(mission).orElseThrow();

        assertEquals(Math.round(contract.getBaseFlightPay() * 0.7), salary.flightPay());
        assertEquals(90, salary.incomeShare());
    }
}
