package net.simforge.airways2.pilottracker.track;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.commons.misc.Geo;

@AllArgsConstructor
@Data
public class TrackPosition {
    private final long time;
    private final boolean onGround;
    private final Geo.Coords coords;
    private final String airportIcao;
}
