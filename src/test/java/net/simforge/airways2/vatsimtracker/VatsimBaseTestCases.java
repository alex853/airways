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

public class VatsimBaseTestCases {

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
    private PilotContext pilotContext;
    private String nextReport;

    private SimulationMode simulationMode = SimulationMode.Nope;
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
    }

    @Test
    public void ideal_flight_from_egll_to_egcc_____flight_should_be_finished() {
        assertAircraftParkedAtAirportAndIdle(egll);

        runWorldMins(10);
        vatsimPilotGoesOnlineParkedAt(egll, aircraft);
        vatsimPilotFilesFlightplan("EGLL", "EGCC");
        runWorldMins(2);

        assertFlightMissionPreflight(egll, egcc);
        assertAircraftParkedAtAirportAndActive(egll);

        vatsimPilotStartsTaxiingOut();
        runWorldMins(3);

        assertFlightMissionDeparting();
        assertAircraftTaxiingOut(egll);

        runWorldMins(3);
        vatsimPilotMakesTakeoff();
        runWorldMins(3);

        assertFlightMissionFlying();
        assertAircraftFlying();

        runWorldMins(10);

        assertFlightMissionFlying();
        assertAircraftFlying();

        runWorldUntilVatsimPilotReachesDestination();
        vatsimPilotMakesLanding();
        runWorldMins(3);

        assertFlightMissionArriving();
        assertAircraftTaxiingIn(egcc);

        runWorldMins(3);
        vatsimPilotPutsBlocksOn();
        runWorldMins(7);

        assertAircraftParkedAtAirportAndIdle(egcc);
        assertFlightMissionFinished(egll, egcc);

        vatsimPilotGoesOffline();
        runWorldMins(10);

        assertAircraftParkedAtAirportAndIdle(egcc);
        assertFlightMissionFinished(egll, egcc);
    }

    @Test
    public void correct_flightplan_filed___then_offline_on_ground_____flight_should_be_cancelled() {
        assertAircraftParkedAtAirportAndIdle(egll);

        runWorldMins(10);
        vatsimPilotGoesOnlineParkedAt(egll, aircraft);
        vatsimPilotFilesFlightplan("EGLL", "EGCC");
        runWorldMins(2);

        assertFlightMissionPreflight(egll, egcc);
        assertAircraftParkedAtAirportAndActive(egll);

        runWorldMins(10);
        vatsimPilotGoesOffline();
        runWorldMins(5);

        assertFlightMissionCancelled();
        assertAircraftParkedAtAirportAndIdle(egll);
    }

    @Test
    public void invalid_flightplan___then_offline_while_parked_____flight_should_not_be_created() {
        assertAircraftParkedAtAirportAndIdle(egll);

        runWorldMins(10);
        vatsimPilotGoesOnlineParkedAt(egll, aircraft);
        vatsimPilotFilesFlightplan("EGLL", "UWWW");
        runWorldMins(2);

        assertNoFlightMissionCreated();
        assertAircraftParkedAtAirportAndIdle(egll);
        assertPilotContextPresent();

        vatsimPilotGoesOffline();
        runWorldMins(2);

        assertPilotContextAbsent();
        assertAircraftParkedAtAirportAndIdle(egll);
    }

    @Test
    public void invalid_flightplan___then_takeoff_____flight_should_not_be_created() {
        assertAircraftParkedAtAirportAndIdle(egll);

        runWorldMins(10);
        vatsimPilotGoesOnlineParkedAt(egll, aircraft);
        vatsimPilotFilesFlightplan("EGLL", "UWWW");
        runWorldMins(2);

        assertNoFlightMissionCreated();
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

    // reconnect on ground...
    // jump on ground...
    // change of aircraft type
    // change of flightplan from correct to incorrect
    // change of flightplan from incorrect to correct
    // change of flightplan from incorrect to incorrect
    // change of flightplan from correct to another correct
    // landing out of the world
    // landing on wrong airport

    // reconnect while pilot context is in irreversible status

    // short disconnect cases

    private void assertPilotContextPresent() {
        assertNotNull(pilotContext);
    }

    private void assertPilotContextAbsent() {
        assertNull(pilotContext);
    }

    private void assertNoFlightMissionCreated() {
        assertFalse(world.flightMissions().byId(1).isPresent());
    }

    private void assertFlightMissionPreflight(final Airports.Airport from, final Airports.Airport to) {
        final FlightMissions.Mission flight = getFlight1();
        assertEquals(FlightMissions.Status.Preflight, flight.getStatus());
        assertEquals(from.getId(), flight.getDepartureAirportId());
        assertEquals(to.getId(), flight.getDestinationAirportId());
    }

    private void assertFlightMissionDeparting() {
        final FlightMissions.Mission flight = getFlight1();
        assertEquals(FlightMissions.Status.Departure, flight.getStatus());
    }

    private void assertFlightMissionFlying() {
        final FlightMissions.Mission flight = getFlight1();
        assertEquals(FlightMissions.Status.Flying, flight.getStatus());
    }

    private void assertFlightMissionArriving() {
        final FlightMissions.Mission flight = getFlight1();
        assertEquals(FlightMissions.Status.Arrival, flight.getStatus());
    }

    private void assertFlightMissionFinished(final Airports.Airport from, final Airports.Airport to) {
        final FlightMissions.Mission flight = getFlight1();
        assertEquals(FlightMissions.Status.Finished, flight.getStatus());
        assertEquals(from.getId(), flight.getDepartureAirportId());
        assertEquals(to.getId(), flight.getDestinationAirportId());
    }

    private void assertFlightMissionCancelled() {
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

    private void vatsimPilotGoesOffline() {
        currentVatsimPosition = null;
    }

    private void vatsimPilotGoesOnlineParkedAt(final Airports.Airport airport, final Aircrafts.Aircraft aircraft) {
        checkArgument(simulationMode == SimulationMode.Nope);
        if (currentVatsimPosition == null) {
            currentVatsimPosition = new ReportPilotPosition();
        }
        currentVatsimPosition.setPilotNumber(pilotNumber);
        currentVatsimPosition.setFpAircraft(world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow().getIcao());
        currentVatsimPosition.setOnGround(true);
        currentVatsimPosition.setHeading(0);
        currentVatsimPosition.setAltitude(10);
        currentVatsimPosition.setGroundspeed(0);
        currentVatsimPosition.setLatitude((double) airport.getLatitude());
        currentVatsimPosition.setLongitude((double) airport.getLongitude());
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
        currentVatsimPosition.setOnGround(false);
    }

    private void runWorldUntilVatsimPilotReachesDestination() {
        checkNotNull(currentVatsimPosition);
        checkArgument(!Position.create(currentVatsimPosition).isOnGround());
        checkArgument(simulationMode == SimulationMode.Flying);

        while (simulationMode != SimulationMode.TimeToLand) {
            runWorldMins(2);
        }
    }

    private void vatsimPilotMakesLanding() {
        checkNotNull(currentVatsimPosition);
        checkArgument(!Position.create(currentVatsimPosition).isOnGround());
        checkArgument(simulationMode == SimulationMode.TimeToLand);
        simulationMode = SimulationMode.TaxiingIn;
        final Airports.Airport destination = world.airports().byIcao(pilotContext.getPlannedDestination()).orElseThrow();
        currentVatsimPosition.setOnGround(true);
        currentVatsimPosition.setLatitude((double) destination.getLatitude());
        currentVatsimPosition.setLongitude((double) destination.getLongitude());
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

        if (currentVatsimPosition != null) {
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
                case Flying: {
                    checkArgument(!Position.create(currentVatsimPosition).isOnGround());
                    final Geo.Coords destinationCoords = net.simforge.refdata.airports.Airports.get().findByIcao(currentVatsimPosition.getFpDestination()).orElseThrow().getCoords();
                    final Geo.Coords currentVatsimPositionCoords = getCurrentVatsimPositionCoords();
                    final double bearing = Geo.bearing(currentVatsimPositionCoords, destinationCoords);
                    final int tas = AircraftPerformanceDatabase.getPerformance(currentVatsimPosition.getFpAircraft()).orElseThrow().getCruiseTas();
                    final Geo.Coords newPosition = Geo.destination(currentVatsimPositionCoords, bearing, tas * (2.0 / 60.0));

                    if (Geo.distance(currentVatsimPositionCoords, destinationCoords) < Geo.distance(newPosition, destinationCoords)) {
                        simulationMode = SimulationMode.TimeToLand;
                    } else {
                        currentVatsimPosition.setLatitude(newPosition.getLat());
                        currentVatsimPosition.setLongitude(newPosition.getLon());
                    }
                    break;
                }
                case TaxiingIn: {
                    checkArgument(Position.create(currentVatsimPosition).isInAirport());
                    final Airports.Airport destination = world.airports().byIcao(currentVatsimPosition.getFpDestination()).orElseThrow();
                    final Geo.Coords newPosition = Geo.destination(
                            destination.getCoords(),
                            Math.random()*360,
                            TAXI_SPEED_KTS * (2.0 / 60.0) / 2);
                    currentVatsimPosition.setLatitude(newPosition.getLat());
                    currentVatsimPosition.setLongitude(newPosition.getLon());
                    break;
                }
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
        TimeToLand,
        TaxiingIn,
    }
}
