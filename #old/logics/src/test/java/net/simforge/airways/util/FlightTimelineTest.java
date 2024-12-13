/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.util;

import net.simforge.airways.TestRefData;
import net.simforge.commons.gckls2com.GC;
import net.simforge.commons.gckls2com.GCAirport;
import net.simforge.commons.misc.Geo;
import net.simforge.commons.misc.JavaTime;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.Assert.assertEquals;

public class FlightTimelineTest {
    @Test
    public void testEstimatedTime() {
        FlightTimeline timeline = FlightTimeline.byFlyingTime(Duration.ofMinutes(30));
        timeline.scheduleDepartureTime(LocalDateTime.of(2016, 1, 1, 10, 0));

        assertEquals(LocalTime.of(10, 50), timeline.getBlocksOn().getScheduledTime().toLocalTime());
        assertEquals(LocalTime.of(10, 50), timeline.getBlocksOn().getEstimatedTime().toLocalTime());

        timeline.getBlocksOff().setActualTime(LocalDateTime.of(2016, 1, 1, 10, 5));

        assertEquals(LocalTime.of(10, 50), timeline.getBlocksOn().getScheduledTime().toLocalTime());
        assertEquals(LocalTime.of(10, 55), timeline.getBlocksOn().getEstimatedTime().toLocalTime());
    }

    @Test
    public void testScheduledDuration() {
        FlightTimeline timeline = FlightTimeline.byFlyingTime(Duration.ofMinutes(30));
        Duration flightDuration = timeline.getScheduledDuration(timeline.getBlocksOff(), timeline.getBlocksOn());
        assertEquals(Duration.ofMinutes(30 + 10 + 10), flightDuration);
    }

    @Test
    public void testEGLLtoEFHK() throws IOException {
        GCAirport egll = GC.findAirport("EGLL");
        GCAirport efhk = GC.findAirport("EFHK");

        Geo.Coords egllCoords = new Geo.Coords(egll.getLat(), egll.getLon());
        Geo.Coords efhkCoords = new Geo.Coords(efhk.getLat(), efhk.getLon());

        SimpleFlight simpleFlight = SimpleFlight.forRoute(egllCoords, efhkCoords, TestRefData.getA320Data());

        Duration flyingTime = simpleFlight.getTotalTime();
        // seconds are being lost here
        flyingTime = JavaTime.hhmmToDuration(JavaTime.toHhmm(flyingTime));

        assertEquals(Duration.ofHours(2).plusMinutes(30), flyingTime);

        FlightTimeline timeline = FlightTimeline.byFlyingTime(flyingTime);
        Duration flightDuration = timeline.getScheduledDuration(timeline.getBlocksOff(), timeline.getBlocksOn());

        assertEquals(Duration.ofHours(2).plusMinutes(50), flightDuration);

        LocalDateTime scheduledDepartureTime = LocalDateTime.of(2016, 1, 1, 10, 0);
        timeline.scheduleDepartureTime(scheduledDepartureTime);

        assertEquals(scheduledDepartureTime, timeline.getBlocksOff().getScheduledTime());
        assertEquals(scheduledDepartureTime, timeline.getBlocksOff().getEstimatedTime());

        LocalDateTime scheduledArrivalTime = scheduledDepartureTime.plus(flightDuration);

        assertEquals(scheduledArrivalTime, timeline.getBlocksOn().getScheduledTime());
        assertEquals(scheduledArrivalTime, timeline.getBlocksOn().getEstimatedTime());
    }
}
