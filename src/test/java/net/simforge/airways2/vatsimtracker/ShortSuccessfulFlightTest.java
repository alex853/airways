package net.simforge.airways2.vatsimtracker;

import net.simforge.airways2.app.WorldRunnerBean;
import net.simforge.airways2.app.vatsimtracker.PilotContext;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.networkview.core.Position;
import org.junit.jupiter.api.Test;

// todo ak0 I am writing these vatsim tracker tests
public class ShortSuccessfulFlightTest {

    private final int pilotNumber = 799999;
    private PilotContext pilotContext;
    private World world;
    private Aircrafts.Aircraft aircraft;
    private Airports.Airport egll;
    private Airports.Airport egcc;
    private Airports.Airport egkk;
    private Airports.Airport egss;

    @Test
    public void test() {
/*
        offline().reports(5);
        parkedAt(egll, aircraft).flightplan(egll, egcc).reports(1);
        asserts();
        same().reports(5);
        asserts();
        blocksOff();
        asserts();
        taxiingOut().reports(5);
        asserts();
        takeoff();
        asserts();
        flying().reports(10);
        asserts();
        flying().untilReachingDestination();
        asserts();
        landing();
        asserts();
        taxiingIn().reports(3);
        asserts();
        blocksOn();
        asserts();
*/
    }


    /**
     * One tick is
     * - 2 mins of world running
     * - 1 vatsim report processed
     */
    private void processOneTick() {
        final int worldInitialTime = world.getWorldTime();
        final int finishTime = worldInitialTime + 2 * Time.ONE_MINUTE;
        while (world.getWorldTime() < finishTime) {
            world.process(world.getWorldTime() + 10);
        }

        final Position position = null; // todo offline or online
        final WorldRunnerBean worldBean = null;

        // code below reproduces logic of processing report position for one single pilot
        if (pilotContext != null) {
            if (position.isPositionKnown()) {
                pilotContext.nextReportPosition(position);
            } else {
                pilotContext.noPositionInReport(position.getReportInfo().getReport());
            }
        } else {
            if (position.isPositionKnown()) {
                pilotContext = new PilotContext(worldBean, pilotNumber);
                pilotContext.newPilotContextInAirport(position);
            }
        }

        if (pilotContext != null) {
            if (pilotContext.shouldBeRemoved()) {
                pilotContext = null;
            }
        }
    }
}
