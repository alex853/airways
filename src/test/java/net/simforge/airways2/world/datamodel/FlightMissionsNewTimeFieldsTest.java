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

public class FlightMissionsNewTimeFieldsTest {

    private World world;
    private FlightMissions.Mission mission;

    @BeforeEach
    public void beforeEach() {
        final int startTime = 1700000000;
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
        mission.setPlannedDepartureTime(0);
        mission.setPlannedArrivalTime(0);
    }

    @Test
    public void test__date_of_flight_field() {
        mission.convertTimeToLT();

        final LocalDate today = JavaTime.todayUtc();

        mission.setDateOfFlight(today);

        assertEquals(today, mission.getDateOfFlight());
    }

    @Test
    public void test__date_of_flight_field__set_null() {
        mission.convertTimeToLT();

        mission.setDateOfFlight(null);

        assertNull(mission.getDateOfFlight());
    }

    @Test
    public void test__date_of_flight_field__get_null() {
        mission.convertTimeToLT();

        assertNull(mission.getDateOfFlight());
    }

    @Test
    public void test__planned_departure_time_lt_field() {
        mission.convertTimeToLT();

        final LocalTime time = LocalTime.now();

        mission.setPlannedDepartureTimeLT(time);

        assertEquals(time.withSecond(0).withNano(0), mission.getPlannedDepartureTimeLT());
    }

    @Test
    public void test__planned_departure_time_lt_field__get_null() {
        mission.convertTimeToLT();

        assertNull(mission.getPlannedDepartureTimeLT());
    }

    @Test
    public void test__planned_departure_time_lt_field__set_null() {
        mission.convertTimeToLT();

        final LocalTime time = LocalTime.now();
        mission.setPlannedDepartureTimeLT(time);
        
        mission.setPlannedDepartureTimeLT(null);

        assertNull(mission.getPlannedDepartureTimeLT());
    }

    @Test
    public void test__planned_arrival_time_lt_field() {
        mission.convertTimeToLT();

        final LocalTime time = LocalTime.now();

        mission.setPlannedArrivalTimeLT(time);

        assertEquals(time.withSecond(0).withNano(0), mission.getPlannedArrivalTimeLT());
    }

    @Test
    public void test__planned_arrival_time_lt_field__get_null() {
        mission.convertTimeToLT();

        assertNull(mission.getPlannedArrivalTimeLT());
    }

    @Test
    public void test__planned_arrival_time_lt_field__set_null() {
        mission.convertTimeToLT();

        final LocalTime time = LocalTime.now();
        mission.setPlannedArrivalTimeLT(time);
        
        mission.setPlannedArrivalTimeLT(null);

        assertNull(mission.getPlannedArrivalTimeLT());
    }

    @Test
    public void test__dof_and_planned_times_combination() {
        mission.convertTimeToLT();

        final LocalDate today = JavaTime.todayUtc();
        final LocalTime plannedDeparture = LocalTime.now();
        final LocalTime plannedArrival = plannedDeparture.plusMinutes((long) (10 + 100*Math.random()));

        mission.setDateOfFlight(today);
        mission.setPlannedDepartureTimeLT(plannedDeparture);
        mission.setPlannedArrivalTimeLT(plannedArrival);

        assertEquals(today, mission.getDateOfFlight());
        assertEquals(plannedDeparture.withSecond(0).withNano(0), mission.getPlannedDepartureTimeLT());
        assertEquals(plannedArrival.withSecond(0).withNano(0), mission.getPlannedArrivalTimeLT());
    }

    @Test
    public void test__all_fields_combination() {
        mission.convertTimeToLT();

        final LocalDate today = JavaTime.todayUtc();
        final LocalTime plannedDeparture = LocalTime.now();
        final LocalTime plannedArrival = plannedDeparture.plusMinutes((long) (10 + 100*Math.random()));

        mission.setDateOfFlight(today);
        mission.setPlannedDepartureTimeLT(plannedDeparture);
        mission.setPlannedArrivalTimeLT(plannedArrival);

        assertEquals(today, mission.getDateOfFlight());
        assertEquals(plannedDeparture.withSecond(0).withNano(0), mission.getPlannedDepartureTimeLT());
        assertEquals(plannedArrival.withSecond(0).withNano(0), mission.getPlannedArrivalTimeLT());
        assertNull(mission.getActualDepartureTimeLT());
        assertNull(mission.getActualTakeoffTimeLT());
        assertNull(mission.getActualLandingTimeLT());
        assertNull(mission.getActualArrivalTimeLT());

        final LocalTime actualDeparture = plannedDeparture.plusMinutes(2);
        mission.setActualDepartureTimeLT(actualDeparture);

        assertEquals(today, mission.getDateOfFlight());
        assertEquals(plannedDeparture.withSecond(0).withNano(0), mission.getPlannedDepartureTimeLT());
        assertEquals(plannedArrival.withSecond(0).withNano(0), mission.getPlannedArrivalTimeLT());
        assertEquals(actualDeparture.withSecond(0).withNano(0), mission.getActualDepartureTimeLT());
        assertNull(mission.getActualTakeoffTimeLT());
        assertNull(mission.getActualLandingTimeLT());
        assertNull(mission.getActualArrivalTimeLT());

        final LocalTime actualTakeoff = plannedDeparture.plusMinutes(2);
        mission.setActualTakeoffTimeLT(actualTakeoff);

        assertEquals(today, mission.getDateOfFlight());
        assertEquals(plannedDeparture.withSecond(0).withNano(0), mission.getPlannedDepartureTimeLT());
        assertEquals(plannedArrival.withSecond(0).withNano(0), mission.getPlannedArrivalTimeLT());
        assertEquals(actualDeparture.withSecond(0).withNano(0), mission.getActualDepartureTimeLT());
        assertEquals(actualTakeoff.withSecond(0).withNano(0), mission.getActualTakeoffTimeLT());
        assertNull(mission.getActualLandingTimeLT());
        assertNull(mission.getActualArrivalTimeLT());

        final LocalTime actualLanding = plannedArrival.minusMinutes(10);
        mission.setActualLandingTimeLT(actualLanding);

        assertEquals(today, mission.getDateOfFlight());
        assertEquals(plannedDeparture.withSecond(0).withNano(0), mission.getPlannedDepartureTimeLT());
        assertEquals(plannedArrival.withSecond(0).withNano(0), mission.getPlannedArrivalTimeLT());
        assertEquals(actualDeparture.withSecond(0).withNano(0), mission.getActualDepartureTimeLT());
        assertEquals(actualTakeoff.withSecond(0).withNano(0), mission.getActualTakeoffTimeLT());
        assertEquals(actualLanding.withSecond(0).withNano(0), mission.getActualLandingTimeLT());
        assertNull(mission.getActualArrivalTimeLT());

        final LocalTime actualArrival = plannedArrival.minusMinutes(3);
        mission.setActualArrivalTimeLT(actualArrival);

        assertEquals(today, mission.getDateOfFlight());
        assertEquals(plannedDeparture.withSecond(0).withNano(0), mission.getPlannedDepartureTimeLT());
        assertEquals(plannedArrival.withSecond(0).withNano(0), mission.getPlannedArrivalTimeLT());
        assertEquals(actualDeparture.withSecond(0).withNano(0), mission.getActualDepartureTimeLT());
        assertEquals(actualTakeoff.withSecond(0).withNano(0), mission.getActualTakeoffTimeLT());
        assertEquals(actualLanding.withSecond(0).withNano(0), mission.getActualLandingTimeLT());
        assertEquals(actualArrival.withSecond(0).withNano(0), mission.getActualArrivalTimeLT());
    }

    @Test
    public void test__convert_to_lt__when_all_null() {
        mission.setPlannedDepartureTime(0);
        mission.setPlannedArrivalTime(0);
        mission.setActualDepartureTime(0);
        mission.setActualTakeoffTime(0);
        mission.setActualLandingTime(0);
        mission.setActualArrivalTime(0);

        mission.convertTimeToLT();

        assertNull(mission.getDateOfFlight());
        assertNull(mission.getPlannedDepartureTimeLT());
        assertNull(mission.getPlannedArrivalTimeLT());
        assertNull(mission.getActualDepartureTimeLT());
        assertNull(mission.getActualTakeoffTimeLT());
        assertNull(mission.getActualLandingTimeLT());
        assertNull(mission.getActualArrivalTimeLT());
    }

    @Test
    public void test__convert_to_lt__when_partially_set() {
        final int now = Time.fromLdt(JavaTime.nowUtc());
        final int plannedDepartureTime = now + 500;
        final int plannedArrivalTime = plannedDepartureTime + 12345;
        final int actualDepartureTime = now + 510;

        mission.setPlannedDepartureTime(plannedDepartureTime);
        mission.setPlannedArrivalTime(plannedArrivalTime);
        mission.setActualDepartureTime(actualDepartureTime);
        mission.setActualTakeoffTime(0);
        mission.setActualLandingTime(0);
        mission.setActualArrivalTime(0);

        mission.convertTimeToLT();

        assertEquals(Time.toLdt(plannedDepartureTime).toLocalDate(), mission.getDateOfFlight());
        assertEquals(Time.toLtOrNull(plannedDepartureTime).withSecond(0), mission.getPlannedDepartureTimeLT());
        assertEquals(Time.toLtOrNull(plannedArrivalTime).withSecond(0), mission.getPlannedArrivalTimeLT());
        assertEquals(Time.toLtOrNull(actualDepartureTime).withSecond(0), mission.getActualDepartureTimeLT());
        assertNull(mission.getActualTakeoffTimeLT());
        assertNull(mission.getActualLandingTimeLT());
        assertNull(mission.getActualArrivalTimeLT());
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

        mission.setPlannedDepartureTime(plannedDepartureTime);
        mission.setPlannedArrivalTime(plannedArrivalTime);
        mission.setActualDepartureTime(actualDepartureTime);
        mission.setActualTakeoffTime(actualTakeoffTime);
        mission.setActualLandingTime(actualLandingTime);
        mission.setActualArrivalTime(actualArrivalTime);

        mission.convertTimeToLT();

        assertEquals(Time.toLdt(plannedDepartureTime).toLocalDate(), mission.getDateOfFlight());
        assertEquals(Time.toLtOrNull(plannedDepartureTime).withSecond(0), mission.getPlannedDepartureTimeLT());
        assertEquals(Time.toLtOrNull(plannedArrivalTime).withSecond(0), mission.getPlannedArrivalTimeLT());
        assertEquals(Time.toLtOrNull(actualDepartureTime).withSecond(0), mission.getActualDepartureTimeLT());
        assertEquals(Time.toLtOrNull(actualTakeoffTime).withSecond(0), mission.getActualTakeoffTimeLT());
        assertEquals(Time.toLtOrNull(actualLandingTime).withSecond(0), mission.getActualLandingTimeLT());
        assertEquals(Time.toLtOrNull(actualArrivalTime).withSecond(0), mission.getActualArrivalTimeLT());
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

        mission.setPlannedDepartureTime(plannedDepartureTime);
        mission.setPlannedArrivalTime(plannedArrivalTime);
        mission.setActualDepartureTime(actualDepartureTime);
        mission.setActualTakeoffTime(0);
        mission.setActualLandingTime(0);
        mission.setActualArrivalTime(0);

        mission.convertTimeToLT();

        mission.setActualTakeoffTime(actualTakeoffTime);
        mission.setActualLandingTime(actualLandingTime);
        mission.setActualArrivalTime(actualArrivalTime);

        assertEquals(Time.toLdt(plannedDepartureTime).toLocalDate(), mission.getDateOfFlight());
        assertEquals(Time.toLtOrNull(plannedDepartureTime).withSecond(0), mission.getPlannedDepartureTimeLT());
        assertEquals(Time.toLtOrNull(plannedArrivalTime).withSecond(0), mission.getPlannedArrivalTimeLT());
        assertEquals(Time.toLtOrNull(actualDepartureTime).withSecond(0), mission.getActualDepartureTimeLT());
        assertEquals(Time.toLtOrNull(actualTakeoffTime).withSecond(0), mission.getActualTakeoffTimeLT());
        assertEquals(Time.toLtOrNull(actualLandingTime).withSecond(0), mission.getActualLandingTimeLT());
        assertEquals(Time.toLtOrNull(actualArrivalTime).withSecond(0), mission.getActualArrivalTimeLT());
    }

}
