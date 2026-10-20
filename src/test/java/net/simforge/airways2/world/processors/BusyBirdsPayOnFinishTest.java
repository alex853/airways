package net.simforge.airways2.world.processors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftOperators;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a BusyBirds flight is finished, its pilot gets paid. There are no accounts yet, so the payment is logged.
 */
class BusyBirdsPayOnFinishTest {
    private static final int USER_ID = 7;

    private World world;
    private FlightMissionControl control;
    private AircraftOperators.AircraftOperator busyBirds;
    private Cities.City fromCity;
    private Cities.City toCity;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private Aircrafts.Aircraft aircraft;
    private Persons.Person person;

    private Logger payLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        control = world.flightMissionControl();

        world.aircraftOperators().create("AA", "AAA", "first");
        world.aircraftOperators().create("SJ", "SJT", "second");
        busyBirds = world.aircraftOperators().create("BB", "BBD", "busyBirds");
        assertEquals(3, busyBirds.getId());

        final int countryId = world.countries().create("GB", "United Kingdom").getId();
        fromCity = world.cities().create(countryId, "From", 51.28, -0.76, 100000);
        toCity = world.cities().create(countryId, "To", 52.5, 13.4, 100000);
        airportA = world.airports().create(51.28, -0.76, "FRM", "EGLF", "Farnborough");
        airportB = world.airports().create(52.36, 13.5, "TOO", "EDDB", "Berlin");

        aircraft = world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", airportA);
        aircraft.setAircraftOperatorId(busyBirds.getId());

        person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, USER_ID,
                Persons.LocationType.Airport, airportA.getId(), fromCity.getId(), "AK", 0);

        payLogger = (Logger) LoggerFactory.getLogger(BusyBirdsMissionControl.class);
        appender = new ListAppender<>();
        appender.start();
        payLogger.addAppender(appender);
        payLogger.setLevel(Level.INFO);
    }

    @AfterEach
    public void afterEach() {
        payLogger.detachAppender(appender);
    }

    private FlightMissions.Mission mission(final Aircrafts.Aircraft forAircraft, final int userId, final int durationSeconds) {
        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(
                forAircraft, airportA, airportB, departure, departure + durationSeconds);
        mission.setUserId(userId);
        return mission;
    }

    private void finish(final FlightMissions.Mission mission) {
        mission.setActualLandingAirportId(airportB.getId());
        mission.setStatus(FlightMissions.Status.Postflight);
        control.finish(mission);
    }

    private List<String> paymentLog() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains("pilot gets paid"))
                .toList();
    }

    @Test
    public void test__repositioning_flight_pays_the_flight_hours_only() {
        final FlightMissions.Mission mission = mission(aircraft, USER_ID, 2 * 3600);

        finish(mission);

        assertEquals(List.of("p/s #" + person.getId() + ", f/m #" + mission.getId()
                + ", t/f none - BusyBirds flight, pilot gets paid 400 (flight pay 400, income share 0)"), paymentLog());
    }

    @Test
    public void test__revenue_flight_pays_the_flight_hours_and_3_percent_of_the_income() {
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.LookingForTickets, fromCity.getId(), toCity.getId(), 4, CabinLayout.Service.J);
        journey.setLocationCityId(fromCity.getId());
        // the list shows a rough pay, the exact income is the price which the quote sets
        final int income = world.busyBirdsMissionControl().quote(journey);

        final FlightMissions.Mission mission = mission(aircraft, USER_ID, 2 * 3600);
        final TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(mission);
        journey.setTransportFlight1Id(transportFlight.getId());
        // at the end of the flight the journey is at the destination
        journey.setStatus(Journeys.Status.ItinerariesDone);
        journey.setLocationCityId(toCity.getId());

        finish(mission);

        final int share = (int) Math.round(income * 0.03);
        assertTrue(share > 0);
        assertEquals(List.of("p/s #" + person.getId() + ", f/m #" + mission.getId() + ", t/f #" + transportFlight.getId()
                + " - BusyBirds flight, pilot gets paid " + (400 + share) + " (flight pay 400, income share " + share + ")"), paymentLog());
    }

    @Test
    public void test__income_share_is_zero_when_the_journey_has_already_paid_its_price() {
        // a technical limitation until the pilot contract: the journey has arrived and paid, its price went down to zero before the pilot finished the flight
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.LookingForTickets, fromCity.getId(), toCity.getId(), 4, CabinLayout.Service.J);
        journey.setLocationCityId(fromCity.getId());
        world.busyBirdsMissionControl().quote(journey);

        final FlightMissions.Mission mission = mission(aircraft, USER_ID, 2 * 3600);
        final TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(mission);
        journey.setTransportFlight1Id(transportFlight.getId());
        journey.setStatus(Journeys.Status.ItinerariesDone);
        journey.setLocationCityId(toCity.getId());
        journey.setPrice(0);

        finish(mission);

        assertEquals(List.of("p/s #" + person.getId() + ", f/m #" + mission.getId() + ", t/f #" + transportFlight.getId()
                + " - BusyBirds flight, pilot gets paid 400 (flight pay 400, income share 0)"), paymentLog());
    }

    @Test
    public void test__gulfstream_flight_is_paid_by_its_own_rate() {
        final Aircrafts.Aircraft g700 = world.aircrafts().create(world.aircraftTypes().create("GA7C", "GL7"), "BB-GLA", airportA);
        g700.setAircraftOperatorId(busyBirds.getId());
        final FlightMissions.Mission mission = mission(g700, USER_ID, 3600 + 1800);

        finish(mission);

        assertEquals(1, paymentLog().size());
        assertTrue(paymentLog().get(0).contains("pilot gets paid 750 (flight pay 750, income share 0)"), paymentLog().get(0));
    }

    @Test
    public void test__flight_of_another_operator_pays_nothing() {
        aircraft.setAircraftOperatorId(1);
        final FlightMissions.Mission mission = mission(aircraft, USER_ID, 2 * 3600);

        finish(mission);

        assertEquals(List.of(), paymentLog());
    }

    @Test
    public void test__busybirds_flight_without_the_pilot_person_pays_nobody() {
        final FlightMissions.Mission mission = mission(aircraft, 9, 2 * 3600);

        finish(mission);

        assertEquals(FlightMissions.Status.Finished, mission.getStatus());
        assertEquals(List.of(), paymentLog());
        assertEquals(Optional.empty(), world.busyBirdsMissionControl().payPilot(mission));
    }

    @Test
    public void test__cancelled_flight_pays_nothing() {
        final FlightMissions.Mission mission = mission(aircraft, USER_ID, 2 * 3600);
        assertTrue(control.start(mission).started());

        control.cancelFlightAndReturnAircraftToDepartureAirport(mission);

        assertEquals(List.of(), paymentLog());
    }

    @Test
    public void test__payment_does_not_depend_on_the_actual_time_of_the_flight() {
        final FlightMissions.Mission mission = mission(aircraft, USER_ID, 2 * 3600);
        mission.setActualDepartureWorldTime(world.getWorldTime());
        mission.setActualArrivalWorldTime(world.getWorldTime() + 5 * 3600);

        finish(mission);

        assertTrue(paymentLog().get(0).contains("pilot gets paid 400"), paymentLog().get(0));
    }
}
