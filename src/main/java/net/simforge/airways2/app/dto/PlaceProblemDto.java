package net.simforge.airways2.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.processors.PilotExpectedPlace;

import java.util.Optional;

/**
 * The warning that the pilot is not expected where a flight departs (PilotExpectedPlace): the roster tells it above the flight, the page of the booking under the start of the mission.
 * expected: the airport where the pilot is expected (ICAO); departure: the airport where the flight (the aircraft) departs from; afterFrom, afterTo: the flight of the roster after
 * which the pilot is at the expected airport, both null when it is the place where the pilot is now.
 */
@Data
@AllArgsConstructor
public class PlaceProblemDto {
    private String expected;
    private String departure;
    private String afterFrom;
    private String afterTo;

    /**
     * The warning for a flight which departs at departureWorldTime from departureAirportId, null when the pilot is expected there or the place is not known.
     */
    public static PlaceProblemDto check(final World world, final int userId, final int departureWorldTime, final int excludedMissionId, final int departureAirportId) {
        final Optional<PilotExpectedPlace.Expected> expected = PilotExpectedPlace.expected(world, userId, departureWorldTime, excludedMissionId);
        if (expected.isEmpty() || expected.get().airport().getId() == departureAirportId) {
            return null;
        }
        final var after = expected.get().afterFlight();
        return new PlaceProblemDto(
                expected.get().airport().getIcao(),
                world.airports().getIcao(departureAirportId).orElse("?"),
                after == null ? null : world.airports().getIcao(after.getDepartureAirportId()).orElse("?"),
                after == null ? null : world.airports().getIcao(after.getDestinationAirportId()).orElse("?"));
    }
}
