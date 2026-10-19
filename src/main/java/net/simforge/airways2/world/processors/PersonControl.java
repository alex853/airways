package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.datamodel.PilotLogbook;
import net.simforge.airways2.world.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Changes of the state and of the location of persons (pilots). Every change is logged as 'p/s #id'.
 * <p>
 * The person of a flight is the person of the user of the mission, missions without a user (NPC) have no person yet. A user without
 * a person is not an error for the flight, it is only logged.
 */
public class PersonControl {
    private static final Logger log = LoggerFactory.getLogger(PersonControl.class);

    private final World world;

    public PersonControl(final World world) {
        this.world = world;
    }

    // ---- the flight of the person: Idle at the airport -> OnDuty at start -> no location in the flight -> airport at landing -> Idle at finish

    /**
     * The flight is started: the pilot goes on duty.
     */
    public void onMissionStarted(final FlightMissions.Mission mission) {
        findPerson(mission).ifPresent(person -> {
            if (person.getState() != Persons.State.Idle) {
                log.warn("p/s #{} - f/m #{} is started while the person is {}, not Idle", person.getId(), mission.getId(), person.getState());
            }
            setState(person, Persons.State.OnDuty, "f/m #" + mission.getId() + " started");
        });
    }

    /**
     * The aircraft took off: the pilot is nowhere until the landing.
     */
    public void onMissionTakeoff(final FlightMissions.Mission mission) {
        findPerson(mission).ifPresent(person ->
                setLocation(person, Persons.LocationType.None, 0, "f/m #" + mission.getId() + " took off"));
    }

    /**
     * The aircraft landed: the pilot is at the landing airport.
     */
    public void onMissionLanding(final FlightMissions.Mission mission, final Airports.Airport landingAirport) {
        findPerson(mission).ifPresent(person ->
                setLocation(person, Persons.LocationType.Airport, landingAirport.getId(), "f/m #" + mission.getId() + " landed"));
    }

    /**
     * The flight is finished: the pilot is free and the flight goes to the logbook of the pilot.
     */
    public void onMissionFinished(final FlightMissions.Mission mission) {
        findPerson(mission).ifPresent(person -> {
            setState(person, Persons.State.Idle, "f/m #" + mission.getId() + " finished");
            writeLogbook(person, mission);
        });
    }

    /**
     * The flight is cancelled after the start: the pilot which is on duty is free and is at the airport where the aircraft is parked now.
     * A person who is not on duty (the flight was not started, so the person was not touched by it) is left as it is.
     */
    public void onMissionCancelled(final FlightMissions.Mission mission, final int airportId) {
        findPerson(mission)
                .filter(person -> person.getState() == Persons.State.OnDuty)
                .ifPresent(person -> {
                    final String reason = "f/m #" + mission.getId() + " cancelled";
                    setState(person, Persons.State.Idle, reason);
                    setLocation(person, Persons.LocationType.Airport, airportId, reason);
                });
    }

    private void writeLogbook(final Persons.Person person, final FlightMissions.Mission mission) {
        final int blocksOff = mission.getActualDepartureWorldTime();
        final int blocksOn = mission.getActualArrivalWorldTime();
        if (blocksOff < Time.START_TIME_EPOCH_SECONDS || blocksOn < blocksOff) {
            log.warn("p/s #{} - f/m #{} is not written to the logbook, its blocks off {} and blocks on {} are not valid", person.getId(), mission.getId(), blocksOff, blocksOn);
            return;
        }

        final int toAirportId = mission.getActualLandingAirportId() > 0 ? mission.getActualLandingAirportId() : mission.getDestinationAirportId();

        final PilotLogbook.Entry entry = world.pilotLogbook().create(
                person.getId(),
                mission.getAircraftId(),
                mission.getDepartureAirportId(),
                toAirportId,
                blocksOff,
                blocksOn);

        entry.setNightTakeoff(mission.isNightTakeoff());
        entry.setNightLanding(mission.isNightLanding());
        // seconds to tenths of hour (360 s), half up, the night time cannot be longer than the flight time
        entry.setNightTimeTenths(Math.min(entry.getFlightTimeTenths(), (mission.getNightSeconds() + 180) / 360));

        log.info("p/s #{} - logbook entry #{}: {} -> {}, {} min, f/m #{}", person.getId(), entry.getId(),
                world.airports().getIcao(entry.getFromAirportId()).orElse("#" + entry.getFromAirportId()),
                world.airports().getIcao(entry.getToAirportId()).orElse("#" + entry.getToAirportId()),
                entry.getDurationMinutes(), mission.getId());
    }

    // ---- low level, it logs every change

    public void setState(final Persons.Person person, final Persons.State state, final String reason) {
        final Persons.State oldState = person.getState();
        if (oldState == state) {
            return;
        }
        person.setState(state);
        log.info("p/s #{} - state {} -> {} ({})", person.getId(), oldState, state, reason);
    }

    public void setLocation(final Persons.Person person, final Persons.LocationType type, final int id, final String reason) {
        final Persons.LocationType oldType = person.getLocationType();
        final int oldId = person.getLocationId();
        if (oldType == type && oldId == id) {
            return;
        }
        person.setLocation(type, id);
        log.info("p/s #{} - location {} -> {} ({})", person.getId(), describeLocation(oldType, oldId), describeLocation(type, id), reason);
    }

    private Optional<Persons.Person> findPerson(final FlightMissions.Mission mission) {
        if (mission.getUserId() <= 0) {
            return Optional.empty();
        }
        final Optional<Persons.Person> person = world.persons().byUserId(mission.getUserId());
        if (person.isEmpty()) {
            log.warn("f/m #{} - user #{} has no person, the state and the location of the person are not changed", mission.getId(), mission.getUserId());
        }
        return person;
    }

    private String describeLocation(final Persons.LocationType type, final int id) {
        if (type == null) {
            return "?";
        }
        return switch (type) {
            case None -> "None";
            case City -> "City " + world.cities().byId(id).map(c -> c.getName()).orElse("#" + id);
            case Airport -> "Airport " + world.airports().getIcao(id).orElse("#" + id);
            case Hotel -> "Hotel #" + id;
        };
    }
}
