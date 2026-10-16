package net.simforge.airways2.world.computations;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stages of the timeline: start - 30 min - blocks off - 10 min - takeoff - flying - landing - 10 min - blocks on - 20 min - finish.
 */
class FlightTimelineTest {
    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2026, 1, 10, 12, 0);
    private static final LocalDateTime ARRIVAL = LocalDateTime.of(2026, 1, 10, 15, 0);
    // 3 hours between the departure and the arrival, 10 minutes of departure and 10 minutes of arrival are inside, 160 minutes are left for flying
    private static final Duration FLYING = Duration.ofMinutes(160);

    private static LocalDateTime at(final int hour, final int minute) {
        return LocalDateTime.of(2026, 1, 10, hour, minute);
    }

    private static FlightTimeline scheduled() {
        return FlightTimeline.byScheduledDepartureArrivalTime(DEPARTURE, ARRIVAL);
    }

    // ---- schedule

    @Test
    public void test__scheduled_by_departure_and_arrival__all_milestones_get_scheduled_times() {
        final FlightTimeline timeline = scheduled();

        assertEquals(at(11, 30), timeline.getStart().getScheduledTime());
        assertEquals(at(12, 0), timeline.getBlocksOff().getScheduledTime());
        assertEquals(at(12, 10), timeline.getTakeoff().getScheduledTime());
        assertEquals(at(14, 50), timeline.getLanding().getScheduledTime());
        assertEquals(at(15, 0), timeline.getBlocksOn().getScheduledTime());
        assertEquals(at(15, 20), timeline.getFinish().getScheduledTime());
    }

    @Test
    public void test__scheduled_by_departure_and_arrival__estimated_times_are_equal_to_scheduled_ones_and_there_are_no_actual_times() {
        final FlightTimeline timeline = scheduled();

        for (final FlightTimeline.Milestone milestone : milestones(timeline)) {
            assertEquals(milestone.getScheduledTime(), milestone.getEstimatedTime());
            assertNull(milestone.getActualTime());
        }
    }

    @Test
    public void test__by_flying_time__nothing_is_scheduled_until_the_departure_time_is_given() {
        final FlightTimeline timeline = FlightTimeline.byFlyingTime(FLYING);

        for (final FlightTimeline.Milestone milestone : milestones(timeline)) {
            assertNull(milestone.getScheduledTime());
            assertNull(milestone.getEstimatedTime());
        }
        assertEquals(Duration.ofMinutes(30 + 10 + 160 + 10 + 20), timeline.getScheduledDuration(timeline.getStart(), timeline.getFinish()));
    }

    @Test
    public void test__by_flying_time__scheduling_the_departure_time_gives_the_same_timeline_as_by_departure_and_arrival() {
        final FlightTimeline timeline = FlightTimeline.byFlyingTime(FLYING);

        timeline.scheduleDepartureTime(DEPARTURE);

        final FlightTimeline expected = scheduled();
        final FlightTimeline.Milestone[] actualMilestones = milestones(timeline);
        final FlightTimeline.Milestone[] expectedMilestones = milestones(expected);
        for (int i = 0; i < actualMilestones.length; i++) {
            assertEquals(expectedMilestones[i].getScheduledTime(), actualMilestones[i].getScheduledTime(), "scheduled, milestone " + i);
            assertEquals(expectedMilestones[i].getEstimatedTime(), actualMilestones[i].getEstimatedTime(), "estimated, milestone " + i);
        }
    }

    @Test
    public void test__rescheduling_the_departure_time_moves_all_the_milestones() {
        final FlightTimeline timeline = scheduled();

        timeline.scheduleDepartureTime(at(18, 0));

        assertEquals(at(17, 30), timeline.getStart().getScheduledTime());
        assertEquals(at(18, 0), timeline.getBlocksOff().getScheduledTime());
        assertEquals(at(18, 10), timeline.getTakeoff().getScheduledTime());
        assertEquals(at(20, 50), timeline.getLanding().getScheduledTime());
        assertEquals(at(21, 0), timeline.getBlocksOn().getScheduledTime());
        assertEquals(at(21, 20), timeline.getFinish().getScheduledTime());
        assertEquals(at(21, 0), timeline.getBlocksOn().getEstimatedTime());
    }

    @Test
    public void test__interval_shorter_than_departure_and_arrival_stages_is_refused() {
        // 10 minutes of departure and 10 minutes of arrival do not fit into 15 minutes, the flying time would be -5 minutes
        assertThrows(IllegalArgumentException.class,
                () -> FlightTimeline.byScheduledDepartureArrivalTime(DEPARTURE, DEPARTURE.plusMinutes(15)));
        assertThrows(IllegalArgumentException.class,
                () -> FlightTimeline.byScheduledDepartureArrivalTime(DEPARTURE, DEPARTURE.plusMinutes(20).minusSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> FlightTimeline.byScheduledDepartureArrivalTime(ARRIVAL, DEPARTURE));
    }

    @Test
    public void test__interval_exactly_of_the_departure_and_arrival_stages_gives_zero_flying() {
        final FlightTimeline timeline = FlightTimeline.byScheduledDepartureArrivalTime(DEPARTURE, DEPARTURE.plusMinutes(20));

        assertEquals(Duration.ZERO, timeline.getScheduledDuration(timeline.getTakeoff(), timeline.getLanding()));
        assertEquals(at(12, 10), timeline.getTakeoff().getScheduledTime());
        assertEquals(at(12, 10), timeline.getLanding().getScheduledTime());
        assertEquals(at(12, 20), timeline.getBlocksOn().getScheduledTime());
    }

    @Test
    public void test__negative_flying_time_is_refused_and_zero_is_accepted() {
        assertThrows(IllegalArgumentException.class, () -> FlightTimeline.byFlyingTime(Duration.ofMinutes(-1)));

        final FlightTimeline timeline = FlightTimeline.byFlyingTime(Duration.ZERO);
        timeline.scheduleDepartureTime(DEPARTURE);
        assertEquals(timeline.getTakeoff().getScheduledTime(), timeline.getLanding().getScheduledTime());
    }

    // ---- scheduled durations

    @Test
    public void test__scheduled_duration_between_milestones() {
        final FlightTimeline timeline = scheduled();

        assertEquals(Duration.ZERO, timeline.getScheduledDuration(timeline.getTakeoff(), timeline.getTakeoff()));
        assertEquals(Duration.ofMinutes(30), timeline.getScheduledDuration(timeline.getStart(), timeline.getBlocksOff()));
        assertEquals(FLYING, timeline.getScheduledDuration(timeline.getTakeoff(), timeline.getLanding()));
        assertEquals(Duration.ofMinutes(3 * 60), timeline.getScheduledDuration(timeline.getBlocksOff(), timeline.getBlocksOn()));
        assertEquals(Duration.ofMinutes(30 + 180 + 20), timeline.getScheduledDuration(timeline.getStart(), timeline.getFinish()));
    }

    @Test
    public void test__scheduled_duration_with_the_wrong_order_of_milestones_is_refused() {
        final FlightTimeline timeline = scheduled();

        assertThrows(IllegalArgumentException.class, () -> timeline.getScheduledDuration(timeline.getLanding(), timeline.getTakeoff()));
        assertThrows(IllegalArgumentException.class, () -> timeline.getScheduledDuration(timeline.getFinish(), timeline.getStart()));
    }

    // ---- actual times and estimates

    @Test
    public void test__actual_time_is_stored_and_the_scheduled_time_does_not_change() {
        final FlightTimeline timeline = scheduled();

        timeline.getBlocksOff().setActualTime(at(12, 7));

        assertEquals(at(12, 7), timeline.getBlocksOff().getActualTime());
        assertEquals(at(12, 0), timeline.getBlocksOff().getScheduledTime());
        assertEquals(at(12, 10), timeline.getTakeoff().getScheduledTime());
    }

    @Test
    public void test__late_blocks_off_shifts_the_estimates_of_all_the_following_milestones_but_not_its_own_one() {
        final FlightTimeline timeline = scheduled();

        timeline.getBlocksOff().setActualTime(at(12, 7));

        assertEquals(at(11, 30), timeline.getStart().getEstimatedTime());
        assertEquals(at(12, 0), timeline.getBlocksOff().getEstimatedTime()); // the estimate of the milestone itself is based on the previous one
        assertEquals(at(12, 17), timeline.getTakeoff().getEstimatedTime());
        assertEquals(at(14, 57), timeline.getLanding().getEstimatedTime());
        assertEquals(at(15, 7), timeline.getBlocksOn().getEstimatedTime());
        assertEquals(at(15, 27), timeline.getFinish().getEstimatedTime());
    }

    @Test
    public void test__takeoff_actual_time_without_the_earlier_ones_shifts_the_following_estimates() {
        final FlightTimeline timeline = scheduled();

        timeline.getTakeoff().setActualTime(at(12, 20));

        assertEquals(at(12, 0), timeline.getBlocksOff().getEstimatedTime());
        assertEquals(at(12, 10), timeline.getTakeoff().getEstimatedTime());
        assertEquals(at(15, 0), timeline.getLanding().getEstimatedTime());
        assertEquals(at(15, 10), timeline.getBlocksOn().getEstimatedTime());
        assertEquals(at(15, 30), timeline.getFinish().getEstimatedTime());
    }

    @Test
    public void test__early_landing_pulls_the_following_estimates_earlier() {
        final FlightTimeline timeline = scheduled();
        timeline.getBlocksOff().setActualTime(at(12, 0));
        timeline.getTakeoff().setActualTime(at(12, 10));

        timeline.getLanding().setActualTime(at(14, 40));

        assertEquals(at(14, 50), timeline.getLanding().getEstimatedTime()); // 12:10 + 160 minutes of flying
        assertEquals(at(14, 50), timeline.getBlocksOn().getEstimatedTime());
        assertEquals(at(15, 10), timeline.getFinish().getEstimatedTime());
    }

    @Test
    public void test__every_next_actual_time_overrides_the_estimates_built_on_the_previous_ones() {
        final FlightTimeline timeline = scheduled();

        timeline.getBlocksOff().setActualTime(at(12, 30));
        assertEquals(at(15, 30), timeline.getBlocksOn().getEstimatedTime());

        timeline.getTakeoff().setActualTime(at(12, 35));
        assertEquals(at(15, 25), timeline.getBlocksOn().getEstimatedTime());

        timeline.getLanding().setActualTime(at(15, 20));
        assertEquals(at(15, 30), timeline.getBlocksOn().getEstimatedTime());

        timeline.getBlocksOn().setActualTime(at(15, 33));
        assertEquals(at(15, 53), timeline.getFinish().getEstimatedTime());
    }

    @Test
    public void test__null_clears_the_actual_time_and_the_estimates_go_back_to_the_schedule() {
        final FlightTimeline timeline = scheduled();
        timeline.getBlocksOff().setActualTime(at(12, 30));

        timeline.getBlocksOff().setActualTime(null);

        assertNull(timeline.getBlocksOff().getActualTime());
        for (final FlightTimeline.Milestone milestone : milestones(timeline)) {
            assertEquals(milestone.getScheduledTime(), milestone.getEstimatedTime());
        }
    }

    @Test
    public void test__the_same_actual_time_twice_gives_the_same_result() {
        final FlightTimeline timeline = scheduled();

        timeline.getTakeoff().setActualTime(at(12, 20));
        final LocalDateTime firstBlocksOn = timeline.getBlocksOn().getEstimatedTime();
        timeline.getTakeoff().setActualTime(at(12, 20));

        assertEquals(firstBlocksOn, timeline.getBlocksOn().getEstimatedTime());
    }

    @Test
    public void test__actual_times_in_the_unusual_order_are_accepted() {
        // the moments are detected from the tracking with a lag, the order is not checked on purpose
        final FlightTimeline timeline = scheduled();

        timeline.getLanding().setActualTime(at(14, 0));
        timeline.getTakeoff().setActualTime(at(14, 30));

        assertEquals(at(14, 0), timeline.getLanding().getActualTime());
        assertEquals(at(14, 30), timeline.getTakeoff().getActualTime());
        assertEquals(at(14, 10), timeline.getBlocksOn().getEstimatedTime()); // based on the landing actual time
    }

    @Test
    public void test__rescheduling_after_an_actual_time_keeps_the_actual_time_and_recalculates_the_estimates() {
        final FlightTimeline timeline = scheduled();
        timeline.getBlocksOff().setActualTime(at(12, 7));

        timeline.scheduleDepartureTime(at(13, 0));

        assertEquals(at(12, 7), timeline.getBlocksOff().getActualTime());
        assertEquals(at(13, 0), timeline.getBlocksOff().getScheduledTime());
        assertEquals(at(12, 17), timeline.getTakeoff().getEstimatedTime()); // based on the actual time, not on the schedule
    }

    @Test
    public void test__actual_time_of_the_timeline_which_is_not_scheduled_is_refused() {
        final FlightTimeline timeline = FlightTimeline.byFlyingTime(FLYING);

        assertThrows(IllegalStateException.class, () -> timeline.getBlocksOff().setActualTime(at(12, 0)));
        assertNull(timeline.getBlocksOff().getActualTime());
    }

    private static FlightTimeline.Milestone[] milestones(final FlightTimeline timeline) {
        return new FlightTimeline.Milestone[]{
                timeline.getStart(),
                timeline.getBlocksOff(),
                timeline.getTakeoff(),
                timeline.getLanding(),
                timeline.getBlocksOn(),
                timeline.getFinish()
        };
    }
}
