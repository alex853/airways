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
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;
import net.simforge.commons.misc.Geo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A journey which has just arrived pays its price to the operator of the transport flight it came with. With a stopover the price is divided between the
 * transport flights in proportion to their distances, the part for the next one stays in the journey.
 */
class JourneyPayoutTest {
    private World world;
    private JourneyControl control;
    private AircraftOperators.AircraftOperator operator;
    private Aircrafts.Aircraft aircraft;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private Airports.Airport airportC;
    private int fromCityId;
    private int toCityId;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        control = world.journeyControl();

        operator = world.aircraftOperators().create("BB", "BBD", "busyBirds");
        final int countryId = world.countries().create("GB", "United Kingdom").getId();
        fromCityId = world.cities().create(countryId, "From", 51.28, -0.76, 100000).getId();
        toCityId = world.cities().create(countryId, "To", 52.5, 13.4, 100000).getId();
        airportA = world.airports().create(51.28, -0.76, "FRM", "EGLF", "Farnborough");
        airportB = world.airports().create(52.36, 13.5, "TOO", "EDDB", "Berlin");
        airportC = world.airports().create(41.8, 12.25, "FCO", "LIRF", "Rome");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", airportA);
        aircraft.setAircraftOperatorId(operator.getId());

        logger = (Logger) LoggerFactory.getLogger(JourneyControl.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void afterEach() {
        logger.detachAppender(appender);
    }

    private FlightMissions.Mission mission(final Airports.Airport from, final Airports.Airport to, final int userId) {
        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(aircraft, from, to, departure, departure + 2 * Time.ONE_HOUR);
        mission.setUserId(userId);
        mission.setActualLandingAirportId(to.getId());
        return mission;
    }

    private TransportFlights.Flight transportFlight(final FlightMissions.Mission mission) {
        return world.transportFlightControl().createTransportFlight(mission);
    }

    private Journeys.Journey journey(final int price, final TransportFlights.Flight first, final TransportFlights.Flight second) {
        final Journeys.Journey journey = world.journeys().create(Journeys.Status.JustArrived, fromCityId, toCityId, 4, CabinLayout.Service.J);
        journey.setTransportFlight1Id(first.getId());
        if (second != null) {
            journey.setTransportFlight2Id(second.getId());
        }
        journey.setPrice1(price);
        return journey;
    }

    private List<String> payoutLog() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).filter(m -> m.contains(" pays ")).toList();
    }

    @Test
    void theJourneyPaysItsPriceToTheOperatorOfAFlightOfAUser() {
        final TransportFlights.Flight tf = transportFlight(mission(airportA, airportB, 7));
        final Journeys.Journey journey = journey(10_000, tf, null);

        final Optional<JourneyControl.Payout> payout = control.payForTransportation(journey);

        assertEquals(Optional.of(new JourneyControl.Payout(operator.getId(), 10_000)), payout);
        // what is paid is taken off the price
        assertEquals(0, journey.getPrice1());
        assertEquals(List.of("j/y #" + journey.getId() + " - pays 10000 to operator #" + operator.getId() + " busyBirds for t/f #" + tf.getId()
                + ", f/m #" + tf.getFlightMissionId()), payoutLog());
    }

    @Test
    void aFlightWhichIsNotAFlightOfAUserIsNotLoggedButThePriceIsSpent() {
        final TransportFlights.Flight tf = transportFlight(mission(airportA, airportB, 0));
        final Journeys.Journey journey = journey(10_000, tf, null);

        assertEquals(Optional.empty(), control.payForTransportation(journey));

        assertEquals(0, journey.getPrice1());
        assertTrue(payoutLog().isEmpty());
    }

    @Test
    void aJourneyWithoutAPriceHasNothingToPay() {
        final TransportFlights.Flight tf = transportFlight(mission(airportA, airportB, 7));
        final Journeys.Journey journey = journey(0, tf, null);

        assertEquals(Optional.empty(), control.payForTransportation(journey));

        assertEquals(0, journey.getPrice1());
        assertTrue(payoutLog().isEmpty());
    }

    // every flight of a stopover route has its own price: the journey pays price1 when it arrives with the first flight, and price2 (which has become price1) with the second
    @Test
    void withAStopoverEveryFlightIsPaidByItsOwnPrice() {
        final TransportFlights.Flight tf1 = transportFlight(mission(airportA, airportB, 7));
        final TransportFlights.Flight tf2 = transportFlight(mission(airportB, airportC, 7));
        final Journeys.Journey journey = journey(6_000, tf1, tf2);
        journey.setPrice2(4_000);

        // the journey arrives with the first transport flight: its own price is paid, the price of the second one waits
        final int first = control.payForTransportation(journey).orElseThrow().amount();
        assertEquals(6_000, first);
        assertEquals(0, journey.getPrice1());
        assertEquals(4_000, journey.getPrice2());

        // the second transport flight becomes the first one with its price
        journey.setTransportFlight1Id(tf2.getId());
        journey.setTransportFlight2Id(0);
        journey.setPrice1(journey.getPrice2());
        journey.setPrice2(0);
        final int second = control.payForTransportation(journey).orElseThrow().amount();

        assertEquals(4_000, second);
        assertEquals(0, journey.getPrice1());
        assertEquals(2, payoutLog().size());
    }

    // the bank: the system accounts (the 2nd is the passengers, as World25 says) and the account of the operator
    private net.simforge.airways2.world.datamodel.BankAccounts.BankAccount createAccountsAndGetOperatorAccount() {
        final net.simforge.airways2.world.datamodel.BankAccounts accounts = world.bankAccounts();
        final int now = world.getWorldTime();
        accounts.create(net.simforge.airways2.world.datamodel.BankAccounts.Kind.System, net.simforge.airways2.world.datamodel.BankAccounts.SYSTEM_BANK, true, now);
        final net.simforge.airways2.world.datamodel.BankAccounts.BankAccount passengers = accounts.create(
                net.simforge.airways2.world.datamodel.BankAccounts.Kind.System, net.simforge.airways2.world.datamodel.BankAccounts.SYSTEM_PASSENGERS, true, now);
        assertEquals(net.simforge.airways2.world.datamodel.BankAccounts.SystemPassengersAccountId, passengers.getId());
        return accounts.create(net.simforge.airways2.world.datamodel.BankAccounts.Kind.Operator, operator.getId(), true, now);
    }

    @Test
    void theJourneyPaysTheOperatorByATransferFromThePassengers() {
        final net.simforge.airways2.world.datamodel.BankAccounts.BankAccount operatorAccount = createAccountsAndGetOperatorAccount();
        final TransportFlights.Flight tf = transportFlight(mission(airportA, airportB, 7));
        final Journeys.Journey journey = journey(10_000, tf, null);

        control.payForTransportation(journey);

        assertEquals(10_000, operatorAccount.getBalance());
        assertEquals(-10_000, world.bankAccounts().byId(net.simforge.airways2.world.datamodel.BankAccounts.SystemPassengersAccountId).orElseThrow().getBalance());
        final List<net.simforge.airways2.world.datamodel.BankTransactions.BankTransaction> transactions = world.bankTransactions().all().toList();
        assertEquals(1, transactions.size());
        assertEquals(net.simforge.airways2.world.datamodel.BankTransactions.Type.JourneyPayment, transactions.get(0).getType());
        assertEquals(net.simforge.airways2.world.datamodel.BankAccounts.SystemPassengersAccountId, transactions.get(0).getFromAccountId());
        assertEquals(operatorAccount.getId(), transactions.get(0).getToAccountId());
        assertEquals(10_000, transactions.get(0).getAmount());
        // the sum of all the balances is zero
        assertEquals(0, world.bankAccounts().all().mapToLong(net.simforge.airways2.world.datamodel.BankAccounts.BankAccount::getBalance).sum());
    }

    @Test
    void aStopoverPaysEachOperatorItsPartByTransfers() {
        final net.simforge.airways2.world.datamodel.BankAccounts.BankAccount operatorAccount = createAccountsAndGetOperatorAccount();
        final TransportFlights.Flight first = transportFlight(mission(airportA, airportB, 7));
        final TransportFlights.Flight second = transportFlight(mission(airportB, airportC, 7));
        final Journeys.Journey journey = journey(10_000, first, second);

        final long paid = control.payForTransportation(journey).orElseThrow().amount();

        // only the part for the first flight is paid, the rest stays in the price
        assertEquals(paid, operatorAccount.getBalance());
        assertEquals(10_000 - paid, journey.getPrice1());
    }

    @Test
    void aFlightWhichIsNotAFlightOfAUserMovesNoMoney() {
        createAccountsAndGetOperatorAccount();
        final TransportFlights.Flight tf = transportFlight(mission(airportA, airportB, 0));
        final Journeys.Journey journey = journey(10_000, tf, null);

        control.payForTransportation(journey);

        assertEquals(0, world.bankTransactions().all().count());
    }

    @Test
    void withoutAccountsTheJourneyStillPaysInTheLogAndNothingMoves() {
        final TransportFlights.Flight tf = transportFlight(mission(airportA, airportB, 7));
        final Journeys.Journey journey = journey(10_000, tf, null);

        control.payForTransportation(journey);

        assertEquals(1, payoutLog().size());
        assertEquals(0, world.bankTransactions().all().count());
        assertEquals(0, journey.getPrice1());
    }

    @Test
    void anOperatorWithoutAnAccountGetsNothingAndTheGameGoesOn() {
        // the system accounts exist, the account of the operator does not
        createAccountsAndGetOperatorAccount();
        final net.simforge.airways2.world.datamodel.AircraftOperators.AircraftOperator other = world.aircraftOperators().create("XX", "XXX", "other");
        aircraft.setAircraftOperatorId(other.getId());
        final TransportFlights.Flight tf = transportFlight(mission(airportA, airportB, 7));
        final Journeys.Journey journey = journey(10_000, tf, null);

        control.payForTransportation(journey);

        assertEquals(0, world.bankTransactions().all().count());
        assertEquals(0, world.bankAccounts().all().mapToLong(net.simforge.airways2.world.datamodel.BankAccounts.BankAccount::getBalance).sum());
    }
}
