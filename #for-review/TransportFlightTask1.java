/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.stage3;

import net.simforge.airways.stage2.status.Status;
import net.simforge.airways.stage2.status.StatusHandler;
import net.simforge.airways.stage3.model.flight.Flight;
import net.simforge.airways.stage3.model.flight.TransportFlight;
import net.simforge.commons.legacy.BM;
import net.simforge.commons.misc.JavaTime;

import java.time.LocalDateTime;

public class TransportFlightTask extends StorageHeartbeatTask<TransportFlight> {

    private final StatusHandler statusHandler;
    private final TransportFlightOps ops;
    private final FlightOps flightOps;

    public TransportFlightTask() {
        super("TransportFlightTask");
        this.statusHandler = StatusHandler.create(this);
        this.ops = new TransportFlightOps(storage, Airways3App.getSessionFactory());
        this.flightOps = new FlightOps(storage, Airways3App.getSessionFactory());
    }

    @Override
    protected void startup() {
        super.startup();

        setBaseSleepTime(5000);
        BM.setLoggingPeriod(600000);
    }

    @Override
    protected TransportFlight heartbeat(TransportFlight transportFlight) {
        BM.start("TransportFlight.heartbeat");
        try {
            statusHandler.perform(StatusHandler.context(transportFlight));
            return transportFlight;
        } finally {
            BM.stop();
        }
    }

    @Override
    protected BaseOps<TransportFlight> getBaseOps() {
        storage.invalidate(); // todo REMOVE!!!
        return ops;
    }

    @Status(code = TransportFlight.Status.Scheduled)
    private void scheduled(StatusHandler.Context<TransportFlight> _ctx) {
        // initial status
        // to schedule next run to beginning of checkin and then switch to "checkin" status

        BM.start("TransportFlight.scheduled");
        try {

            TransportFlight transportFlight = _ctx.getSubject();
            Flight flight = flightOps.getOrLoad(transportFlight.getFlight().getId());

            Integer flightStatus = flight.getStatus();
            if (!(flightStatus == Flight.Status.Planned
                    || flightStatus == Flight.Status.Assigned
                    || flightStatus == Flight.Status.PreFlight)) {

                ops.switchToStatus(transportFlight, TransportFlight.Status.CancellationRequested, "Cancellation - Appropriate flight is in incorrect status");
                return;

            }

            FlightTimeline flightTimeline = FlightTimeline.byFlight(flight);
            LocalDateTime preflightStartsAt = flightTimeline.getStart().getEstimatedTime();
            LocalDateTime checkinStartsAt = preflightStartsAt.minusHours(3);

            if (checkinStartsAt.isBefore(JavaTime.nowUtc())) {

                ops.switchToStatus(transportFlight, TransportFlight.Status.Checkin, "Check-in started");

            } else {

                ops.arrangeHeartbeatAt(transportFlight, checkinStartsAt);

            }
        } finally {
            BM.stop();
        }
    }

    @Status(code = TransportFlight.Status.Checkin)
    private void checkin(StatusHandler.Context<TransportFlight> _ctx) {
        // load journey itineraries for the flight
        // make a list of journeys
        // notify suitable journeys that checkin is in progress and they should arrive to airport
        // do it repeatedly until a milestone "finish of checkin time" then go to "waiting for boarding" status

        // todo nov17 IMPLEMENT WORK WITH JOURNEYS

        BM.start("TransportFlight.checkin");
        try {

            TransportFlight transportFlight = _ctx.getSubject();
            Flight flight = storage.get(Flight.class, transportFlight.getFlight().getId());

            // todo nov17 cancellation check

            FlightTimeline flightTimeline = FlightTimeline.byFlight(flight);
            LocalDateTime preflightStartsAt = flightTimeline.getStart().getEstimatedTime();
            LocalDateTime checkinStartsAt = preflightStartsAt.minusHours(3);
            LocalDateTime checkinEndsAt = preflightStartsAt;

            if (checkinEndsAt.isBefore(JavaTime.nowUtc())) {

                ops.switchToStatusWithoutHeartbeat(transportFlight, TransportFlight.Status.WaitingForBoarding, "Check-in completed, waiting for boarding");

            } else {

                ops.arrangeHeartbeatAt(transportFlight, checkinStartsAt);

            }
        } finally {
            BM.stop();
        }
    }

    @Status(code = TransportFlight.Status.WaitingForBoarding)
    private void waitingForBoarding(StatusHandler.Context<TransportFlight> _ctx) {
        // it will wait for a change to "boarding" status by pilot
        // nothing to do
    }

    @Status(code = TransportFlight.Status.Boarding)
    private void boarding(StatusHandler.Context<TransportFlight> _ctx) {
        // pilot makes a call "lets start boarding" and this status comes
        // it loads list of journeys and puts them in "on board" status
        // this status will have several runs - it is possible to board around 20-30 passengers per minute
        // once everyone will be on board - it will switch to "waiting for departure" status

        // todo nov17 IMPLEMENT WORK WITH JOURNEYS

        BM.start("TransportFlight.boarding");
        try {

            TransportFlight transportFlight = _ctx.getSubject();
            Flight flight = storage.get(Flight.class, transportFlight.getFlight().getId());
            FlightTimeline flightTimeline = FlightTimeline.byFlight(flight);
            LocalDateTime preflightStartsAt = flightTimeline.getStart().getEstimatedTime();
            LocalDateTime boardingEndsAt = preflightStartsAt.plusMinutes(10);

            if (boardingEndsAt.isBefore(JavaTime.nowUtc())) {

                ops.switchToStatusWithoutHeartbeat(transportFlight, TransportFlight.Status.WaitingForBoarding, "Boarding completed, waiting for departure");

            } else {

                ops.arrangeHeartbeatAt(transportFlight, boardingEndsAt);

            }
        } finally {
            BM.stop();
        }

    }

    @Status(code = TransportFlight.Status.WaitingForDeparture)
    private void waitingForDeparture(StatusHandler.Context<TransportFlight> ctx) {
        // it will wait for a change to "Departure" status by pilot
        // nothing to do
    }

    @Status(code = TransportFlight.Status.Departure)
    private void departing(StatusHandler.Context<TransportFlight> ctx) {
        // this status is set by pilot
        // nothing to do
    }

    @Status(code = TransportFlight.Status.Flying)
    private void flying(StatusHandler.Context<TransportFlight> ctx) {
        // this status is set by pilot
        // nothing to do
    }

    @Status(code = TransportFlight.Status.Arrival)
    private void arrival(StatusHandler.Context<TransportFlight> ctx) {
        // this status is set by pilot
        // nothing to do
    }

    @Status(code = TransportFlight.Status.WaitingForUnboarding)
    private void waitingForUnboarding(StatusHandler.Context<TransportFlight> ctx) {
        // this status is set by pilot together with "PostFlight" status in flight object
        // nothing to do
    }

    @Status(code = TransportFlight.Status.Unboarding)
    private void unboarding(StatusHandler.Context<TransportFlight> ctx) {
        // pilot makes a call "lets start unboarding" and this status comes
        // it loads list of journeys and puts them in "arrived" status
        // this status will have several runs - it is possible to unboard around 30-50 passengers per minute
        // once everyone will be unboarded - it will switch to "finished" status
    }

    @Status(code = TransportFlight.Status.Finished)
    private void finished(StatusHandler.Context<TransportFlight> ctx) {
        // nothing to do
        // reset heartbeat
    }

    @Status(code = TransportFlight.Status.CancellationRequested)
    private void cancellationRequested(StatusHandler.Context<TransportFlight> _ctx) {
        // load journey itineraries
        // notify them in some way (it will be clear after ticketing stage)

        // todo nov17 IMPLEMENT WORK WITH JOURNEYS

        ops.switchToStatusWithoutHeartbeat(_ctx.getSubject(), TransportFlight.Status.Cancelled, "Cancellation - done");
    }

    @Status(code = TransportFlight.Status.Cancelled)
    private void cancelled(StatusHandler.Context<TransportFlight> ctx) {
        // nothing to do
    }
}
