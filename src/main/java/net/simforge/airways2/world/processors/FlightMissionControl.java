package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;

// todo ak1 add precondition checks for all the statuses
public class FlightMissionControl {
    private static final Logger log = LoggerFactory.getLogger(FlightMissionControl.class);

    private final World world;

    public FlightMissionControl(final World world) {
        this.world = world;
    }

    private TransportFlightControl transportFlightControl() {
        return world.transportFlightControl();
    }

    public void startOrCancel(final FlightMissions.Mission mission) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

        final FlightMissions.Status actualStatus = mission.getStatus();
        if (actualStatus != FlightMissions.Status.Dispatched) {
            mission.setStatus(FlightMissions.Status.Cancelled);

            world.log(EventLog.EventType.FlightCancelled, EventLog.pilotId(0), mission, aircraft);
            log.info("f/m #{} - flight cancelled - flight actual state {} while expected {}", mission.getId(), actualStatus, FlightMissions.Status.Dispatched);
            // todo ak0 t/f actions in case of flight cancellation

            return;
        }

        if (aircraft.getOperationalStatus() != Aircrafts.OperationalStatus.Idle
                || aircraft.getLocationStatus() != Aircrafts.LocationStatus.ParkedAtAirport
                || aircraft.getLocationAirportId() != mission.getDepartureAirportId()) {
            mission.setStatus(FlightMissions.Status.Cancelled);

            world.log(EventLog.EventType.FlightCancelled, EventLog.pilotId(0), mission, aircraft);
            log.info("f/m #{} - flight cancelled - aircraft {} actual operational status {}, location status {}, location airport {}",
                    mission.getId(), aircraft.getRegNo(), aircraft.getOperationalStatus(), aircraft.getLocationStatus(), aircraft.getLocationAirportId());
            // todo ak0 t/f actions in case of flight cancellation

            return;
        }

        mission.setStatus(FlightMissions.Status.Preflight);
        mission.setHeartbeatTime(world.getWorldTime() + Time.TICK);
        aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Active);
        aircraft.setFlightMissionId(mission.getId());
        // todo ak3 pilot/pilots/cabin crew - set status

        world.log(EventLog.EventType.FlightStarted, EventLog.pilotId(0), mission, aircraft, EventLog.airportId(mission.getDepartureAirportId()));
        log.info("f/m #{} - flight started and in Preflight status, aircraft {} is activated", mission.getId(), aircraft.getRegNo());
    }

    public void cancelFlightAndReturnAircraftToDepartureAirport(final FlightMissions.Mission mission) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

        final FlightMissions.Status actualStatus = mission.getStatus();
        checkArgument(actualStatus == FlightMissions.Status.Preflight
                || actualStatus == FlightMissions.Status.Departure
                || actualStatus == FlightMissions.Status.Flying);

        mission.setStatus(FlightMissions.Status.Cancelled);

        final Airports.Airport departureAirport = world.airports().byId(mission.getDepartureAirportId()).orElseThrow();

        aircraft.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);
        aircraft.setLocationAirportId(mission.getDepartureAirportId());
        aircraft.setLocationLatitude(departureAirport.getLatitude());
        aircraft.setLocationLongitude(departureAirport.getLongitude());

        aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Idle);
        aircraft.setFlightMissionId(0);

        world.log(EventLog.EventType.FlightCancelled, EventLog.pilotId(0), mission, aircraft);
        log.info("f/m #{} - flight cancelled from {}", mission.getId(), actualStatus);

        // todo ak0 t/f actions in case of flight cancellation
    }

    public void blocksOff(final FlightMissions.Mission mission) {
        mission.setStatus(FlightMissions.Status.Departure);
        mission.setActualDepartureWorldTime(world.getWorldTime());

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        aircraft.setLocationStatus(Aircrafts.LocationStatus.TaxiingOut);

        world.transportFlights().byFlightMissionId(mission.getId()).ifPresent(transportFlightControl()::whenFlightDepartsFromGate);

        world.log(EventLog.EventType.AircraftDepartedFromGate, EventLog.pilotId(0), mission, aircraft, EventLog.airportId(mission.getDepartureAirportId()));
        log.info("f/m #{} - aircraft {} departed from gate at {}", mission.getId(), aircraft.getRegNo(), world.airports().getIcao(mission.getDepartureAirportId()));
    }

    public void takeoff(final FlightMissions.Mission mission) {
        mission.setStatus(FlightMissions.Status.Flying);
        mission.setActualTakeoffWorldTime(world.getWorldTime());

        // todo ak3 pilot/pilots/cabin crew - set status, location

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        aircraft.setLocationStatus(Aircrafts.LocationStatus.Flying);

        final Airports.Airport locationAirport = world.airports().byId(aircraft.getLocationAirportId()).orElseThrow();
        aircraft.setLocationAirportId(0);
        aircraft.setLocationLatitude(locationAirport.getLatitude());
        aircraft.setLocationLongitude(locationAirport.getLongitude());

        world.transportFlights().byFlightMissionId(mission.getId()).ifPresent(transportFlightControl()::whenFlightTakeoffs);

        world.log(EventLog.EventType.AircraftTakeoff, EventLog.pilotId(0), mission, aircraft, EventLog.airportId(mission.getDepartureAirportId()));
        log.info("f/m #{} - aircraft {} took off at {}", mission.getId(), aircraft.getRegNo(), world.airports().getIcao(mission.getDepartureAirportId()));
    }

    public void landing(final FlightMissions.Mission mission, Airports.Airport landingAirport) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

        mission.setStatus(FlightMissions.Status.Arrival);
        mission.setActualLandingWorldTime(world.getWorldTime());
        mission.setActualLandingAirportId(landingAirport.getId());

        // todo ak3 pilot/pilots/cabin crew - set status, location

        aircraft.setLocationStatus(Aircrafts.LocationStatus.TaxiingIn);
        aircraft.setLocationAirportId(landingAirport.getId());
        aircraft.setLocationLatitude(landingAirport.getLatitude());
        aircraft.setLocationLongitude(landingAirport.getLongitude());

        world.transportFlights().byFlightMissionId(mission.getId()).ifPresent(transportFlightControl()::whenFlightLands);

        world.log(EventLog.EventType.AircraftLanding, EventLog.pilotId(0), mission, aircraft, EventLog.airportId(mission.getActualLandingAirportId()));
        log.info("f/m #{} - aircraft {} landed at {}", mission.getId(), aircraft.getRegNo(), world.airports().getIcao(mission.getActualLandingAirportId()));
    }

    public void blocksOn(final FlightMissions.Mission mission) {
        mission.setStatus(FlightMissions.Status.Postflight);
        mission.setActualArrivalWorldTime(world.getWorldTime());

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

        final Airports.Airport locationAirport = world.airports().byId(mission.getDestinationAirportId()).orElseThrow();

        aircraft.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);
        aircraft.setLocationAirportId(locationAirport.getId());
        aircraft.setLocationLatitude(locationAirport.getLatitude());
        aircraft.setLocationLongitude(locationAirport.getLongitude());

        // todo ak3 pilot/pilots/cabin crew - set status, location

        world.transportFlights().byFlightMissionId(mission.getId()).ifPresent(transportFlightControl()::whenFlightArrivesToGate);

        world.log(EventLog.EventType.AircraftArrivedToGate, EventLog.pilotId(0), mission, aircraft, EventLog.airportId(mission.getDestinationAirportId()));
        log.info("f/m #{} - aircraft {} arrived to gate at {}", mission.getId(), aircraft.getRegNo(), world.airports().getIcao(mission.getDestinationAirportId()));
    }

    public void finish(final FlightMissions.Mission mission) {
        mission.setStatus(FlightMissions.Status.Finished);

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Idle);
        aircraft.setFlightMissionId(0);

        // todo ak3 pilot/pilots/cabin crew - set status, location
        // todo ak3 pilot assignements / aircraft assignments?

        world.log(EventLog.EventType.FlightFinished, EventLog.pilotId(0), mission, aircraft, EventLog.airportId(mission.getDestinationAirportId()));
        log.info("f/m #{} - flight finished", mission.getId());

        world.airport2airportDailyFlightStats().incrementTodayCount(mission.getDepartureAirportId(), mission.getActualLandingAirportId());
    }
}
