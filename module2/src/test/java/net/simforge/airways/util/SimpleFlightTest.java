/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.util;

import junit.framework.TestCase;
import net.simforge.airways.persistence.model.aircraft.AircraftType;
import net.simforge.commons.gckls2com.GC;
import net.simforge.commons.gckls2com.GCAirport;
import net.simforge.commons.misc.Geo;

import java.io.IOException;
import java.time.Duration;

public class SimpleFlightTest extends TestCase {

    public void testA320_20NM() {
        AircraftType data = TestRefData.getA320Data();
        SimpleFlight simpleFlight = SimpleFlight.forRoute(new Geo.Coords(0, 0), Geo.destination(new Geo.Coords(0, 0), 0, 20), data);
        assertEquals(6, simpleFlight.getTotalTime().toMinutes());
    }

    public void testA320_50NM() {
        AircraftType data = TestRefData.getA320Data();
        SimpleFlight simpleFlight = SimpleFlight.forRoute(new Geo.Coords(0, 0), Geo.destination(new Geo.Coords(0, 0), 0, 50), data);
        assertEquals(15, simpleFlight.getTotalTime().toMinutes());
    }

    public void testA320_100NM() {
        AircraftType data = TestRefData.getA320Data();
        SimpleFlight simpleFlight = SimpleFlight.forRoute(new Geo.Coords(0, 0), Geo.destination(new Geo.Coords(0, 0), 0, 100), data);
        assertEquals(26, simpleFlight.getTotalTime().toMinutes());
    }

    public void testA320_200NM() {
        AircraftType data = TestRefData.getA320Data();
        SimpleFlight simpleFlight = SimpleFlight.forRoute(new Geo.Coords(0, 0), Geo.destination(new Geo.Coords(0, 0), 0, 200), data);
        assertEquals(43, simpleFlight.getTotalTime().toMinutes());
    }

    public void testA320_300NM() {
        AircraftType data = TestRefData.getA320Data();
        SimpleFlight simpleFlight = SimpleFlight.forRoute(new Geo.Coords(0, 0), Geo.destination(new Geo.Coords(0, 0), 0, 300), data);
        assertEquals(56, simpleFlight.getTotalTime().toMinutes());
    }

    public void testA320_400NM() {
        AircraftType data = TestRefData.getA320Data();
        SimpleFlight simpleFlight = SimpleFlight.forRoute(new Geo.Coords(0, 0), Geo.destination(new Geo.Coords(0, 0), 0, 400), data);
        assertEquals(69, simpleFlight.getTotalTime().toMinutes());
    }

    public void testA320_500NM() {
        AircraftType data = TestRefData.getA320Data();
        SimpleFlight simpleFlight = SimpleFlight.forRoute(new Geo.Coords(0, 0), Geo.destination(new Geo.Coords(0, 0), 0, 500), data);
        assertEquals(83, simpleFlight.getTotalTime().toMinutes());
    }

    public void testA320_1000NM() {
        AircraftType data = TestRefData.getA320Data();
        SimpleFlight simpleFlight = SimpleFlight.forRoute(new Geo.Coords(0, 0), Geo.destination(new Geo.Coords(0, 0), 0, 1000), data);
        assertEquals(150, simpleFlight.getTotalTime().toMinutes());
    }

    public void testC152_20NM() {
        AircraftType data = TestRefData.getC152Data();
        SimpleFlight simpleFlight = SimpleFlight.forRoute(new Geo.Coords(0, 0), Geo.destination(new Geo.Coords(0, 0), 0, 20), data);
        assertEquals(15, simpleFlight.getTotalTime().toMinutes());
    }

    public void testC152_50NM() {
        AircraftType data = TestRefData.getC152Data();
        SimpleFlight simpleFlight = SimpleFlight.forRoute(new Geo.Coords(0, 0), Geo.destination(new Geo.Coords(0, 0), 0, 50), data);
        assertEquals(32, simpleFlight.getTotalTime().toMinutes());
    }

    public void testC152_100NM() {
        AircraftType data = TestRefData.getC152Data();
        SimpleFlight simpleFlight = SimpleFlight.forRoute(new Geo.Coords(0, 0), Geo.destination(new Geo.Coords(0, 0), 0, 100), data);
        assertEquals(60, simpleFlight.getTotalTime().toMinutes());
    }

    public void testC152_200NM() {
        AircraftType data = TestRefData.getC152Data();
        SimpleFlight simpleFlight = SimpleFlight.forRoute(new Geo.Coords(0, 0), Geo.destination(new Geo.Coords(0, 0), 0, 200), data);
        assertEquals(116, simpleFlight.getTotalTime().toMinutes());
    }

    public void testEGLLtoEFHK() throws IOException {
        GCAirport egll = GC.findAirport("EGLL");
        GCAirport efhk = GC.findAirport("EFHK");

        Geo.Coords egllCoords = new Geo.Coords(egll.getLat(), egll.getLon());
        Geo.Coords efhkCoords = new Geo.Coords(efhk.getLat(), efhk.getLon());

        SimpleFlight simpleFlight = SimpleFlight.forRoute(egllCoords, efhkCoords, TestRefData.getA320Data());

        Duration totalTime = Duration.ofHours(2).plusMinutes(30).plusSeconds(29);
        assertEquals(totalTime, simpleFlight.getTotalTime());

        SimpleFlight.Position beforeTakeoff = simpleFlight.getAircraftPosition(Duration.ofSeconds(-1));
        assertEquals(SimpleFlight.Position.Stage.BeforeTakeoff, beforeTakeoff.getStage());
        assertTrue(egllCoords.isSame(beforeTakeoff.getCoords()));

        SimpleFlight.Position takeoff = simpleFlight.getAircraftPosition(Duration.ofSeconds(0));
        assertEquals(SimpleFlight.Position.Stage.Climb, takeoff.getStage());
        assertTrue(egllCoords.isSame(takeoff.getCoords()));

        SimpleFlight.Position beforeTOC = simpleFlight.getAircraftPosition(simpleFlight.getClimbTime().minusSeconds(1));
        assertEquals(SimpleFlight.Position.Stage.Climb, beforeTOC.getStage());
        assertEquals(simpleFlight.getClimbDistance(), Geo.distance(egllCoords, beforeTOC.getCoords()), 0.25);
        assertEquals(simpleFlight.getTotalDistance() - simpleFlight.getClimbDistance(), Geo.distance(efhkCoords, beforeTOC.getCoords()), 0.25);

        SimpleFlight.Position afterTOC = simpleFlight.getAircraftPosition(simpleFlight.getClimbTime().plusSeconds(1));
        assertEquals(SimpleFlight.Position.Stage.Cruise, afterTOC.getStage());
        assertEquals(simpleFlight.getClimbDistance(), Geo.distance(egllCoords, afterTOC.getCoords()), 0.25);
        assertEquals(simpleFlight.getTotalDistance() - simpleFlight.getClimbDistance(), Geo.distance(efhkCoords, afterTOC.getCoords()), 0.25);

        SimpleFlight.Position beforeTOD = simpleFlight.getAircraftPosition(simpleFlight.getClimbTime().plus(simpleFlight.getCruiseTime()).minusSeconds(1));
        assertEquals(SimpleFlight.Position.Stage.Cruise, beforeTOD.getStage());
        assertEquals(simpleFlight.getClimbDistance() + simpleFlight.getCruiseDistance(), Geo.distance(egllCoords, beforeTOD.getCoords()), 0.25);
        assertEquals(simpleFlight.getDescentDistance(), Geo.distance(efhkCoords, beforeTOD.getCoords()), 0.25);

        SimpleFlight.Position afterTOD = simpleFlight.getAircraftPosition(simpleFlight.getClimbTime().plus(simpleFlight.getCruiseTime()).plusSeconds(1));
        assertEquals(SimpleFlight.Position.Stage.Descent, afterTOD.getStage());
        assertEquals(simpleFlight.getClimbDistance() + simpleFlight.getCruiseDistance(), Geo.distance(egllCoords, afterTOD.getCoords()), 0.25);
        assertEquals(simpleFlight.getDescentDistance(), Geo.distance(efhkCoords, afterTOD.getCoords()), 0.25);

        SimpleFlight.Position landing = simpleFlight.getAircraftPosition(simpleFlight.getTotalTime());
        assertEquals(SimpleFlight.Position.Stage.Descent, landing.getStage());
        assertTrue(efhkCoords.isSame(landing.getCoords()));

        SimpleFlight.Position afterLanding = simpleFlight.getAircraftPosition(simpleFlight.getTotalTime().plusSeconds(1));
        assertEquals(SimpleFlight.Position.Stage.AfterLanding, afterLanding.getStage());
        assertTrue(efhkCoords.isSame(afterLanding.getCoords()));
    }
}
