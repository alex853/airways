/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.util;

import net.simforge.airways.model.flight.Flight;

import java.time.Duration;
import java.time.LocalDateTime;

public class FlightTimeline {

    public static FlightTimeline byFlyingTime(Duration flyingTime) {
        FlightTimeline result = new FlightTimeline();

        result.flying.scheduledDuration = flyingTime;

        return result;
    }

    public static FlightTimeline byScheduledDepartureArrivalTime(LocalDateTime scheduledDepartureTime, LocalDateTime scheduledArrivalTime) {
        FlightTimeline result = new FlightTimeline();

        Duration duration = Duration.between(scheduledDepartureTime, scheduledArrivalTime);
        duration = duration.minus(result.departure.scheduledDuration);
        duration = duration.minus(result.arrival.scheduledDuration);

        result.flying.scheduledDuration = duration;

        result.scheduleDepartureTime(scheduledDepartureTime);

        return result;
    }

    public static FlightTimeline byFlight(Flight flight) {
        FlightTimeline timeline = byScheduledDepartureArrivalTime(flight.getScheduledDepartureTime(), flight.getScheduledArrivalTime());

        if (flight.getActualDepartureTime() != null) {
            timeline.getBlocksOff().setActualTime(flight.getActualDepartureTime());
        }

        if (flight.getActualTakeoffTime() != null) {
            timeline.getTakeoff().setActualTime(flight.getActualTakeoffTime());
        }

        if (flight.getActualLandingTime() != null) {
            timeline.getLanding().setActualTime(flight.getActualLandingTime());
        }

        if (flight.getActualArrivalTime() != null) {
            timeline.getBlocksOn().setActualTime(flight.getActualArrivalTime());
        }

        return timeline;
    }

    private Milestone start;
    private Milestone blocksOff;
    private Milestone takeoff;
    private Milestone landing;
    private Milestone blocksOn;
    private Milestone finish;

    private Stage preFlight;
    private Stage departure;
    private Stage flying;
    private Stage arrival;
    private Stage postFlight;

    private FlightTimeline() {
        start = new Milestone(MilestoneType.Started);
        blocksOff = new Milestone(MilestoneType.BlocksOff);
        takeoff = new Milestone(MilestoneType.Takeoff);
        landing = new Milestone(MilestoneType.Landing);
        blocksOn = new Milestone(MilestoneType.BlocksOn);
        finish = new Milestone(MilestoneType.Finished);

        preFlight = new Stage(start, blocksOff, Duration.ofMinutes(30));
        departure = new Stage(blocksOff, takeoff, Duration.ofMinutes(10));
        flying = new Stage(takeoff, landing, null);
        arrival = new Stage(landing, blocksOn, Duration.ofMinutes(10));
        postFlight = new Stage(blocksOn, finish, Duration.ofMinutes(20));
    }

    public Milestone getStart() {
        return start;
    }

    public Milestone getBlocksOff() {
        return blocksOff;
    }

    public Milestone getTakeoff() {
        return takeoff;
    }

    public Milestone getLanding() {
        return landing;
    }

    public Milestone getBlocksOn() {
        return blocksOn;
    }

    public Milestone getFinish() {
        return finish;
    }

    public void scheduleDepartureTime(LocalDateTime scheduledDepartureTime) {
        blocksOff.scheduledTime = scheduledDepartureTime;

        blocksOff.updateScheduleTime_up();
        blocksOff.updateScheduleTime_down();

        updateEstimatedTimes();
    }

    private void updateEstimatedTimes() {
        Milestone curr = start;
        while (true) {
            if (curr.previousStage != null) {
                LocalDateTime previousActualTime = curr.previousStage.from.actualTime;
                if (previousActualTime != null) {
                    curr.estimatedTime = previousActualTime.plus(curr.previousStage.scheduledDuration);
                } else {
                    curr.estimatedTime = curr.previousStage.from.estimatedTime.plus(curr.previousStage.scheduledDuration);
                }
            } else {
                curr.estimatedTime = curr.scheduledTime;
            }

            if (curr.nextStage != null) {
                curr = curr.nextStage.to;
            } else {
                break;
            }
        }
    }

    public Duration getScheduledDuration(Milestone from, Milestone to) {
        Duration sum = Duration.ZERO;
        Milestone curr = from;
        while (curr != to) {
            if (curr.nextStage == null) {
                throw new IllegalArgumentException("Can't find milestone. Probably wrong order of milestones is specified.");
            }
            sum = sum.plus(curr.nextStage.scheduledDuration);
            curr = curr.nextStage.to;
        }
        return sum;
    }

    public class Milestone {
        private MilestoneType type;
        private LocalDateTime scheduledTime;
        private LocalDateTime estimatedTime;
        private LocalDateTime actualTime;

        private Stage previousStage;
        private Stage nextStage;

        private Milestone(MilestoneType type) {
            this.type = type;
        }

        public LocalDateTime getScheduledTime() {
            return scheduledTime;
        }

        public LocalDateTime getEstimatedTime() {
            return estimatedTime;
        }

        public LocalDateTime getActualTime() {
            return actualTime;
        }

        public void setActualTime(LocalDateTime actualTime) {
            this.actualTime = actualTime; // todo AK checks

            updateEstimatedTimes();
        }

        private void updateScheduleTime_up() {
            if (previousStage == null) {
                return;
            }

            previousStage.from.scheduledTime = scheduledTime.minus(previousStage.scheduledDuration);
            previousStage.from.updateScheduleTime_up();
        }

        private void updateScheduleTime_down() {
            if (nextStage == null) {
                return;
            }

            nextStage.to.scheduledTime = scheduledTime.plus(nextStage.scheduledDuration);
            nextStage.to.updateScheduleTime_down();
        }
    }

    public enum MilestoneType {Started, BlocksOff, Takeoff, Landing, BlocksOn, Finished}

    public class Stage {
        private Milestone from;
        private Milestone to;
        private Duration scheduledDuration;

        private Stage(Milestone from, Milestone to, Duration scheduledDuration) {
            this.from = from;
            this.to = to;
            this.scheduledDuration = scheduledDuration;

            from.nextStage = this;
            to.previousStage = this;
        }
    }

    public enum StageType {PreFlight, Departure, Flying, Arrival, PostFlight}

}
