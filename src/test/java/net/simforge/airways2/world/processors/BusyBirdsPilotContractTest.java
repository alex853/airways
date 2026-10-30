package net.simforge.airways2.world.processors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.simforge.airways2.app.user.PilotController;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PilotPayParams;
import net.simforge.airways2.world.datamodel.AircraftOperators;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.PilotContracts;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contracts of the BusyBirds pilot: a contract per flight of a booked plan, it is paid when the flight is finished and removed with the flight mission.
 */
class BusyBirdsPilotContractTest {
    private static final int USER_ID = 7;

    private World world;
    private AircraftOperators.AircraftOperator busyBirds;
    private Cities.City fromCity;
    private Cities.City toCity;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private Aircrafts.Aircraft aircraft;
    private Persons.Person person;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);

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

        logger = (Logger) LoggerFactory.getLogger(BusyBirdsMissionControl.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    public void afterEach() {
        logger.detachAppender(appender);
    }

    private static PilotPayParams allGood(final int level) {
        final PilotPayParams params = PilotPayParams.none();
        params.setTypeCurrency(true);
        params.setNightCurrency(true);
        params.setCanCarryPassengers(true);
        params.setExperienceLevel(level);
        return params;
    }

    private FlightMissions.Mission mission(final int durationSeconds) {
        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(
                aircraft, airportA, airportB, departure, departure + durationSeconds);
        mission.setUserId(USER_ID);
        return mission;
    }

    private Journeys.Journey journeyWithPrice() {
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.LookingForTickets, fromCity.getId(), toCity.getId(), 4, CabinLayout.Service.J);
        journey.setLocationCityId(fromCity.getId());
        world.busyBirdsMissionControl().quote(journey);
        return journey;
    }

    // the journey is booked to the transport flight of the mission of the pilot: its price is the income of the flight, the pay takes the income from there
    private void bookJourney(final FlightMissions.Mission mission, final Journeys.Journey journey) {
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        final net.simforge.airways2.world.datamodel.TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(mission);
        world.transportFlightControl().addIncome(transportFlight, journey.getPrice1());
    }

    private void finish(final FlightMissions.Mission mission) {
        mission.setActualLandingAirportId(airportB.getId());
        mission.setStatus(FlightMissions.Status.Postflight);
        world.flightMissionControl().finish(mission);
    }

    // the bank: the bank and the passengers (the system accounts), the account of the operator busyBirds and the account of the pilot, which the person refers to
    private net.simforge.airways2.world.datamodel.BankAccounts.BankAccount operatorAccount;
    private net.simforge.airways2.world.datamodel.BankAccounts.BankAccount pilotAccount;

    private void createAccounts(final boolean personRefersToItsAccount) {
        final net.simforge.airways2.world.datamodel.BankAccounts accounts = world.bankAccounts();
        final int now = world.getWorldTime();
        accounts.create(net.simforge.airways2.world.datamodel.BankAccounts.Kind.System, net.simforge.airways2.world.datamodel.BankAccounts.SYSTEM_BANK, true, now);
        accounts.create(net.simforge.airways2.world.datamodel.BankAccounts.Kind.System, net.simforge.airways2.world.datamodel.BankAccounts.SYSTEM_PASSENGERS, true, now);
        operatorAccount = accounts.create(net.simforge.airways2.world.datamodel.BankAccounts.Kind.Operator, busyBirds.getId(), true, now);
        pilotAccount = accounts.create(net.simforge.airways2.world.datamodel.BankAccounts.Kind.Person, person.getId(), false, now);
        if (personRefersToItsAccount) {
            person.setBankAccountId(pilotAccount.getId());
        }
    }

    private List<String> paymentLog() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains("pilot gets paid"))
                .toList();
    }

    @Test
    public void test__contract_of_a_reposition_leg_has_the_flight_pay_and_no_income() {
        final FlightMissions.Mission mission = mission(2 * 3600);

        final PilotContracts.Contract contract = world.busyBirdsMissionControl().createContract(mission, person.getId(), allGood(2));

        assertEquals(mission.getId(), contract.getFlightMissionId());
        assertEquals(busyBirds.getId(), contract.getOperatorId());
        assertEquals(person.getId(), contract.getPersonId());
        // C25C: 200 per hour, two hours
        assertEquals(400, contract.getBaseFlightPay());
        assertEquals(2, contract.getPayParams().getExperienceLevel());
        assertTrue(world.pilotContracts().byFlightMissionId(mission.getId()).isPresent());
    }

    @Test
    public void test__the_income_of_a_revenue_flight_is_the_price_of_its_journey_in_the_transport_flight_not_in_the_contract() {
        final Journeys.Journey journey = journeyWithPrice();
        final FlightMissions.Mission mission = mission(2 * 3600);

        world.busyBirdsMissionControl().createContract(mission, person.getId(), allGood(3));
        bookJourney(mission, journey);

        assertTrue(journey.getPrice1() > 0);
        assertEquals(journey.getPrice1(), world.transportFlightControl().getIncomeOfMission(mission));
    }

    @Test
    public void test__a_flight_with_a_contract_is_paid_by_the_contract_when_it_is_finished() {
        final Journeys.Journey journey = journeyWithPrice();
        final FlightMissions.Mission mission = mission(2 * 3600);
        final PilotContracts.Contract contract = world.busyBirdsMissionControl().createContract(mission, person.getId(), allGood(3));
        bookJourney(mission, journey);

        finish(mission);

        // level III is 3% of the income, the flight pay is 400, nothing is taken off
        final int share = (int) Math.round(journey.getPrice1() * 0.03);
        assertEquals(List.of("p/s #" + person.getId() + ", f/m #" + mission.getId() + " - pilot contract #" + contract.getId()
                + ", pilot gets paid " + (400 + share) + " (flight pay 400, income share " + share + "), params " + contract.getPayParams()), paymentLog());
    }

    @Test
    public void test__the_income_share_does_not_depend_on_the_price_of_the_journey_after_the_boarding() {
        // the journey has paid and its price is zero, the income of the flight is kept in its transport flight
        final Journeys.Journey journey = journeyWithPrice();
        final int income = journey.getPrice1();
        final FlightMissions.Mission mission = mission(2 * 3600);
        world.busyBirdsMissionControl().createContract(mission, person.getId(), allGood(5));
        bookJourney(mission, journey);
        journey.setPrice1(0);

        finish(mission);

        final int share = (int) Math.round(income * 0.07);
        assertTrue(share > 0);
        assertEquals(1, paymentLog().size());
        assertTrue(paymentLog().get(0).contains("income share " + share + ")"), paymentLog().get(0));
    }

    @Test
    public void test__the_currency_of_the_pilot_at_the_booking_reduces_the_pay() {
        // no currency at all: three quarters off, a quarter of the flight pay is left
        final FlightMissions.Mission mission = mission(2 * 3600);
        world.busyBirdsMissionControl().createContract(mission, person.getId(), PilotPayParams.none());

        finish(mission);

        assertEquals(1, paymentLog().size());
        assertTrue(paymentLog().get(0).contains("pilot gets paid 100 (flight pay 100, income share 0)"), paymentLog().get(0));
    }

    @Test
    public void test__a_pilot_without_the_logbook_has_no_currency_and_level_zero() {
        final PilotPayParams params = PilotController.payParams(world, person.getId(), "C25C");

        assertFalse(params.hasTypeCurrency());
        assertFalse(params.hasNightCurrency());
        assertFalse(params.canCarryPassengers());
        assertFalse(params.isManual());
        assertEquals(0, params.getExperienceLevel());
    }

    @Test
    public void test__a_flight_without_a_contract_pays_nothing() {
        final FlightMissions.Mission mission = mission(2 * 3600);

        finish(mission);

        assertEquals(FlightMissions.Status.Finished, mission.getStatus());
        assertEquals(List.of(), paymentLog());
        assertEquals(Optional.empty(), world.busyBirdsMissionControl().payPilot(mission));
    }

    @Test
    public void test__a_flight_of_another_operator_pays_nothing_even_with_a_contract() {
        aircraft.setAircraftOperatorId(1);
        final FlightMissions.Mission mission = mission(2 * 3600);
        world.busyBirdsMissionControl().createContract(mission, person.getId(), allGood(1));

        finish(mission);

        assertEquals(List.of(), paymentLog());
    }

    @Test
    public void test__a_cancelled_flight_pays_nothing() {
        final FlightMissions.Mission mission = mission(2 * 3600);
        world.busyBirdsMissionControl().createContract(mission, person.getId(), allGood(1));
        assertTrue(world.flightMissionControl().start(mission).started());

        world.flightMissionControl().cancelFlightAndReturnAircraftToDepartureAirport(mission);

        assertEquals(List.of(), paymentLog());
    }

    @Test
    public void test__the_pay_does_not_depend_on_the_actual_time_of_the_flight() {
        final FlightMissions.Mission mission = mission(2 * 3600);
        world.busyBirdsMissionControl().createContract(mission, person.getId(), allGood(0));
        mission.setActualDepartureWorldTime(world.getWorldTime());
        mission.setActualArrivalWorldTime(world.getWorldTime() + 5 * 3600);

        finish(mission);

        assertTrue(paymentLog().get(0).contains("pilot gets paid 400"), paymentLog().get(0));
    }

    @Test
    public void test__gulfstream_flight_is_paid_by_its_own_rate() {
        final Aircrafts.Aircraft g700 = world.aircrafts().create(world.aircraftTypes().create("GA7C", "GL7"), "BB-GLA", airportA);
        g700.setAircraftOperatorId(busyBirds.getId());
        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(g700, airportA, airportB, departure, departure + 3600 + 1800);
        mission.setUserId(USER_ID);
        world.busyBirdsMissionControl().createContract(mission, person.getId(), allGood(0));

        finish(mission);

        assertTrue(paymentLog().get(0).contains("pilot gets paid 750 (flight pay 750, income share 0)"), paymentLog().get(0));
    }

    @Test
    public void test__the_contract_is_removed_with_the_flight_mission_by_the_cleanup() throws Exception {
        final FlightMissions.Mission finished = mission(3600);
        finished.setStatus(FlightMissions.Status.Finished);
        // eleven days later: the finished flight is old enough for the cleanup, the new one is not
        world.setWorldTime(world.getWorldTime() + 11 * Time.ONE_DAY);
        final FlightMissions.Mission fresh = mission(3600);
        world.busyBirdsMissionControl().createContract(finished, person.getId(), allGood(1));
        world.busyBirdsMissionControl().createContract(fresh, person.getId(), allGood(1));

        // the cleanup runs not more often than once a minute, the test lets it run
        final Field lastExecution = FlightCleanup.class.getDeclaredField("lastExecution");
        lastExecution.setAccessible(true);
        lastExecution.setLong(null, 0);
        FlightCleanup.process(world);

        assertEquals(Optional.empty(), world.flightMissions().byId(finished.getId()));
        assertEquals(Optional.empty(), world.pilotContracts().byFlightMissionId(finished.getId()));
        assertTrue(world.pilotContracts().byFlightMissionId(fresh.getId()).isPresent());
        assertEquals(1, world.pilotContracts().all().count());
    }

    @Test
    public void test__a_manual_start_sets_the_manual_bit_of_the_contract_and_the_pay_falls() {
        final Journeys.Journey journey = journeyWithPrice();
        final FlightMissions.Mission mission = mission(2 * 3600);
        final PilotContracts.Contract contract = world.busyBirdsMissionControl().createContract(mission, person.getId(), allGood(3));
        bookJourney(mission, journey);
        assertFalse(contract.getPayParams().isManual());

        world.flightMissionControl().setTrackingMode(mission, FlightMissions.TrackingMode.Manual);

        assertTrue(contract.getPayParams().isManual());
        // the other parameters stay
        assertEquals(3, contract.getPayParams().getExperienceLevel());
        assertTrue(contract.getPayParams().hasTypeCurrency());
        finish(mission);
        // manual: a third of the flight pay, a third of the share (level III, 3%)
        final int flightPay = (int) Math.round(400 * 0.33);
        final int share = (int) Math.round(journey.getPrice1() * 3 * 33 / 10_000.0);
        assertTrue(paymentLog().get(0).contains("pilot gets paid " + (flightPay + share) + " (flight pay " + flightPay + ", income share " + share + ")"), paymentLog().get(0));
    }

    @Test
    public void test__a_tracked_start_does_not_set_the_manual_bit() {
        final FlightMissions.Mission mission = mission(2 * 3600);
        final PilotContracts.Contract contract = world.busyBirdsMissionControl().createContract(mission, person.getId(), allGood(1));

        world.flightMissionControl().setTrackingMode(mission, FlightMissions.TrackingMode.SimTracker);

        assertFalse(contract.getPayParams().isManual());
    }

    @Test
    public void test__a_manual_start_of_a_flight_without_a_contract_is_fine() {
        final FlightMissions.Mission mission = mission(2 * 3600);

        world.flightMissionControl().setTrackingMode(mission, FlightMissions.TrackingMode.Manual);

        assertEquals(Optional.empty(), world.pilotContracts().byFlightMissionId(mission.getId()));
    }

    @Test
    public void test__the_operator_pays_the_pilot_by_two_transfers() {
        createAccounts(true);
        final Journeys.Journey journey = journeyWithPrice();
        final FlightMissions.Mission mission = mission(2 * 3600);
        world.busyBirdsMissionControl().createContract(mission, person.getId(), allGood(3));
        bookJourney(mission, journey);

        finish(mission);

        final int share = (int) Math.round(journey.getPrice1() * 0.03);
        assertEquals(400 + share, pilotAccount.getBalance());
        assertEquals(-(400 + share), operatorAccount.getBalance());
        final List<net.simforge.airways2.world.datamodel.BankTransactions.BankTransaction> transactions = world.bankTransactions().all().toList();
        assertEquals(2, transactions.size());
        assertEquals(net.simforge.airways2.world.datamodel.BankTransactions.Type.PilotFlightPay, transactions.get(0).getType());
        assertEquals(400, transactions.get(0).getAmount());
        assertEquals(net.simforge.airways2.world.datamodel.BankTransactions.Type.PilotIncomeShare, transactions.get(1).getType());
        assertEquals(share, transactions.get(1).getAmount());
        assertEquals(operatorAccount.getId(), transactions.get(0).getFromAccountId());
        assertEquals(pilotAccount.getId(), transactions.get(0).getToAccountId());
        // the sum of all the balances is zero
        assertEquals(0, world.bankAccounts().all().mapToLong(net.simforge.airways2.world.datamodel.BankAccounts.BankAccount::getBalance).sum());
    }

    @Test
    public void test__a_flight_without_an_income_share_has_one_transfer() {
        createAccounts(true);
        final FlightMissions.Mission mission = mission(2 * 3600);
        world.busyBirdsMissionControl().createContract(mission, person.getId(), allGood(3));

        finish(mission);

        assertEquals(1, world.bankTransactions().all().count());
        assertEquals(400, pilotAccount.getBalance());
    }

    @Test
    public void test__the_pay_of_a_manual_flight_is_what_moves() {
        createAccounts(true);
        final FlightMissions.Mission mission = mission(2 * 3600);
        world.busyBirdsMissionControl().createContract(mission, person.getId(), allGood(3));
        world.flightMissionControl().setTrackingMode(mission, FlightMissions.TrackingMode.Manual);

        finish(mission);

        // a third of 400
        assertEquals(132, pilotAccount.getBalance());
    }

    @Test
    public void test__a_person_without_a_reference_to_an_account_gets_nothing_and_the_game_goes_on() {
        createAccounts(false);
        final FlightMissions.Mission mission = mission(2 * 3600);
        world.busyBirdsMissionControl().createContract(mission, person.getId(), allGood(3));

        finish(mission);

        assertEquals(FlightMissions.Status.Finished, mission.getStatus());
        assertEquals(0, world.bankTransactions().all().count());
        assertEquals(1, paymentLog().size());
    }

    @Test
    public void test__without_accounts_the_pilot_is_paid_in_the_log_only() {
        final FlightMissions.Mission mission = mission(2 * 3600);
        world.busyBirdsMissionControl().createContract(mission, person.getId(), allGood(3));

        finish(mission);

        assertEquals(1, paymentLog().size());
        assertEquals(0, world.bankTransactions().all().count());
    }
}
