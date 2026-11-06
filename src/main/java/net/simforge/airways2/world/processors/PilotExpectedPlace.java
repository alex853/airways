package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Persons;

import java.util.Comparator;
import java.util.Optional;

/**
 * Where a pilot is expected to be when a flight departs: at the airport where the flight before it (in the roster of the user) lands, with the divert and the actual landing taken
 * into account; before the very first flight of the roster it is where the pilot is now, but only when the pilot stands at an airport (in a city, a hotel or on the way nobody can tell
 * the airport). The bookings and the roster only warn about it, they do not forbid anything (the pilot comes to the aircraft himself).
 */
public final class PilotExpectedPlace {
    private PilotExpectedPlace() {
    }

    /**
     * @param airport     the airport where the pilot is expected, null when the pilot is now not at an airport (then pilotAt tells where)
     * @param afterFlight the flight of the roster after which the pilot is there, null when it is the place where the pilot is now
     * @param pilotAt     only when airport is null: the city of the pilot or "a hotel"
     */
    public record Expected(Airports.Airport airport, FlightMissions.Mission afterFlight, String pilotAt) {
    }

    /**
     * The expected place of the pilot of the user at the departure at departureWorldTime (seconds); the flight excludedMissionId (the flight itself, 0 for none) is not counted
     * as "the flight before". The cancelled flights do not count. Empty when the place is not known.
     */
    public static Optional<Expected> expected(final World world, final int userId, final int departureWorldTime, final int excludedMissionId) {
        if (userId <= 0) {
            return Optional.empty();
        }
        final Optional<FlightMissions.Mission> before = world.flightMissions().allByUserId(userId)
                .filter(m -> m.getId() != excludedMissionId)
                // a finished flight says nothing about where the pilot is now (the pilot could have moved since: warp, a trip), the person location does
                .filter(m -> m.getStatus() != FlightMissions.Status.Cancelled && m.getStatus() != FlightMissions.Status.Finished)
                .filter(m -> m.getPlannedDepartureWorldTime() < departureWorldTime)
                .max(Comparator.comparingInt(FlightMissions.Mission::getPlannedArrivalWorldTime));
        if (before.isPresent()) {
            return world.airports().byId(landingAirportId(before.get())).map(airport -> new Expected(airport, before.get(), null));
        }

        // before the first unfinished flight: where the pilot is now
        final Optional<Persons.Person> person = world.persons().byUserId(userId);
        if (person.isEmpty()) {
            return Optional.empty();
        }
        switch (person.get().getLocationType()) {
            case Airport:
                return world.airports().byId(person.get().getLocationId()).map(airport -> new Expected(airport, null, null));
            case City:
                return Optional.of(new Expected(null, null, world.cities().byId(person.get().getLocationId()).map(Cities.City::getName).orElse("a city")));
            case Hotel:
                return Optional.of(new Expected(null, null, "a hotel"));
            default:
                // on the way: nobody can tell
                return Optional.empty();
        }
    }

    // where the aircraft of a flight is after it: the airport where it has landed, else the declared divert, else the destination
    static int landingAirportId(final FlightMissions.Mission mission) {
        if (mission.getActualLandingAirportId() > 0) {
            return mission.getActualLandingAirportId();
        }
        if (mission.isDivertDeclared() && mission.getDivertAirportId() > 0) {
            return mission.getDivertAirportId();
        }
        return mission.getDestinationAirportId();
    }
}
