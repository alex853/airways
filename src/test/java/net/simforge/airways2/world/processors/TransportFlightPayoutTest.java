package net.simforge.airways2.world.processors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftOperators;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.BankTransactions;
import net.simforge.airways2.world.datamodel.FlightMissions;
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
 * A finished transport flight of a pilot is paid to its operator in one transfer of its income (the sum of the prices of the journeys which boarded), not by every journey.
 */
class TransportFlightPayoutTest {
    private World world;
    private TransportFlightControl control;
    private AircraftOperators.AircraftOperator operator;
    private Aircrafts.Aircraft aircraft;
    private Airports.Airport airportA;
    private Airports.Airport airportB;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        control = world.transportFlightControl();

        operator = world.aircraftOperators().create("BB", "BBD", "busyBirds");
        airportA = world.airports().create(51.28, -0.76, "FRM", "EGLF", "Farnborough");
        airportB = world.airports().create(52.36, 13.5, "TOO", "EDDB", "Berlin");
        aircraft = world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", airportA);
        aircraft.setAircraftOperatorId(operator.getId());

        logger = (Logger) LoggerFactory.getLogger(TransportFlightControl.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void afterEach() {
        logger.detachAppender(appender);
    }

    private TransportFlights.Flight transportFlight(final FlightMissions.CharacterMode mode, final int income) {
        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, departure, departure + 2 * Time.ONE_HOUR);
        mission.setCharacterMode(mode);
        mission.setUserId(mode == FlightMissions.CharacterMode.PC ? 1 : 0);
        final TransportFlights.Flight flight = control.createTransportFlight(mission);
        control.addIncome(flight, income);
        return flight;
    }

    private List<String> payoutLog() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).filter(m -> m.contains(" pay ")).toList();
    }

    // the bank: the system accounts (the 2nd is the passengers, as World25 says) and the account of the operator
    private BankAccounts.BankAccount createAccountsAndGetOperatorAccount() {
        final BankAccounts accounts = world.bankAccounts();
        final int now = world.getWorldTime();
        accounts.create(BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK, true, now);
        final BankAccounts.BankAccount passengers = accounts.create(BankAccounts.Kind.System, BankAccounts.SYSTEM_PASSENGERS, true, now);
        assertEquals(BankAccounts.SystemPassengersAccountId, passengers.getId());
        return accounts.create(BankAccounts.Kind.Operator, operator.getId(), true, now);
    }

    @Test
    void theFlightIsPaidToItsOperatorByOneTransferOfTheIncome() {
        final BankAccounts.BankAccount operatorAccount = createAccountsAndGetOperatorAccount();
        final TransportFlights.Flight tf = transportFlight(FlightMissions.CharacterMode.PC, 10_000);

        final Optional<TransportFlightControl.Payout> payout = control.payOperator(tf);

        assertEquals(Optional.of(new TransportFlightControl.Payout(operator.getId(), 10_000)), payout);
        assertEquals(List.of("t/f #" + tf.getId() + " - passengers pay 10000 to operator #" + operator.getId() + " busyBirds, f/m #" + tf.getFlightMissionId()), payoutLog());
        assertEquals(10_000, operatorAccount.getBalance());
        assertEquals(-10_000, world.bankAccounts().byId(BankAccounts.SystemPassengersAccountId).orElseThrow().getBalance());
        final List<BankTransactions.BankTransaction> transactions = world.bankTransactions().all().toList();
        assertEquals(1, transactions.size());
        assertEquals(BankTransactions.Type.JourneyPayment, transactions.get(0).getType());
        assertEquals(BankAccounts.SystemPassengersAccountId, transactions.get(0).getFromAccountId());
        assertEquals(operatorAccount.getId(), transactions.get(0).getToAccountId());
        assertEquals(10_000, transactions.get(0).getAmount());
        // the sum of all the balances is zero
        assertEquals(0, world.bankAccounts().all().mapToLong(BankAccounts.BankAccount::getBalance).sum());
        // the income is kept, the pay of the pilot is taken from it
        assertEquals(10_000, control.getIncome(tf));
    }

    @Test
    void aFlightWhichIsNotAFlightOfAPilotHasNoIncomeAndMovesNoMoney() {
        createAccountsAndGetOperatorAccount();
        final TransportFlights.Flight tf = transportFlight(FlightMissions.CharacterMode.NPC, 10_000);

        assertEquals(Optional.empty(), control.payOperator(tf));

        assertTrue(payoutLog().isEmpty());
        assertEquals(0, world.bankTransactions().all().count());
    }

    @Test
    void aFlightTrackedViaVatsimWithoutAUserHasNoIncomeAndMovesNoMoney() {
        createAccountsAndGetOperatorAccount();
        final int departure = world.getWorldTime() + Time.ONE_HOUR;
        final FlightMissions.Mission mission = world.flightMissions().createDispatchedMission(aircraft, airportA, airportB, departure, departure + 2 * Time.ONE_HOUR);
        mission.setCharacterMode(FlightMissions.CharacterMode.PC);
        mission.setUserId(0);
        final TransportFlights.Flight tf = control.createTransportFlight(mission);

        control.addIncome(tf, 10_000);

        assertEquals(0, control.getIncome(tf));
        assertEquals(Optional.empty(), control.payOperator(tf));
        assertTrue(payoutLog().isEmpty());
        assertEquals(0, world.bankTransactions().all().count());
    }

    @Test
    void aFlightWithoutAUserWhichHasAnIncomeBookedBeforeTheRuleStillPaysNothing() {
        createAccountsAndGetOperatorAccount();
        final TransportFlights.Flight tf = transportFlight(FlightMissions.CharacterMode.PC, 10_000);
        world.flightMissions().byId(tf.getFlightMissionId()).orElseThrow().setUserId(0);

        assertEquals(Optional.empty(), control.payOperator(tf));

        assertTrue(payoutLog().isEmpty());
        assertEquals(0, world.bankTransactions().all().count());
    }

    @Test
    void aFlightWithoutIncomeHasNothingToPay() {
        createAccountsAndGetOperatorAccount();
        final TransportFlights.Flight tf = transportFlight(FlightMissions.CharacterMode.PC, 0);

        assertEquals(Optional.empty(), control.payOperator(tf));

        assertTrue(payoutLog().isEmpty());
        assertEquals(0, world.bankTransactions().all().count());
    }

    @Test
    void withoutAccountsTheFlightStillPaysInTheLogAndNothingMoves() {
        final TransportFlights.Flight tf = transportFlight(FlightMissions.CharacterMode.PC, 10_000);

        control.payOperator(tf);

        assertEquals(1, payoutLog().size());
        assertEquals(0, world.bankTransactions().all().count());
    }

    @Test
    void anOperatorWithoutAnAccountGetsNothingAndTheGameGoesOn() {
        // the system accounts exist, the account of the operator does not
        createAccountsAndGetOperatorAccount();
        final AircraftOperators.AircraftOperator other = world.aircraftOperators().create("XX", "XXX", "other");
        aircraft.setAircraftOperatorId(other.getId());
        final TransportFlights.Flight tf = transportFlight(FlightMissions.CharacterMode.PC, 10_000);

        control.payOperator(tf);

        assertEquals(0, world.bankTransactions().all().count());
        assertEquals(0, world.bankAccounts().all().mapToLong(BankAccounts.BankAccount::getBalance).sum());
    }
}
