package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.EventLog;
import net.simforge.airways2.world.datamodel.FlightMissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlightMissionControl {
    private static final Logger log = LoggerFactory.getLogger(FlightMissionControl.class);

    private final World world;

    public FlightMissionControl(final World world) {
        this.world = world;
    }

    public void startOrCancel(final FlightMissions.Mission mission) {
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

        final FlightMissions.Status actualStatus = mission.getStatus();
        if (actualStatus != FlightMissions.Status.Dispatched) {
            mission.setStatus(FlightMissions.Status.Cancelled);

            int pilot = 0; // todo ak3 remove it when pilot is introduced
            world.log(EventLog.EventType.FlightCancelled, EventLog.pilotId(pilot), mission, aircraft);
            log.info("Pilot {}, flight {} - flight cancelled - flight actual state {} while expected {}", pilot, mission.getId(), actualStatus, FlightMissions.Status.Dispatched);

            return;
        }

        if (aircraft.getOperationalStatus() != Aircrafts.OperationalStatus.Idle
                || aircraft.getLocationStatus() != Aircrafts.LocationStatus.ParkedAtAirport
                || aircraft.getLocationAirportId() != mission.getDepartureAirportId()) {
            mission.setStatus(FlightMissions.Status.Cancelled);

            int pilot = 0; // todo ak3 remove it when pilot is introduced
            world.log(EventLog.EventType.FlightCancelled, EventLog.pilotId(pilot), mission, aircraft);
            log.info("Pilot {}, flight {} - flight cancelled - aircraft {} actual operational status {}, location status {}, location airport {}",
                    pilot, mission.getId(), aircraft.getRegNo(), aircraft.getOperationalStatus(), aircraft.getLocationStatus(), aircraft.getLocationAirportId());

            return;
        }

        mission.setStatus(FlightMissions.Status.Preflight);
        mission.setHeartbeatTime(world.getWorldTime() + Time.TICK);
        aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Active);
        aircraft.setFlightMissionId(mission.getId());
        // todo ak3 pilot/pilots/cabin crew - set status

        int pilot = 0; // todo ak3 remove it when pilot is introduced
        world.log(EventLog.EventType.FlightStarted, EventLog.pilotId(pilot), mission, aircraft, EventLog.airportId(mission.getDepartureAirportId()));
        log.info("Pilot {}, flight {} - flight started and in Preflight status, aircraft {} is activated", pilot, mission.getId(), aircraft.getRegNo());
    }

    public void blocksOff(final FlightMissions.Mission mission) {
        mission.setStatus(FlightMissions.Status.Departure);
        mission.setActualDepartureTime(world.getWorldTime());

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

        // todo ak3 scheduling.fireEvent(session, BlocksOff.class, flight);

        // todo ak3   (was not implemented in #old) pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        aircraft.setLocationStatus(Aircrafts.LocationStatus.TaxiingOut);

        int pilot = 0; // todo ak3 remove it when pilot is introduced
        world.log(EventLog.EventType.AircraftDepartedFromGate, EventLog.pilotId(pilot), mission, aircraft, EventLog.airportId(mission.getDepartureAirportId()));
        log.info("Pilot {}, flight {} - aircraft {} departed from gate at {}", pilot, mission.getId(), aircraft.getRegNo(), world.airports().getIcao(mission.getDepartureAirportId()));
    }

    public void takeoff(final FlightMissions.Mission mission) {
        // todo ak3 Pilot pilot = ctx.getPilot();

        mission.setStatus(FlightMissions.Status.Flying);
        mission.setActualTakeoffTime(world.getWorldTime());

        // todo ak3 scheduling.fireEvent(session, Takeoff.class, flight);

        // todo ak3 (not implemented in #old) pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        // todo ak3 Person person = pilot.getPerson();
        // todo ak3 person.setLocationAirport(null);

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        aircraft.setLocationStatus(Aircrafts.LocationStatus.Flying);

        final Airports.Airport locationAirport = world.airports().byId(aircraft.getLocationAirportId()).orElseThrow();
        aircraft.setLocationAirportId(0);
        aircraft.setLocationLatitude(locationAirport.getLatitude());
        aircraft.setLocationLongitude(locationAirport.getLongitude());

        int pilot = 0; // todo ak3 remove it when pilot is introduced
        world.log(EventLog.EventType.AircraftTakeoff, EventLog.pilotId(pilot), mission, aircraft, EventLog.airportId(mission.getDepartureAirportId()));
        log.info("Pilot {}, flight {} - aircraft {} took off at {}", pilot, mission.getId(), aircraft.getRegNo(), world.airports().getIcao(mission.getDepartureAirportId()));
    }

    public void landing(final FlightMissions.Mission mission) {
        // todo ak3 Pilot pilot = ctx.getPilot();
        // todo ak3 Person person = pilot.getPerson();
        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

        mission.setStatus(FlightMissions.Status.Arrival);
        mission.setActualLandingTime(world.getWorldTime());

        // todo ak3 scheduling.fireEvent(session, Landing.class, flight);

        // todo ak3        person.setLocationAirport(flight.getToAirport());
        // todo ak3               pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        final Airports.Airport locationAirport = world.airports().byId(mission.getDestinationAirportId()).orElseThrow();

        aircraft.setLocationStatus(Aircrafts.LocationStatus.TaxiingIn);
        aircraft.setLocationAirportId(locationAirport.getId());
        aircraft.setLocationLatitude(locationAirport.getLatitude());
        aircraft.setLocationLongitude(locationAirport.getLongitude());

        int pilot = 0; // todo ak3 remove it when pilot is introduced
        world.log(EventLog.EventType.AircraftLanding, EventLog.pilotId(pilot), mission, aircraft, EventLog.airportId(mission.getDestinationAirportId()));
        log.info("Pilot {}, flight {} - aircraft {} landed at {}", pilot, mission.getId(), aircraft.getRegNo(), world.airports().getIcao(mission.getDestinationAirportId()));
    }

    public void blocksOn(final FlightMissions.Mission mission) {
        mission.setStatus(FlightMissions.Status.Postflight);
        mission.setActualArrivalTime(world.getWorldTime());

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();

        final Airports.Airport locationAirport = world.airports().byId(mission.getDestinationAirportId()).orElseThrow();

        aircraft.setLocationStatus(Aircrafts.LocationStatus.ParkedAtAirport);
        aircraft.setLocationAirportId(locationAirport.getId());
        aircraft.setLocationLatitude(locationAirport.getLatitude());
        aircraft.setLocationLongitude(locationAirport.getLongitude());

        // todo ak3 scheduling.fireEvent(session, BlocksOn.class, flight);

        // todo ak3               pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        int pilot = 0; // todo ak3 remove it when pilot is introduced
        world.log(EventLog.EventType.AircraftArrivedToGate, EventLog.pilotId(pilot), mission, aircraft, EventLog.airportId(mission.getDestinationAirportId()));
        log.info("Pilot {}, flight {} - aircraft {} arrived to gate at {}", pilot, mission.getId(), aircraft.getRegNo(), world.airports().getIcao(mission.getDestinationAirportId()));
    }

    public void finish(final FlightMissions.Mission mission) {
        mission.setStatus(FlightMissions.Status.Finished);

        // todo ak3 pilot.setStatus(Pilot.Status.Idle);
        // todo ak3               pilot.setHeartbeatDt(timeMachine.now().plusMinutes(1));

        final Aircrafts.Aircraft aircraft = world.aircrafts().byId(mission.getAircraftId()).orElseThrow();
        aircraft.setOperationalStatus(Aircrafts.OperationalStatus.Idle);
        aircraft.setFlightMissionId(0);

        // todo ak3 pilotAssignment.setStatus(PilotAssignment.Status.Done);

        // todo ak3 aircraftAssignment.setStatus(AircraftAssignment.Status.Done);

        int pilot = 0; // todo ak3 remove it when pilot is introduced
        world.log(EventLog.EventType.FlightFinished, EventLog.pilotId(pilot), mission, aircraft, EventLog.airportId(mission.getDestinationAirportId()));
        log.info("Pilot {}, flight {} - flight finished", pilot, mission.getId());
    }
}
