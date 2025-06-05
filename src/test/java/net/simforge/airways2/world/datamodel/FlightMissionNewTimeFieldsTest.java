package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.commons.misc.JavaTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

public class FlightMissionNewTimeFieldsTest {

    private World world;
    private FlightMissions.Mission mission;

    @BeforeEach
    public void beforeEach() {
        final int startTime = Time.START_TIME_EPOCH_SECONDS;
        world = World.create(new InMemoryStorageStrategy(), startTime);

        final Airports.Airport airportA = world.airports().create(0, 0, "AAA", "AAAA", "Alpha");
        final Airports.Airport airportB = world.airports().create(0, 10, "BBB", "BBBB", "Bravo");

        final AircraftTypes.AircraftType aircraftType = world.aircraftTypes().create("TTTT", "TTT");
        final Aircrafts.Aircraft aircraft = world.aircrafts().create(aircraftType, "A-BCDE", airportA);

        final int departureTime = startTime + 2* Time.ONE_HOUR;
        final int arrivalTime = departureTime + 2*Time.ONE_HOUR;
        mission = world.flightMissions()
                .createDispatchedMission(
                        aircraft,
                        airportA,
                        airportB,
                        departureTime,
                        arrivalTime);
        mission.setPlannedDepartureWorldTime(0);
        mission.setPlannedArrivalWorldTime(0);
    }

    @Test
    public void test__date_of_flight_field() {
        final LocalDate today = JavaTime.todayUtc();

        mission.setDateOfFlight(today);

        assertEquals(today, mission.getDateOfFlight());
    }

    @Test
    public void test__date_of_flight_field__set_null() {
        mission.setDateOfFlight(null);

        assertNull(mission.getDateOfFlight());
    }

    @Test
    public void test__date_of_flight_field__get_null() {
        assertNull(mission.getDateOfFlight());
    }

    @Test
    public void test__planned_departure_time_lt_field() {
        final LocalTime time = LocalTime.now();

        mission.setPlannedDepartureTimeLt(time);

        assertEquals(time.withSecond(0).withNano(0), mission.getPlannedDepartureLt());
    }

    @Test
    public void test__planned_departure_time_lt_field__get_null() {
        assertNull(mission.getPlannedDepartureLt());
    }

    @Test
    public void test__planned_departure_time_lt_field__set_null() {
        final LocalTime time = LocalTime.now();
        mission.setPlannedDepartureTimeLt(time);
        
        mission.setPlannedDepartureTimeLt(null);

        assertNull(mission.getPlannedDepartureLt());
    }

    @Test
    public void test__planned_arrival_time_lt_field() {
        final LocalTime time = LocalTime.now();

        mission.setPlannedArrivalLt(time);

        assertEquals(time.withSecond(0).withNano(0), mission.getPlannedArrivalLt());
    }

    @Test
    public void test__planned_arrival_time_lt_field__get_null() {
        assertNull(mission.getPlannedArrivalLt());
    }

    @Test
    public void test__planned_arrival_time_lt_field__set_null() {
        final LocalTime time = LocalTime.now();
        mission.setPlannedArrivalLt(time);
        
        mission.setPlannedArrivalLt(null);

        assertNull(mission.getPlannedArrivalLt());
    }

    @Test
    public void test__dof_and_planned_times_combination() {
        final LocalDate today = JavaTime.todayUtc();
        final LocalTime plannedDeparture = LocalTime.now();
        final LocalTime plannedArrival = plannedDeparture.plusMinutes((long) (10 + 100*Math.random()));

        mission.setDateOfFlight(today);
        mission.setPlannedDepartureTimeLt(plannedDeparture);
        mission.setPlannedArrivalLt(plannedArrival);

        assertEquals(today, mission.getDateOfFlight());
        assertEquals(plannedDeparture.withSecond(0).withNano(0), mission.getPlannedDepartureLt());
        assertEquals(plannedArrival.withSecond(0).withNano(0), mission.getPlannedArrivalLt());
    }

    @Test
    public void test__planned_times_all_combinations() {
        final LocalDate today = JavaTime.todayUtc();
        mission.setDateOfFlight(today);

        for (int time1 = 0; time1 < 1440; time1++) {
            final int plannedDepartureTime = Time.fromLdLt(today, LocalTime.ofSecondOfDay(time1 * 60));
            mission.setPlannedDepartureWorldTime(plannedDepartureTime);
            for (int time2 = time1; time2 < 1440; time2++) {
                final int plannedArrivalTime = Time.fromLdLt(today, LocalTime.ofSecondOfDay(time2 * 60));
                mission.setPlannedArrivalWorldTime(plannedArrivalTime);

                assertEquals(plannedDepartureTime, mission.getPlannedDepartureWorldTime(), "planned departure check for " + time1 + "/" + time2);
                assertEquals(plannedArrivalTime, mission.getPlannedArrivalWorldTime(), "planned arrival check for " + time1 + "/" + time2);
            }
        }
    }

    @Test
    public void test__all_fields_combination() {
        final LocalDate today = JavaTime.todayUtc();
        final LocalTime plannedDeparture = LocalTime.now();
        final LocalTime plannedArrival = plannedDeparture.plusMinutes((long) (10 + 100*Math.random()));

        mission.setDateOfFlight(today);
        mission.setPlannedDepartureTimeLt(plannedDeparture);
        mission.setPlannedArrivalLt(plannedArrival);

        assertEquals(today, mission.getDateOfFlight());
        assertEquals(plannedDeparture.withSecond(0).withNano(0), mission.getPlannedDepartureLt());
        assertEquals(plannedArrival.withSecond(0).withNano(0), mission.getPlannedArrivalLt());
        assertNull(mission.getActualDepartureLt());
        assertNull(mission.getActualTakeoffLt());
        assertNull(mission.getActualLandingLt());
        assertNull(mission.getActualArrivalLt());

        final LocalTime actualDeparture = plannedDeparture.plusMinutes(2);
        mission.setActualDepartureLt(actualDeparture);

        assertEquals(today, mission.getDateOfFlight());
        assertEquals(plannedDeparture.withSecond(0).withNano(0), mission.getPlannedDepartureLt());
        assertEquals(plannedArrival.withSecond(0).withNano(0), mission.getPlannedArrivalLt());
        assertEquals(actualDeparture.withSecond(0).withNano(0), mission.getActualDepartureLt());
        assertNull(mission.getActualTakeoffLt());
        assertNull(mission.getActualLandingLt());
        assertNull(mission.getActualArrivalLt());

        final LocalTime actualTakeoff = plannedDeparture.plusMinutes(2);
        mission.setActualTakeoffLt(actualTakeoff);

        assertEquals(today, mission.getDateOfFlight());
        assertEquals(plannedDeparture.withSecond(0).withNano(0), mission.getPlannedDepartureLt());
        assertEquals(plannedArrival.withSecond(0).withNano(0), mission.getPlannedArrivalLt());
        assertEquals(actualDeparture.withSecond(0).withNano(0), mission.getActualDepartureLt());
        assertEquals(actualTakeoff.withSecond(0).withNano(0), mission.getActualTakeoffLt());
        assertNull(mission.getActualLandingLt());
        assertNull(mission.getActualArrivalLt());

        final LocalTime actualLanding = plannedArrival.minusMinutes(10);
        mission.setActualLandingLt(actualLanding);

        assertEquals(today, mission.getDateOfFlight());
        assertEquals(plannedDeparture.withSecond(0).withNano(0), mission.getPlannedDepartureLt());
        assertEquals(plannedArrival.withSecond(0).withNano(0), mission.getPlannedArrivalLt());
        assertEquals(actualDeparture.withSecond(0).withNano(0), mission.getActualDepartureLt());
        assertEquals(actualTakeoff.withSecond(0).withNano(0), mission.getActualTakeoffLt());
        assertEquals(actualLanding.withSecond(0).withNano(0), mission.getActualLandingLt());
        assertNull(mission.getActualArrivalLt());

        final LocalTime actualArrival = plannedArrival.minusMinutes(3);
        mission.setActualArrivalLt(actualArrival);

        assertEquals(today, mission.getDateOfFlight());
        assertEquals(plannedDeparture.withSecond(0).withNano(0), mission.getPlannedDepartureLt());
        assertEquals(plannedArrival.withSecond(0).withNano(0), mission.getPlannedArrivalLt());
        assertEquals(actualDeparture.withSecond(0).withNano(0), mission.getActualDepartureLt());
        assertEquals(actualTakeoff.withSecond(0).withNano(0), mission.getActualTakeoffLt());
        assertEquals(actualLanding.withSecond(0).withNano(0), mission.getActualLandingLt());
        assertEquals(actualArrival.withSecond(0).withNano(0), mission.getActualArrivalLt());
    }

    @Test
    public void test__convert_to_lt__when_all_null() {
        mission.setPlannedDepartureWorldTime(0);
        mission.setPlannedArrivalWorldTime(0);
        mission.setActualDepartureWorldTime(0);
        mission.setActualTakeoffWorldTime(0);
        mission.setActualLandingWorldTime(0);
        mission.setActualArrivalWorldTime(0);

        assertNull(mission.getDateOfFlight());
        assertNull(mission.getPlannedDepartureLt());
        assertNull(mission.getPlannedArrivalLt());
        assertNull(mission.getActualDepartureLt());
        assertNull(mission.getActualTakeoffLt());
        assertNull(mission.getActualLandingLt());
        assertNull(mission.getActualArrivalLt());
    }

    @Test
    public void test__convert_to_lt__when_partially_set() {
        final int now = Time.fromLdt(JavaTime.nowUtc());
        final int plannedDepartureTime = now + 500;
        final int plannedArrivalTime = plannedDepartureTime + 12345;
        final int actualDepartureTime = now + 510;

        mission.setPlannedDepartureWorldTime(plannedDepartureTime);
        mission.setPlannedArrivalWorldTime(plannedArrivalTime);
        mission.setActualDepartureWorldTime(actualDepartureTime);
        mission.setActualTakeoffWorldTime(0);
        mission.setActualLandingWorldTime(0);
        mission.setActualArrivalWorldTime(0);

        assertEquals(Time.toLdt(plannedDepartureTime).toLocalDate(), mission.getDateOfFlight());
        assertEquals(Time.toLtOrNull(plannedDepartureTime).withSecond(0), mission.getPlannedDepartureLt());
        assertEquals(Time.toLtOrNull(plannedArrivalTime).withSecond(0), mission.getPlannedArrivalLt());
        assertEquals(Time.toLtOrNull(actualDepartureTime).withSecond(0), mission.getActualDepartureLt());
        assertNull(mission.getActualTakeoffLt());
        assertNull(mission.getActualLandingLt());
        assertNull(mission.getActualArrivalLt());
    }

    @Test
    public void test__convert_to_lt__when_all_set() {
        final int now = Time.fromLdt(JavaTime.nowUtc());
        final int plannedDepartureTime = now + 500;
        final int plannedArrivalTime = plannedDepartureTime + 12345;
        final int actualDepartureTime = now + 510;
        final int actualTakeoffTime = actualDepartureTime + 130;
        final int actualLandingTime = actualTakeoffTime + 10203;
        final int actualArrivalTime = actualLandingTime + 170;

        mission.setPlannedDepartureWorldTime(plannedDepartureTime);
        mission.setPlannedArrivalWorldTime(plannedArrivalTime);
        mission.setActualDepartureWorldTime(actualDepartureTime);
        mission.setActualTakeoffWorldTime(actualTakeoffTime);
        mission.setActualLandingWorldTime(actualLandingTime);
        mission.setActualArrivalWorldTime(actualArrivalTime);

        assertEquals(Time.toLdt(plannedDepartureTime).toLocalDate(), mission.getDateOfFlight());
        assertEquals(Time.toLtOrNull(plannedDepartureTime).withSecond(0), mission.getPlannedDepartureLt());
        assertEquals(Time.toLtOrNull(plannedArrivalTime).withSecond(0), mission.getPlannedArrivalLt());
        assertEquals(Time.toLtOrNull(actualDepartureTime).withSecond(0), mission.getActualDepartureLt());
        assertEquals(Time.toLtOrNull(actualTakeoffTime).withSecond(0), mission.getActualTakeoffLt());
        assertEquals(Time.toLtOrNull(actualLandingTime).withSecond(0), mission.getActualLandingLt());
        assertEquals(Time.toLtOrNull(actualArrivalTime).withSecond(0), mission.getActualArrivalLt());
    }

    @Test
    public void test__time_is_lt__set_via_both_ways() {
        final int now = Time.fromLdt(JavaTime.nowUtc());
        final int plannedDepartureTime = now + 500;
        final int plannedArrivalTime = plannedDepartureTime + 12345;
        final int actualDepartureTime = now + 510;
        final int actualTakeoffTime = actualDepartureTime + 130;
        final int actualLandingTime = actualTakeoffTime + 10203;
        final int actualArrivalTime = actualLandingTime + 170;

        mission.setPlannedDepartureWorldTime(plannedDepartureTime);
        mission.setPlannedArrivalWorldTime(plannedArrivalTime);
        mission.setActualDepartureWorldTime(actualDepartureTime);
        mission.setActualTakeoffWorldTime(0);
        mission.setActualLandingWorldTime(0);
        mission.setActualArrivalWorldTime(0);

        mission.setActualTakeoffWorldTime(actualTakeoffTime);
        mission.setActualLandingWorldTime(actualLandingTime);
        mission.setActualArrivalWorldTime(actualArrivalTime);

        assertEquals(Time.toLdt(plannedDepartureTime).toLocalDate(), mission.getDateOfFlight());
        assertEquals(Time.toLtOrNull(plannedDepartureTime).withSecond(0), mission.getPlannedDepartureLt());
        assertEquals(Time.toLtOrNull(plannedArrivalTime).withSecond(0), mission.getPlannedArrivalLt());
        assertEquals(Time.toLtOrNull(actualDepartureTime).withSecond(0), mission.getActualDepartureLt());
        assertEquals(Time.toLtOrNull(actualTakeoffTime).withSecond(0), mission.getActualTakeoffLt());
        assertEquals(Time.toLtOrNull(actualLandingTime).withSecond(0), mission.getActualLandingLt());
        assertEquals(Time.toLtOrNull(actualArrivalTime).withSecond(0), mission.getActualArrivalLt());
    }

}
