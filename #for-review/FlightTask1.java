/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage3;

import net.simforge.airways.stage2.status.Otherwise;
import net.simforge.airways.stage2.status.Status;
import net.simforge.airways.stage2.status.StatusHandler;
import net.simforge.airways.stage3.model.flight.Flight;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.JavaTime;
import net.simforge.commons.runtime.BaseTask;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FlightTask extends BaseTask {

    private final StatusHandler statusHandler;
    private final EntityStorage storage;

    public FlightTask() {
        super("FlightTask");
        this.statusHandler = StatusHandler.create(this);
        storage = Airways3App.getStorage();
    }

    @Override
    protected void startup() {
        super.startup();

        setBaseSleepTime(5000);
        BM.setLoggingPeriod(600000);
    }

    @Override
    protected void process() {
        FlightOps ops = storage.getFlightOps();

        List<Flight> flights = ops.whereHeartbeatDtBelow(JavaTime.nowUtc(), 100);
        for (Flight object : flights) {
            LocalDateTime before = object.getHeartbeatDt();

            try {
                object = heartbeat(object);
            } catch (Throwable t) {
                logger.error("Error during Heartbeat for " + object, t);
            }

            LocalDateTime after = storage.get(object).getHeartbeatDt();

            if (after != null
                    && (after.equals(before)
                    || after.isBefore(after))) {
                logger.warn(String.format("HeartbeatDt for %s is not changed or changed in wrong way: before %s, after %s", object, before, after));
            }
        }
    }

    private Flight heartbeat(Flight flight) {
        BM.start("FlightTask.heartbeat");
        try {
            statusHandler.perform(StatusHandler.context(flight));
            return flight;
        } finally {
            BM.stop();
        }
    }

    @SuppressWarnings("unused")
    @Status(code = Flight.Status.Planned)
    private void planned(StatusHandler.Context<Flight> ctx) {
        BM.start("FlightTask.planned");
        try {

            Flight flight = ctx.getSubject();
            FlightOps flightOps = storage.getFlightOps();

            logger.debug("Flight " + flight + " - status: planned");

            LocalDateTime allocationDeadline = flight.getScheduledDepartureTime().minusHours(1);
            LocalDateTime now = JavaTime.nowUtc();

            if (allocationDeadline.isBefore(now)) {
                flightOps.cancelFlight(flight, "Allocation deadline");
                return;
            }

//            int allocationWindowDays = 3;
//            LocalDateTime startOfAllocationWindow = allocationDeadline.minusDays(allocationWindowDays);
//
//            if (now.isBefore(startOfAllocationWindow)) {
//                flightOps.arrangeHeartbeatAt(flight, startOfAllocationWindow);
//                return;
//            }
//            FlightOps.allocateFlight(session, flight);


            // nov17 - stupid solution - allocation starts few hours before flight
            // it just looks for available pilot and aircraft at the departure point
            // todo nov17 trivial allocation logic

            LocalDateTime beginningOfAllocation = allocationDeadline.minusHours(3);

            if (now.isBefore(beginningOfAllocation)) {
                flightOps.arrangeHeartbeatAt(flight, beginningOfAllocation);
                return;
            }

            flightOps.doStupidAllocation(flight);

            FlightContext flightCtx = FlightContext.fromCache(flight);
            flight = flightCtx.getFlight();
            if (!flightCtx.isFullyAssigned()) {
                flightOps.arrangeHeartbeatIn(flight, TimeUnit.HOURS.toMillis(1));
            }

        } finally {
            BM.stop();
        }
    }

    /*@SuppressWarnings("unused")
    @Status(code = Flight.Status.Assigned)
    private void assigned(StatusHandler.Context<Flight> ctx) {
        BM.start("FlightTask.assigned");
        try {

            // notify and update TransportFlight as it will need to communicate with journeys & passengers

            Flight flight = ctx.getSubject();
            FlightTimeline flightTimeline = FlightTimeline.byFlight(flight);
            flight.getTransportFlight();

        } finally {
            BM.stop();
        }
    }*/

    @SuppressWarnings("unused")
    @Otherwise
    private void otherwise(StatusHandler.Context<Flight> ctx) {
        BM.start("FlightTask.otherwise");
        try {

            Flight flight = ctx.getSubject();

            logger.warn("Flight {} - OTHERWISE handler - removing heartbeat", flight);

            FlightOps flightOps = storage.getFlightOps();
            flightOps.arrangeHeartbeatAt(flight, null, "WARN - Otherwise handler");

        } finally {
            BM.stop();
        }
    }

}
