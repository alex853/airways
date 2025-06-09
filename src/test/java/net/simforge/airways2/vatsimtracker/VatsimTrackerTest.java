package net.simforge.airways2.vatsimtracker;

import net.simforge.airways2.app.WorldAccess;
import net.simforge.airways2.app.vatsimtracker.PilotContext;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Geo;
import net.simforge.networkview.core.Position;
import net.simforge.networkview.core.report.ReportUtils;
import net.simforge.networkview.core.report.persistence.Report;
import net.simforge.networkview.core.report.persistence.ReportPilotPosition;
import net.simforge.refdata.aircrafts.apd.AircraftPerformanceDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.jupiter.api.Assertions.*;

public class VatsimTrackerTest {

    private World world;
    private WorldAccess worldAccess;

    private Aircrafts.Aircraft aircraft;

    private Set<String> worldIcaos;
    private Airports.Airport egll;
    private Airports.Airport egcc;
    private Airports.Airport egkk;
    private Airports.Airport egss;

    private final int pilotNumber = 799999;
    private ReportPilotPosition currentVatsimPosition;
    private ReportPilotPosition positionWhileFlyingOffline;
    private PilotContext pilotContext;
    private String nextReport;

    private SimulationMode simulationMode = SimulationMode.Nope;
    private String simulationActualDestinationAirportIcao;
    private static final int TAXI_SPEED_KTS = 10;

    @BeforeEach
    public void beforeEach() {
        final int startTime = Time.START_TIME_EPOCH_SECONDS;
        world = World.create(new InMemoryStorageStrategy(), startTime);
        worldAccess = new WorldAccess() {
            @Override
            public <T> T read(Action<T> action) {
                return action.invoke(world);
            }

            @Override
            public <T> T modifySync(Action<T> action) {
                return action.invoke(world);
            }
        };

        egll = world.airports().create(51.4775, -0.461389, null, "EGLL", "Heathrow");
        egcc = world.airports().create(53.3537, -2.27495, null, "EGCC", "Manchester");
        egkk = world.airports().create(51.1481, -0.190278, null, "EGKK", "Gatwick");
        egss = world.airports().create(51.885, 0.235, null, "EGSS", "Stansted");

        worldIcaos = world.airports().all().stream().map(Airports.Airport::getIcao).collect(Collectors.toSet());

        final AircraftTypes.AircraftType aircraftType = world.aircraftTypes().create("A320", "320");
        final AircraftOperators.AircraftOperator shadowJet = world.aircraftOperators().create(World25.ShadowJetIata, World25.ShadowJetIcao, "ShadowJet");

        aircraft = world.aircrafts().create(aircraftType, "G-ABCD", egll);
        aircraft.setAircraftOperatorId(shadowJet.getId());

        nextReport = ReportUtils.toTimestamp(Time.toLdt(startTime));

        vatsimPilotGoesOffline();

        pilotContext = null;
    }

    @Test
    public void ideal_flight_from_egll_to_egcc___flight_should_be_finished() {
        assertAircraftParkedAtAirportAndIdle(egll);

        runWorldMins(10);
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC");
        runWorldMins(2);

        assertFlight1Preflight(egll, egcc);
        assertAircraftParkedAtAirportAndActive(egll);

        vatsimPilotStartsTaxiingOut();
        runWorldMins(3);

        assertFlight1Departing();
        assertAircraftTaxiingOut(egll);

        runWorldMins(3);
        vatsimPilotMakesTakeoff();
        runWorldMins(3);

        assertFlight1Flying();
        assertAircraftFlying();

        runWorldMins(10);

        assertFlight1Flying();
        assertAircraftFlying();

        runWorldUntilVatsimPilotReachesDestination();
        vatsimPilotMakesLanding();
        runWorldMins(3);

        assertFlight1Arriving();
        assertAircraftTaxiingIn(egcc);

        runWorldMins(3);
        vatsimPilotPutsBlocksOn();
        runWorldMins(10);

        assertFlight1Finished(egll, egcc);
        assertAircraftParkedAtAirportAndIdle(egcc);

        vatsimPilotGoesOffline();
        runWorldMins(10);

        assertAircraftParkedAtAirportAndIdle(egcc);
        assertFlight1Finished(egll, egcc);
    }

    @Test
    public void valid_fp___then_offline_on_ground___flight_should_be_cancelled() {
        assertAircraftParkedAtAirportAndIdle(egll);

        runWorldMins(10);
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC");
        runWorldMins(2);

        assertFlight1Preflight(egll, egcc);
        assertAircraftParkedAtAirportAndActive(egll);

        runWorldMins(10);
        vatsimPilotGoesOffline();
        runWorldMins(5);

        assertFlight1Cancelled();
        assertAircraftParkedAtAirportAndIdle(egll);
    }

    @Test
    public void invalid_fp___then_offline_while_parked___flight_should_not_be_created() {
        assertAircraftParkedAtAirportAndIdle(egll);

        runWorldMins(10);
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "UWWW");
        runWorldMins(2);

        assertNoFlight1Created();
        assertAircraftParkedAtAirportAndIdle(egll);
        assertPilotContextPresent();

        vatsimPilotGoesOffline();
        runWorldMins(2);

        assertPilotContextAbsent();
        assertAircraftParkedAtAirportAndIdle(egll);
    }

    @Test
    public void invalid_fp___then_takeoff___flight_should_not_be_created() {
        assertAircraftParkedAtAirportAndIdle(egll);

        runWorldMins(10);
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "UWWW");
        runWorldMins(2);

        assertNoFlight1Created();
        assertAircraftParkedAtAirportAndIdle(egll);
        assertPilotContextPresent();

        vatsimPilotStartsTaxiingOut();
        runWorldMins(3);

        assertAircraftParkedAtAirportAndIdle(egll);
        assertPilotContextPresent();

        runWorldMins(3);
        vatsimPilotMakesTakeoff();
        runWorldMins(2);

        assertPilotContextAbsent();
        assertAircraftParkedAtAirportAndIdle(egll);
    }

    @Test
    public void invalid_fp___then_valid_fp___flight_should_start() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "UWWW"); // invalid flightplan
        runWorldMins(10);

        assertNoFlight1Created();
        assertAircraftParkedAtAirportAndIdle(egll);
        assertPilotContextPresent();

        vatsimPilotFilesFlightplan("EGLL", "EGCC"); // valid flightplan
        runWorldMins(3);

        assertFlight1Preflight(egll, egcc);
        assertAircraftParkedAtAirportAndActive(egll);
        assertPilotContextPresent();
    }

    @Test
    public void invalid_fp___then_invalid_fp_again___flight_should_not_be_created() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "UWWW"); // invalid flightplan
        runWorldMins(10);

        assertNoFlight1Created();
        assertAircraftParkedAtAirportAndIdle(egll);
        assertPilotContextPresent();

        vatsimPilotFilesFlightplan("EGLL", "LCLK"); // another invalid flightplan
        runWorldMins(3);

        assertNoFlight1Created();
        assertAircraftParkedAtAirportAndIdle(egll);
        assertPilotContextPresent();
    }

    @Test
    public void valid_fp___then_invalid_fp___flight_should_be_cancelled___and___another_should_not_be_created() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC"); // valid flightplan
        runWorldMins(10);

        assertFlight1Preflight(egll, egcc);
        assertAircraftParkedAtAirportAndActive(egll);
        assertPilotContextPresent();

        vatsimPilotFilesFlightplan("EGLL", "LCLK"); // invalid flightplan
        runWorldMins(3);

        assertFlight1Cancelled();
        assertNoFlight2Created();
        assertAircraftParkedAtAirportAndIdle(egll);
        assertPilotContextPresent();
    }

    @Test
    public void valid_fp___then_another_valid_fp___flight_should_be_cancelled___and___another_should_be_created() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC"); // valid flightplan
        runWorldMins(10);

        assertFlight1Preflight(egll, egcc);
        assertAircraftParkedAtAirportAndActive(egll);
        assertPilotContextPresent();

        vatsimPilotFilesFlightplan("EGLL", "EGSS"); // another valid flightplan
        runWorldMins(3);

        assertFlight1Cancelled();
        assertFlight2Preflight(egll, egss);
        assertAircraftParkedAtAirportAndActive(egll);
        assertPilotContextPresent();
    }

    // todo ak1 change of aircraft type

    @Test
    public void valid_fp___then_goes_offline___then_same_valid_fp___flight_should_be_cancelled___and___another_should_be_created() { // todo ak1 this may be treated as same flight, do not recreate
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC"); // valid flightplan
        runWorldMins(10);

        assertFlight1Preflight(egll, egcc);
        assertAircraftParkedAtAirportAndActive(egll);
        assertPilotContextPresent();

        vatsimPilotGoesOffline();
        runWorldMins(5);
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC"); // same valid flightplan
        runWorldMins(3);

        assertFlight1Cancelled();
        assertFlight2Preflight(egll, egcc);
        assertAircraftParkedAtAirportAndActive(egll);
        assertPilotContextPresent();
    }

    @Test
    public void valid_fp___then_goes_offline___then_another_valid_fp___flight_should_be_cancelled___and___another_should_be_created() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC"); // valid flightplan
        runWorldMins(10);

        assertFlight1Preflight(egll, egcc);
        assertAircraftParkedAtAirportAndActive(egll);
        assertPilotContextPresent();

        vatsimPilotGoesOffline();
        runWorldMins(5);
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGSS"); // another valid flightplan
        runWorldMins(3);

        assertFlight1Cancelled();
        assertFlight2Preflight(egll, egss);
        assertAircraftParkedAtAirportAndActive(egll);
        assertPilotContextPresent();
    }

    @Test
    public void valid_fp___then_goes_offline___then_invalid_fp___flight_should_be_cancelled___and___another_should_not_be_created() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC"); // valid flightplan
        runWorldMins(10);

        assertFlight1Preflight(egll, egcc);
        assertAircraftParkedAtAirportAndActive(egll);
        assertPilotContextPresent();

        vatsimPilotGoesOffline();
        runWorldMins(5);
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "LCLK"); // invalid flightplan
        runWorldMins(3);

        assertFlight1Cancelled();
        assertNoFlight2Created();
        assertAircraftParkedAtAirportAndIdle(egll);
        assertPilotContextPresent();
    }

    @Test
    public void valid_fp___then_goes_offline___then_connect_at_another_airport___flight_should_be_cancelled() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC"); // valid flightplan
        runWorldMins(10);

        assertFlight1Preflight(egll, egcc);
        assertAircraftParkedAtAirportAndActive(egll);
        assertPilotContextPresent();

        vatsimPilotGoesOffline();
        runWorldMins(5);
        vatsimPilotGoesOnlineParkedAt(egkk, "A320");
        runWorldMins(3);

        assertFlight1Cancelled();
        assertNoFlight2Created();
        assertAircraftParkedAtAirportAndIdle(egll);
        assertPilotContextPresent();
    }

    @Test
    public void valid_fp___jump_to_another_airport___flight_should_be_cancelled() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC"); // valid flightplan
        runWorldMins(10);

        assertFlight1Preflight(egll, egcc);
        assertAircraftParkedAtAirportAndActive(egll);
        assertPilotContextPresent();

        vatsimPilotJumpsToAirportWhileParkedAtAirport(egkk);
        runWorldMins(3);

        assertFlight1Cancelled();
        assertNoFlight2Created();
        assertAircraftParkedAtAirportAndIdle(egll);
        assertPilotContextPresent();
    }

    @Test
    public void valid_flight_till_landing___then_landing_at_wrong_airport_in_the_world___flight_should_be_finished___aircraft_is_at_that_wrong_airport() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC");
        runWorldMins(2);
        vatsimPilotStartsTaxiingOut();
        runWorldMins(5);
        vatsimPilotMakesTakeoff();
        vatsimPilotInFactFliesToAnotherAirport("EGKK");
        runWorldUntilVatsimPilotReachesDestination();
        vatsimPilotMakesLanding();
        runWorldMins(3);

        assertFlight1Arriving();
        assertAircraftTaxiingIn(egkk);

        runWorldMins(3);
        vatsimPilotPutsBlocksOn();
        runWorldMins(10);

        assertFlight1Finished(egll, egcc); // todo ak1 flight finished at egkk however egcc was planned, flight finished, flight mission still shows egcc, not egkk - need to fix, need to have 'actual destination'?
        assertAircraftParkedAtAirportAndIdle(egcc);
    }

    @Test
    public void valid_flight_till_landing___then___landing_out_of_the_world___flight_should_be_cancelled() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC");
        runWorldMins(2);
        vatsimPilotStartsTaxiingOut();
        runWorldMins(5);
        vatsimPilotMakesTakeoff();
        vatsimPilotInFactFliesToAnotherAirport("EIDW");
        runWorldUntilVatsimPilotReachesDestination();
        vatsimPilotMakesLanding();
        runWorldMins(3);

        assertFlight1Cancelled();
        assertAircraftParkedAtAirportAndIdle(egll);
    }

    @Test
    public void valid_flight_till_blocks_on___context_should_be_reset_in_10_mins_after_blocks_on() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC");
        runWorldMins(2);
        vatsimPilotStartsTaxiingOut();
        runWorldMins(5);
        vatsimPilotMakesTakeoff();
        runWorldUntilVatsimPilotReachesDestination();
        vatsimPilotMakesLanding();
        runWorldMins(3);
        vatsimPilotPutsBlocksOn();
        runWorldMins(5);

        assertFlight1Finished(egll, egcc);
        assertAircraftParkedAtAirportAndIdle(egcc);

        runWorldMins(10);

        assertNoFlight2Created();
        assertPilotContextPresent();
    }

    @Test
    public void valid_flight_till_blocks_on___then_valid_fp_before_arrived_status___flight_should_be_finished___another_should_be_created() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC");
        runWorldMins(2);
        vatsimPilotStartsTaxiingOut();
        runWorldMins(5);
        vatsimPilotMakesTakeoff();
        runWorldUntilVatsimPilotReachesDestination();
        vatsimPilotMakesLanding();
        runWorldMins(3);
        vatsimPilotPutsBlocksOn();
        runWorldMins(5);

        assertFlight1Finished(egll, egcc);
        assertAircraftParkedAtAirportAndIdle(egcc);

        vatsimPilotFilesFlightplan("EGCC", "EGKK");
        runWorldMins(2);

        assertFlight1Finished(egll, egcc);
        assertFlight2Preflight(egcc, egkk);
    }

    @Test
    public void valid_flight_till_landing___then_pilot_goes_offline___then_pilot_jumps_to_another_airport_and_goes_online_with_correct_fp___flight_should_be_finished___another_should_be_created() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC");
        runWorldMins(2);
        vatsimPilotStartsTaxiingOut();
        runWorldMins(5);
        vatsimPilotMakesTakeoff();
        runWorldUntilVatsimPilotReachesDestination();
        vatsimPilotMakesLanding();
        runWorldMins(3);

        assertFlight1Arriving();

        vatsimPilotGoesOffline();
        runWorldMins(3);

        assertFlight1Finished(egll, egcc);
        assertAircraftParkedAtAirportAndIdle(egcc);

        vatsimPilotGoesOnlineParkedAt(egss, "A320");
        vatsimPilotFilesFlightplan("EGSS", "EGLL");
        runWorldMins(3);

        assertFlight2Preflight(egss, egll);
    }

    @Test
    public void valid_flight_till_blocks_on___then_pilot_goes_offline___then_pilot_jumps_to_another_airport_and_goes_online_with_correct_fp___flight_should_be_finished___another_should_be_created() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC");
        runWorldMins(2);
        vatsimPilotStartsTaxiingOut();
        runWorldMins(5);
        vatsimPilotMakesTakeoff();
        runWorldUntilVatsimPilotReachesDestination();
        vatsimPilotMakesLanding();
        runWorldMins(3);
        vatsimPilotPutsBlocksOn();
        runWorldMins(3);
        vatsimPilotGoesOffline();
        runWorldMins(3);

        assertFlight1Finished(egll, egcc);
        assertAircraftParkedAtAirportAndIdle(egcc);

        vatsimPilotGoesOnlineParkedAt(egss, "A320");
        vatsimPilotFilesFlightplan("EGSS", "EGLL");
        runWorldMins(3);

        assertFlight2Preflight(egss, egll);
    }

    @Test
    public void valid_flight_till_flying___then_pilot_jumps_to_ground_parked_at_another_airport___flight_should_be_cancelled() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC");
        runWorldMins(2);
        vatsimPilotStartsTaxiingOut();
        runWorldMins(5);
        vatsimPilotMakesTakeoff();
        runWorldMins(10);
        vatsimPilotJumpsToAirportWhileFlying(egss);
        runWorldMins(3);

        assertFlight1Cancelled();
        assertAircraftParkedAtAirportAndIdle(egll);
    }

    @Test
    public void valid_flight_till_flying___then_pilot_jumps_to_in_air___flight_should_be_cancelled() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC");
        runWorldMins(2);
        vatsimPilotStartsTaxiingOut();
        runWorldMins(5);
        vatsimPilotMakesTakeoff();
        runWorldMins(15);
        vatsimPilotJumpsInSlewModeWhileFlying(Geo.coords(egll.getCoords().getLat() - 10, egll.getCoords().getLon() - 10));
        runWorldMins(3);

        assertFlight1Cancelled();
        assertAircraftParkedAtAirportAndIdle(egll);
    }

    @Test
    public void valid_flight_till_flying___then_pilot_goes_offline_for_few_mins___then_pilot_goes_back_online___flight_should_be_continued_and_then_finished() {
        vatsimPilotGoesOnlineParkedAt(egll, "A320");
        vatsimPilotFilesFlightplan("EGLL", "EGCC");
        runWorldMins(2);
        vatsimPilotStartsTaxiingOut();
        runWorldMins(5);
        vatsimPilotMakesTakeoff();
        runWorldMins(10);

        vatsimPilotGoesOfflineWhileFlyingAndWillBeBackOnline();
        runWorldMins(3);

        vatsimPilotGoesBackOnlineWhileFlying();

        assertFlight1Flying();

        runWorldUntilVatsimPilotReachesDestination();
        vatsimPilotMakesLanding();
        runWorldMins(3);

        vatsimPilotPutsBlocksOn();
        runWorldMins(10);

        assertFlight1Finished(egll, egcc);
        assertAircraftParkedAtAirportAndIdle(egcc);
    }

    // todo ak1 short disconnect cases on ground
    // todo ak1 offline and then reconnect as before, reconnect without f/p, reconnect on ground, reconnect on different place of world

    // todo ak2 reconnect while pilot context is in irreversible status - it does not seem relevant because Irreversible is almost removed

    private void assertPilotContextPresent() {
        assertNotNull(pilotContext);
    }

    private void assertPilotContextAbsent() {
        assertNull(pilotContext);
    }

    private void assertNoFlight1Created() {
        assertFalse(world.flightMissions().byId(1).isPresent());
    }

    private void assertNoFlight2Created() {
        assertFalse(world.flightMissions().byId(2).isPresent());
    }

    private void assertFlight1Preflight(final Airports.Airport from, final Airports.Airport to) {
        assertFlightPreflight(getFlight1(), from, to);
    }

    private void assertFlight2Preflight(final Airports.Airport from, final Airports.Airport to) {
        assertFlightPreflight(getFlight2(), from, to);
    }

    private void assertFlightPreflight(final FlightMissions.Mission flight, final Airports.Airport from, final Airports.Airport to) {
        assertEquals(FlightMissions.Status.Preflight, flight.getStatus());
        assertEquals(from.getId(), flight.getDepartureAirportId());
        assertEquals(to.getId(), flight.getDestinationAirportId());
    }

    private void assertFlight1Departing() {
        final FlightMissions.Mission flight = getFlight1();
        assertEquals(FlightMissions.Status.Departure, flight.getStatus());
    }

    private void assertFlight1Flying() {
        final FlightMissions.Mission flight = getFlight1();
        assertEquals(FlightMissions.Status.Flying, flight.getStatus());
    }

    private void assertFlight1Arriving() {
        final FlightMissions.Mission flight = getFlight1();
        assertEquals(FlightMissions.Status.Arrival, flight.getStatus());
    }

    private void assertFlight1Finished(final Airports.Airport from, final Airports.Airport to) {
        final FlightMissions.Mission flight = getFlight1();
        assertEquals(FlightMissions.Status.Finished, flight.getStatus());
        assertEquals(from.getId(), flight.getDepartureAirportId());
        assertEquals(to.getId(), flight.getDestinationAirportId());
    }

    private void assertFlight1Cancelled() {
        final FlightMissions.Mission flight = getFlight1();
        assertEquals(FlightMissions.Status.Cancelled, flight.getStatus());
    }

    public void assertAircraftParkedAtAirportAndActive(final Airports.Airport airport) {
        assertEquals(Aircrafts.LocationStatus.ParkedAtAirport, aircraft.getLocationStatus());
        assertEquals(Aircrafts.OperationalStatus.Active, aircraft.getOperationalStatus());
        assertEquals(airport.getId(), aircraft.getLocationAirportId());
    }

    public void assertAircraftTaxiingOut(final Airports.Airport airport) {
        assertEquals(Aircrafts.LocationStatus.TaxiingOut, aircraft.getLocationStatus());
        assertEquals(Aircrafts.OperationalStatus.Active, aircraft.getOperationalStatus());
        assertEquals(airport.getId(), aircraft.getLocationAirportId());
    }

    public void assertAircraftFlying() {
        assertEquals(Aircrafts.LocationStatus.Flying, aircraft.getLocationStatus());
        assertEquals(Aircrafts.OperationalStatus.Active, aircraft.getOperationalStatus());
        assertEquals(0, aircraft.getLocationAirportId());
    }

    public void assertAircraftTaxiingIn(final Airports.Airport airport) {
        assertEquals(Aircrafts.LocationStatus.TaxiingIn, aircraft.getLocationStatus());
        assertEquals(Aircrafts.OperationalStatus.Active, aircraft.getOperationalStatus());
        assertEquals(airport.getId(), aircraft.getLocationAirportId());
    }

    public void assertAircraftParkedAtAirportAndIdle(final Airports.Airport airport) {
        assertEquals(Aircrafts.LocationStatus.ParkedAtAirport, aircraft.getLocationStatus());
        assertEquals(Aircrafts.OperationalStatus.Idle, aircraft.getOperationalStatus());
        assertEquals(airport.getId(), aircraft.getLocationAirportId());
    }

    private FlightMissions.Mission getFlight1() {
        return world.flightMissions().byId(1).orElseThrow();
    }

    private FlightMissions.Mission getFlight2() {
        return world.flightMissions().byId(2).orElseThrow();
    }

    private void vatsimPilotGoesOffline() {
        currentVatsimPosition = null;
        simulationMode = SimulationMode.Nope;
        positionWhileFlyingOffline = null;
        simulationActualDestinationAirportIcao = null;
    }

    private void vatsimPilotGoesOnlineParkedAt(final Airports.Airport airport, final String aircraftType) {
        checkArgument(currentVatsimPosition == null);
        checkArgument(simulationMode == SimulationMode.Nope);
        currentVatsimPosition = new ReportPilotPosition();
        currentVatsimPosition.setPilotNumber(pilotNumber);
        currentVatsimPosition.setFpAircraft(aircraftType);
        currentVatsimPosition.setOnGround(true);
        currentVatsimPosition.setHeading(0);
        currentVatsimPosition.setAltitude(10);
        currentVatsimPosition.setGroundspeed(0);
        currentVatsimPosition.setLatitude((double) airport.getLatitude());
        currentVatsimPosition.setLongitude((double) airport.getLongitude());
    }

    private void vatsimPilotJumpsToAirportWhileParkedAtAirport(final Airports.Airport airport) {
        checkArgument(simulationMode == SimulationMode.Nope);
        checkNotNull(currentVatsimPosition);
        checkArgument(currentVatsimPosition.getOnGround());
        currentVatsimPosition.setLatitude((double) airport.getLatitude());
        currentVatsimPosition.setLongitude((double) airport.getLongitude());
    }

    private void vatsimPilotJumpsToAirportWhileFlying(final Airports.Airport airport) {
        checkArgument(simulationMode == SimulationMode.Flying);
        checkNotNull(currentVatsimPosition);
        checkArgument(!currentVatsimPosition.getOnGround());
        currentVatsimPosition.setLatitude((double) airport.getLatitude());
        currentVatsimPosition.setLongitude((double) airport.getLongitude());
        currentVatsimPosition.setOnGround(true);
    }

    private void vatsimPilotJumpsInSlewModeWhileFlying(final Geo.Coords coords) {
        checkArgument(simulationMode == SimulationMode.Flying);
        checkNotNull(currentVatsimPosition);
        checkArgument(!currentVatsimPosition.getOnGround());
        currentVatsimPosition.setLatitude(coords.getLat());
        currentVatsimPosition.setLongitude(coords.getLon());
    }

    private void vatsimPilotFilesFlightplan(final String departure, final String destination) {
        checkNotNull(currentVatsimPosition);
        checkArgument(simulationMode == SimulationMode.Nope);
        currentVatsimPosition.setFpOrigin(departure);
        currentVatsimPosition.setFpDestination(destination);
    }

    private void vatsimPilotStartsTaxiingOut() {
        checkNotNull(currentVatsimPosition);
        checkArgument(Position.create(currentVatsimPosition).isInAirport());
        checkArgument(simulationMode == SimulationMode.Nope);
        simulationMode = SimulationMode.TaxiingOut;
    }

    private void vatsimPilotMakesTakeoff() {
        checkNotNull(currentVatsimPosition);
        checkArgument(Position.create(currentVatsimPosition).isInAirport());
        checkArgument(simulationMode == SimulationMode.TaxiingOut);
        simulationMode = SimulationMode.Flying;
        simulationActualDestinationAirportIcao = currentVatsimPosition.getFpDestination();
        currentVatsimPosition.setOnGround(false);
    }

    private void vatsimPilotInFactFliesToAnotherAirport(final String actualDestinationIcao) {
        checkNotNull(currentVatsimPosition);
        checkArgument(!Position.create(currentVatsimPosition).isOnGround());
        checkArgument(simulationMode == SimulationMode.Flying);
        simulationActualDestinationAirportIcao = actualDestinationIcao;
    }

    private void runWorldUntilVatsimPilotReachesDestination() {
        checkNotNull(currentVatsimPosition);
        checkArgument(!Position.create(currentVatsimPosition).isOnGround());
        checkArgument(simulationMode == SimulationMode.Flying);

        while (simulationMode != SimulationMode.TimeToLand) {
            runWorldMins(2);
        }
    }

    private void vatsimPilotGoesOfflineWhileFlyingAndWillBeBackOnline() {
        checkNotNull(currentVatsimPosition);
        checkArgument(!Position.create(currentVatsimPosition).isOnGround());
        checkArgument(simulationMode == SimulationMode.Flying);
        simulationMode = SimulationMode.FlyingWhileShortDisconnect;
        positionWhileFlyingOffline = currentVatsimPosition;
        currentVatsimPosition = null;
    }

    private void vatsimPilotGoesBackOnlineWhileFlying() {
        checkArgument(simulationMode == SimulationMode.FlyingWhileShortDisconnect);
        checkArgument(currentVatsimPosition == null);
        checkNotNull(positionWhileFlyingOffline);
        simulationMode = SimulationMode.Flying;
        currentVatsimPosition = positionWhileFlyingOffline;
        positionWhileFlyingOffline = null;
    }

    private void vatsimPilotMakesLanding() {
        checkNotNull(currentVatsimPosition);
        checkArgument(!Position.create(currentVatsimPosition).isOnGround());
        checkArgument(simulationMode == SimulationMode.TimeToLand);
        simulationMode = SimulationMode.TaxiingIn;

        final Geo.Coords destinationCoords = net.simforge.refdata.airports.Airports.get().findByIcao(simulationActualDestinationAirportIcao).orElseThrow().getCoords();
        //final Airports.Airport destination = world.airports().byIcao(pilotContext.getPlannedDestination()).orElseThrow();
        currentVatsimPosition.setOnGround(true);
        currentVatsimPosition.setLatitude(destinationCoords.getLat());
        currentVatsimPosition.setLongitude(destinationCoords.getLon());
    }

    private void vatsimPilotPutsBlocksOn() {
        checkNotNull(currentVatsimPosition);
        checkArgument(Position.create(currentVatsimPosition).isInAirport());
        checkArgument(simulationMode == SimulationMode.TaxiingIn);
        simulationMode = SimulationMode.Nope;
    }

    private void runWorldMins(final int minutes) {
        final int startTime = world.getWorldTime();
        final int finishTime = startTime + minutes * Time.ONE_MINUTE;

        while (true) {
            final int now = world.getWorldTime();

            if (now >= finishTime) {
                break;
            }

            world.process(world.getWorldTime() + 10);

            if (!Time.toLdt(now).isBefore(ReportUtils.fromTimestampJava(nextReport))) {
                processVatsimReport();
            }
        }
    }

    private void processVatsimReport() {
        if (currentVatsimPosition != null) {
            currentVatsimPosition.setReport(toReport(nextReport));
        }

        final Position position = currentVatsimPosition != null
                ? Position.create(currentVatsimPosition)
                : Position.createOfflinePosition(toReport(nextReport));

        // code below reproduces logic of processing report position for one single pilot
        if (pilotContext != null) {
            if (position.isPositionKnown()) {
                pilotContext.nextReportPosition(position);
            } else {
                pilotContext.noPositionInReport(position.getReportInfo().getReport());
            }
        } else {
            if (position.isPositionKnown()
                    && position.isInAirport()
                    && worldIcaos.contains(position.getAirportIcao())) {
                pilotContext = new PilotContext(worldAccess, pilotNumber);
                pilotContext.newPilotContextInAirport(position);
            }
        }

        if (pilotContext != null) {
            if (pilotContext.shouldBeRemoved()) {
                pilotContext = null;
            }
        }

        nextReport = ReportUtils.toTimestamp(ReportUtils.fromTimestampJava(nextReport).plusMinutes(2));

        switch (simulationMode) {
            case Nope:
                break;
            case TaxiingOut: {
                checkArgument(Position.create(currentVatsimPosition).isInAirport());
                final Geo.Coords newPosition = Geo.destination(
                        getCurrentVatsimPositionCoords(),
                        0,
                        TAXI_SPEED_KTS * (2.0 / 60.0));
                currentVatsimPosition.setLatitude(newPosition.getLat());
                currentVatsimPosition.setLongitude(newPosition.getLon());
                break;
            }
            case Flying, FlyingWhileShortDisconnect: {
                final Geo.Coords destinationCoords = net.simforge.refdata.airports.Airports.get().findByIcao(simulationActualDestinationAirportIcao).orElseThrow().getCoords();
                final Geo.Coords currentVatsimPositionCoords = simulationMode == SimulationMode.Flying
                        ? getCurrentVatsimPositionCoords()
                        : Geo.coords(positionWhileFlyingOffline.getLatitude(), positionWhileFlyingOffline.getLongitude());
                final double bearing = Geo.bearing(currentVatsimPositionCoords, destinationCoords);
                final int tas = AircraftPerformanceDatabase.getPerformance(simulationMode == SimulationMode.Flying ? currentVatsimPosition.getFpAircraft() : positionWhileFlyingOffline.getFpAircraft()).orElseThrow().getCruiseTas();
                final Geo.Coords newPosition = Geo.destination(currentVatsimPositionCoords, bearing, tas * (2.0 / 60.0));

                if (Geo.distance(currentVatsimPositionCoords, destinationCoords) < Geo.distance(newPosition, destinationCoords)) {
                    if (simulationMode == SimulationMode.Flying) {
                        simulationMode = SimulationMode.TimeToLand;
                    } else {
                        throw new IllegalStateException("TimeToLand reached while in FlyingWhileShortDisconnect");
                    }
                } else {
                    if (simulationMode == SimulationMode.Flying) {
                        currentVatsimPosition.setLatitude(newPosition.getLat());
                        currentVatsimPosition.setLongitude(newPosition.getLon());
                    } else {
                        positionWhileFlyingOffline.setLatitude(newPosition.getLat());
                        positionWhileFlyingOffline.setLongitude(newPosition.getLon());
                    }
                }
                break;
            }
            case TaxiingIn: {
                checkArgument(Position.create(currentVatsimPosition).isInAirport());
                final Geo.Coords destinationCoords = net.simforge.refdata.airports.Airports.get().findByIcao(simulationActualDestinationAirportIcao).orElseThrow().getCoords();
                final Geo.Coords newPosition = Geo.destination(
                        destinationCoords,
                        Math.random() * 360,
                        TAXI_SPEED_KTS * (2.0 / 60.0) / 2);
                currentVatsimPosition.setLatitude(newPosition.getLat());
                currentVatsimPosition.setLongitude(newPosition.getLon());
                break;
            }
        }
    }

    private Geo.Coords getCurrentVatsimPositionCoords() {
        return Geo.coords(currentVatsimPosition.getLatitude(), currentVatsimPosition.getLongitude());
    }

    private Report toReport(String report) {
        final Report r = new Report();
        r.setId(1L);
        r.setReport(report);
        return r;
    }

    private enum SimulationMode {
        Nope,
        TaxiingOut,
        Flying,
        FlyingWhileShortDisconnect,
        TimeToLand,
        TaxiingIn,
    }
}
