package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PilotPayParams;
import net.simforge.airways2.world.datamodel.AircraftOperators;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Countries;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.PilotContracts;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.datamodel.PilotLogbook;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The aircraft of a flight of a pilot has landed not at the destination and is at the gate: the pilot flies on now (the same transport flight moves to a new mission) or cancels
 * the transportation (the passengers get off there). Nothing is paid for a flight which has not delivered the passengers.
 */
class ContinueAfterWrongLandingTest {
    private static final int INCOME = 10_000;

    private World world;
    private FlightMissionControl control;
    private AircraftOperators.AircraftOperator operator;
    private Airports.Airport departure;
    private Airports.Airport destination;
    private Airports.Airport diversion;
    private Aircrafts.Aircraft aircraft;
    private Persons.Person person;
    private FlightMissions.Mission mission;
    private TransportFlights.Flight transportFlight;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        control = world.flightMissionControl();

        operator = world.aircraftOperators().create("BB", "BBD", "busyBirds");
        departure = world.airports().create(51.5, -0.1, "AAA", "AAAA", "Alpha");
        destination = world.airports().create(51.5, 4.0, "BBB", "BBBB", "Bravo");
        diversion = world.airports().create(51.8, 2.0, "DDD", "DDDD", "Delta");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("A320", "320"), "A-BCDE", departure);
        aircraft.setAircraftOperatorId(operator.getId());
        final int departureTime = world.getWorldTime() + Time.ONE_HOUR;
        mission = world.flightMissions().createDispatchedMission(aircraft, departure, destination, departureTime, departureTime + Time.ONE_HOUR);
        mission.setUserId(7);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 7, Persons.LocationType.Airport, departure.getId(), 0, "AK", 0);
        world.pilotContracts().create(mission.getId(), operator.getId(), person.getId(), 2_000, PilotPayParams.none());

        transportFlight = world.transportFlightControl().createTransportFlight(mission);
        world.transportFlightControl().addIncome(transportFlight, INCOME);
    }

    // the flight is flown to the gate of the airport: blocks on, the transport flight waits for the deboarding
    private void flyTo(final FlightMissions.Mission m, final Airports.Airport landingAirport) {
        final FlightMissionControl.StartResult started = control.start(m);
        assertTrue(started.started(), started.reason());
        control.setTrackingMode(m, FlightMissions.TrackingMode.Manual);
        control.blocksOff(m);
        control.takeoff(m);
        control.landing(m, landingAirport);
        control.blocksOn(m);
    }

    @Test
    void theLandingAtADifferentAirportIsAWrongOne() {
        assertFalse(FlightMissionHelper.isLandedAtWrongAirport(mission));

        flyTo(mission, diversion);

        assertTrue(FlightMissionHelper.isLandedAtWrongAirport(mission));
        assertEquals(TransportFlights.Status.WaitingForDeboarding, transportFlight.getStatus());
        assertFalse(world.transportFlightControl().isDelivered(transportFlight));
    }

    @Test
    void theLandingAtTheDestinationIsNotAWrongOne() {
        flyTo(mission, destination);

        assertFalse(FlightMissionHelper.isLandedAtWrongAirport(mission));
        assertTrue(world.transportFlightControl().isDelivered(transportFlight));
        assertEquals(INCOME, world.transportFlightControl().getIncomeOfMission(mission));
    }

    @Test
    void theFlightGoesOnToTheDestinationInANewMissionWithTheSameTransportFlight() {
        flyTo(mission, diversion);

        final FlightMissions.Mission next = control.continueNow(mission);

        // the new mission: the same aircraft and pilot, from where the aircraft stands to the planned destination, not started yet
        assertEquals(FlightMissions.Status.Dispatched, next.getStatus());
        assertEquals(aircraft.getId(), next.getAircraftId());
        assertEquals(diversion.getId(), next.getDepartureAirportId());
        assertEquals(destination.getId(), next.getDestinationAirportId());
        assertEquals(7, next.getUserId());
        assertEquals(FlightMissions.CharacterMode.PC, next.getCharacterMode());
        assertTrue(next.getPlannedDepartureWorldTime() >= world.getWorldTime() + FlightMissionControl.CONTINUE_DEPARTURE_GAP_MINUTES * 60);

        // the transport flight is the same one and waits for the departure: no boarding again
        final Optional<TransportFlights.Flight> moved = world.transportFlights().byFlightMissionId(next.getId());
        assertEquals(Optional.of(transportFlight.getId()), moved.map(TransportFlights.Flight::getId));
        assertEquals(TransportFlights.Status.WaitingForDeparture, transportFlight.getStatus());
        assertEquals(Optional.empty(), world.transportFlights().byFlightMissionId(mission.getId()));

        // the first mission is finished in the ordinary way, the pilot is free at the airport of the landing
        assertEquals(FlightMissions.Status.Finished, mission.getStatus());
        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(diversion.getId(), person.getLocationId());
        assertEquals(1, world.pilotLogbook().filter(world.pilotLogbook().byPersonId(person.getId())).count());
    }

    @Test
    void theIncomeGoesWithTheTransportFlightToTheNewMission() {
        flyTo(mission, diversion);

        final FlightMissions.Mission next = control.continueNow(mission);

        assertEquals(0, world.transportFlightControl().getIncomeOfMission(mission));
        assertEquals(INCOME, world.transportFlightControl().getIncomeOfMission(next));
    }

    @Test
    void theNewMissionHasAContractOfTheSamePilot() {
        flyTo(mission, diversion);

        final FlightMissions.Mission next = control.continueNow(mission);

        final PilotContracts.Contract contract = world.pilotContracts().byFlightMissionId(next.getId()).orElseThrow();
        assertEquals(person.getId(), contract.getPersonId());
        assertEquals(operator.getId(), contract.getOperatorId());
        assertTrue(contract.getBaseFlightPay() > 0);
    }

    @Test
    void theNewMissionIsStartedAsAnyOther() {
        flyTo(mission, diversion);
        final FlightMissions.Mission next = control.continueNow(mission);

        final FlightMissionControl.StartResult started = control.start(next);
        assertTrue(started.started(), started.reason());
        control.setTrackingMode(next, FlightMissions.TrackingMode.Manual);
        control.blocksOff(next);
        control.takeoff(next);
        control.landing(next, destination);
        control.blocksOn(next);

        // it has delivered the passengers now
        assertTrue(world.transportFlightControl().isDelivered(transportFlight));
        assertEquals(TransportFlights.Status.WaitingForDeboarding, transportFlight.getStatus());
        assertEquals(INCOME, world.transportFlightControl().getIncomeOfMission(next));
    }

    @Test
    void theOperatorIsPaidForTheFlightWhichDeliveredTheyPassengersAndNotForTheOneWhichDidNot() {
        final BankAccounts accounts = world.bankAccounts();
        final int now = world.getWorldTime();
        accounts.create(BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK, true, now);
        accounts.create(BankAccounts.Kind.System, BankAccounts.SYSTEM_PASSENGERS, true, now);
        accounts.create(BankAccounts.Kind.Operator, operator.getId(), true, now);

        flyTo(mission, diversion);
        assertEquals(Optional.empty(), world.transportFlightControl().payOperator(transportFlight));

        final FlightMissions.Mission next = control.continueNow(mission);
        flyTo(next, destination);
        assertEquals(Optional.of(new TransportFlightControl.Payout(operator.getId(), INCOME)), world.transportFlightControl().payOperator(transportFlight));
    }

    @Test
    void theContinuationIsRefusedWhenTheFlightLandedAtTheDestination() {
        flyTo(mission, destination);

        assertThrows(IllegalArgumentException.class, () -> control.continueNow(mission));
    }

    @Test
    void theContinuationIsRefusedBeforeTheBlocksOn() {
        assertTrue(control.start(mission).started());

        assertThrows(IllegalArgumentException.class, () -> control.continueNow(mission));
    }

    @Test
    void theContinuationIsRefusedTwice() {
        flyTo(mission, diversion);
        control.continueNow(mission);

        assertThrows(IllegalArgumentException.class, () -> control.continueNow(mission));
    }

    @Test
    void cancellingTheTransportationDeboardsThePassengersAndNothingIsPaid() {
        flyTo(mission, diversion);

        world.transportFlightControl().cancelTransportation(transportFlight);

        assertEquals(TransportFlights.Status.Deboarding, transportFlight.getStatus());
        // the transport flight stays with the first mission, but the passengers are not delivered: no income share for the pilot, nothing for the operator
        assertEquals(0, world.transportFlightControl().getIncomeOfMission(mission));
        assertEquals(Optional.empty(), world.transportFlightControl().payOperator(transportFlight));
    }

    private PilotLogbook.Entry entry() {
        return world.pilotLogbook().filter(world.pilotLogbook().byPersonId(person.getId())).findFirst().orElseThrow();
    }

    @Test
    void aFlightWhichLandedAtTheDestinationHasNoLandingFlags() {
        flyTo(mission, destination);
        control.finish(mission);

        assertFalse(entry().isDiverted());
        assertFalse(entry().isUnplannedLanding());
        assertFalse(entry().isTransportationCancelled());
    }

    @Test
    void aLandingAtTheDeclaredDivertIsMarkedAsDeclaredAndTheContinuationIsNotACancellation() {
        assertTrue(control.start(mission).started());
        control.setTrackingMode(mission, FlightMissions.TrackingMode.Manual);
        control.blocksOff(mission);
        control.takeoff(mission);
        mission.setDivertAirportId(diversion.getId());
        control.landing(mission, diversion);
        control.blocksOn(mission);

        assertTrue(FlightMissionHelper.isLandedAtWrongAirport(mission));
        assertFalse(FlightMissionHelper.isLandedAtUnplannedAirport(mission));
        control.continueNow(mission);

        assertTrue(entry().isDiverted());
        assertFalse(entry().isUnplannedLanding());
        assertFalse(entry().isTransportationCancelled());
    }

    @Test
    void aLandingElsewhereWithoutTheDeclarationIsUnplanned() {
        flyTo(mission, diversion);

        assertTrue(FlightMissionHelper.isLandedAtUnplannedAirport(mission));
        control.continueNow(mission);

        // an unplanned landing has both flags: diverted and unplanned
        assertTrue(entry().isDiverted());
        assertTrue(entry().isUnplannedLanding());
        assertFalse(entry().isTransportationCancelled());
    }

    @Test
    void cancellingTheTransportationIsMarkedInTheLogbook() {
        assertTrue(control.start(mission).started());
        control.setTrackingMode(mission, FlightMissions.TrackingMode.Manual);
        control.blocksOff(mission);
        control.takeoff(mission);
        mission.setDivertAirportId(diversion.getId());
        control.landing(mission, diversion);
        control.blocksOn(mission);

        world.transportFlightControl().cancelTransportation(transportFlight);
        control.finish(mission);

        assertTrue(entry().isDiverted());
        assertFalse(entry().isUnplannedLanding());
        assertTrue(entry().isTransportationCancelled());
    }

    // a journey of the flight which is on board, from the city of the departure to the city of the destination; the diversion airport has a city of its own
    private Journeys.Journey journeyOnBoard(final Cities.City from, final Cities.City to) {
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.OnBoard, from.getId(), to.getId(), 2, CabinLayout.Service.Y);
        journey.setLocationCityId(from.getId());
        journey.setLastKnownLocationCityId(from.getId());
        journey.setTransportFlight1Id(transportFlight.getId());
        return journey;
    }

    @Test
    void aContinuedFlightWhichIsCancelledCancelsTheTransportationOfTheFirstOne() {
        flyTo(mission, diversion);
        final FlightMissions.Mission next = control.continueNow(mission);
        assertTrue(next.isContinuation());
        assertFalse(entry().isTransportationCancelled());

        control.cancelDispatched(next, "the pilot does not fly on");

        assertTrue(entry().isTransportationCancelled());
        assertEquals(TransportFlights.Status.Cancelled, transportFlight.getStatus());
    }

    @Test
    void aFlightWhichIsNotAContinuationLeavesTheLogbookAlone() {
        flyTo(mission, destination);
        control.finish(mission);
        final FlightMissions.Mission other = world.flightMissions().createDispatchedMission(aircraft, destination, departure,
                world.getWorldTime() + Time.ONE_HOUR, world.getWorldTime() + 2 * Time.ONE_HOUR);
        other.setUserId(7);
        other.setCharacterMode(FlightMissions.CharacterMode.PC);

        control.cancelDispatched(other, "test");

        assertFalse(entry().isTransportationCancelled());
    }

    @Test
    void aCancellationLeavesThePassengersWhereTheAircraftStands() {
        final Countries.Country country = world.countries().create("TT", "Testland");
        final Cities.City cityDeparture = world.cities().create(country.getId(), "Alpha City", 51.5f, -0.1f, 1_000_000);
        final Cities.City cityDestination = world.cities().create(country.getId(), "Bravo City", 51.5f, 4.0f, 1_000_000);
        final Cities.City cityDiversion = world.cities().create(country.getId(), "Delta City", 51.8f, 2.0f, 1_000_000);
        world.airport2city().create(departure.getId(), cityDeparture.getId());
        world.airport2city().create(destination.getId(), cityDestination.getId());
        world.airport2city().create(diversion.getId(), cityDiversion.getId());
        final Journeys.Journey journey = journeyOnBoard(cityDeparture, cityDestination);
        flyTo(mission, diversion);
        final FlightMissions.Mission next = control.continueNow(mission);

        control.cancelDispatched(next, "the pilot does not fly on");

        assertEquals(Journeys.Status.LookingForTickets, journey.getStatus());
        assertEquals(cityDiversion.getId(), journey.getLocationCityId());
    }

    @Test
    void cancellingIsRefusedWhenThePassengersAreDelivered() {
        flyTo(mission, destination);

        assertThrows(IllegalArgumentException.class, () -> world.transportFlightControl().cancelTransportation(transportFlight));
        assertNotEquals(TransportFlights.Status.Deboarding, transportFlight.getStatus());
    }
}
