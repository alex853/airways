package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PilotPayParams;
import net.simforge.airways2.world.datamodel.AircraftOperators;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.AirportFacilities;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Journeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plans of BusyBirds carry the salary of the pilot: for every leg and in total.
 */
class BusyBirdsPlanSalaryTest {
    private World world;
    private Airports.Airport base;
    private Airports.Airport destination;
    private Journeys.Journey journey;
    private Aircrafts.Aircraft aircraft;

    @BeforeEach
    public void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);

        // BusyBirds is the operator #3 of the world
        world.aircraftOperators().create("AA", "AAA", "first");
        world.aircraftOperators().create("SJ", "SJT", "second");
        final AircraftOperators.AircraftOperator busyBirds = world.aircraftOperators().create("BB", "BBD", "busyBirds");
        assertEquals(3, busyBirds.getId());

        final int countryId = world.countries().create("GB", "United Kingdom").getId();
        final Cities.City fromCity = world.cities().create(countryId, "From", 51.28, -0.76, 100000);
        final Cities.City toCity = world.cities().create(countryId, "To", 52.5, 13.4, 100000);

        base = world.airports().create(51.28, -0.76, "FRM", "EGLF", "Farnborough");
        destination = world.airports().create(52.36, 13.5, "TOO", "EDDB", "Berlin");
        world.airport2city().create(base.getId(), fromCity.getId());
        world.airport2city().create(destination.getId(), toCity.getId());
        world.airportFacilities().createIfAbsent(base, busyBirds, AirportFacilities.Type.BaseAirport);

        final AircraftTypes.AircraftType type = world.aircraftTypes().create("C25C", "CNJ");
        aircraft = world.aircrafts().create(type, "BB-CJA", base);
        aircraft.setAircraftOperatorId(busyBirds.getId());

        journey = world.journeys().create(Journeys.Status.LookingForTickets, fromCity.getId(), toCity.getId(), 4, CabinLayout.Service.J);
        journey.setLocationCityId(fromCity.getId());
    }

    // A pilot with every currency and the experience level III (3% of the income): nothing is taken off, the numbers are the base ones.
    private static final PilotPayParams FULL_PAY = fullPay();

    private static PilotPayParams fullPay() {
        final PilotPayParams params = PilotPayParams.none();
        params.setTypeCurrency(true);
        params.setNightCurrency(true);
        params.setCanCarryPassengers(true);
        params.setExperienceLevel(3);
        return params;
    }

    // the plans for a start in an hour, the pauses are the default ones
    private List<BusyBirdsMissionControl.MissionPlan> plans(final Aircrafts.Aircraft forAircraft, final boolean ferryBackToBase, final PilotPayParams params) {
        return plans(forAircraft, ferryBackToBase, params, List.of());
    }

    private List<BusyBirdsMissionControl.MissionPlan> plans(final Aircrafts.Aircraft forAircraft, final boolean ferryBackToBase, final PilotPayParams params, final List<Double> pauses) {
        return world.busyBirdsMissionControl().buildPlans(journey, forAircraft, pauses, ferryBackToBase, params, world.getWorldTime() + Time.ONE_HOUR);
    }

    private int income() {
        // the list shows a rough pay, the exact income is the price which the quote sets
        return world.busyBirdsMissionControl().quote(journey);
    }

    @Test
    public void test__revenue_and_return_legs_have_their_own_salary_and_the_plan_has_the_total() {
        // the quote comes first: it sets the price of the journey, the plans are built with it
        final int income = income();
        assertTrue(income > 0);

        final List<BusyBirdsMissionControl.MissionPlan> plans =
                plans(aircraft, true, FULL_PAY);

        assertEquals(1, plans.size());
        final BusyBirdsMissionControl.MissionPlan plan = plans.get(0);
        assertEquals(BusyBirdsMissionControl.MissionPlan.Status.Success, plan.getStatus());
        assertEquals(2, plan.getLegs().size());

        final BusyBirdsMissionControl.Leg revenue = plan.getLegs().get(0);
        final BusyBirdsMissionControl.Leg back = plan.getLegs().get(1);
        assertEquals(BusyBirdsMissionControl.Leg.Type.Revenue, revenue.getType());
        assertEquals(BusyBirdsMissionControl.Leg.Type.Reposition, back.getType());

        final int revenueFlightPay = (int) Math.round(200 * revenue.getPaidDuration().toSeconds() / 3600.0);
        assertTrue(revenueFlightPay > 0);
        assertEquals(revenueFlightPay, revenue.getSalary().flightPay());
        assertEquals((int) Math.round(income * 0.03), revenue.getSalary().incomeShare());

        assertEquals((int) Math.round(200 * back.getPaidDuration().toSeconds() / 3600.0), back.getSalary().flightPay());
        assertEquals(0, back.getSalary().incomeShare());

        assertEquals(revenue.getSalary().flightPay() + back.getSalary().flightPay(), plan.getTotalSalary().flightPay());
        assertEquals(revenue.getSalary().incomeShare(), plan.getTotalSalary().incomeShare());
        assertEquals(plan.getTotalSalary().flightPay() + plan.getTotalSalary().incomeShare(), plan.getTotalSalary().total());
    }

    @Test
    public void test__plan_without_the_return_leg_has_only_the_revenue_leg_salary() {
        final BusyBirdsMissionControl.MissionPlan plan =
                plans(aircraft, false, FULL_PAY).get(0);

        assertEquals(1, plan.getLegs().size());
        assertEquals(plan.getLegs().get(0).getSalary(), plan.getTotalSalary());
    }

    @Test
    public void test__the_paid_time_is_in_whole_minutes_as_the_planned_times_of_a_flight_mission_are() {
        // 94 minutes 21 seconds of flying: the mission keeps 94 minutes, the pay is for 94 + 20 minutes of the taxi
        assertEquals(java.time.Duration.ofMinutes(114), BusyBirdsMissionControl.paidDuration(94 * 60 + 21));
        assertEquals(java.time.Duration.ofMinutes(114), BusyBirdsMissionControl.paidDuration(94 * 60));
        assertEquals(java.time.Duration.ofMinutes(20), BusyBirdsMissionControl.paidDuration(59));
    }

    @Test
    public void test__the_quote_of_a_leg_and_the_contract_of_its_booked_flight_give_the_same_flight_pay() {
        final BusyBirdsMissionControl.MissionPlan plan = plans(aircraft, true, FULL_PAY).get(0);
        final net.simforge.airways2.world.datamodel.Persons.Person person = world.persons().create(
                net.simforge.airways2.world.datamodel.Persons.Type.Pilot, net.simforge.airways2.world.datamodel.Persons.State.Idle, 1,
                net.simforge.airways2.world.datamodel.Persons.LocationType.None, 0, 0, "AK", 0);

        for (final BusyBirdsMissionControl.Leg leg : plan.getLegs()) {
            final net.simforge.airways2.world.datamodel.FlightMissions.Mission mission = FlightMissionHelper.scheduleDispatchedMission(
                    world, aircraft, leg.getFromAirport(), leg.getToAirport(), leg.getPlannedDepTime());
            final net.simforge.airways2.world.datamodel.PilotContracts.Contract contract =
                    world.busyBirdsMissionControl().createContract(mission, person.getId(), FULL_PAY);

            assertEquals(leg.getSalary().flightPay(), net.simforge.airways2.world.computations.PilotPay.flightPay(contract), leg.getFromAirport().getIcao() + "-" + leg.getToAirport().getIcao());
        }
    }

    @Test
    public void test__the_first_leg_departs_at_the_given_time_and_the_pause_before_the_second_leg_is_counted_from_the_arrival_of_the_first_one() {
        final int start = world.getWorldTime() + 5 * Time.ONE_HOUR;
        final BusyBirdsMissionControl.MissionPlan plan = world.busyBirdsMissionControl().buildPlans(journey, aircraft, List.of(3.0), true, FULL_PAY, start).get(0);

        assertEquals(Time.alignTo5mins(start), plan.getLegs().get(0).getPlannedDepTime());
        final int second = plan.getLegs().get(1).getPlannedDepTime() - plan.getLegs().get(0).getPlannedArrTime();
        assertTrue(second >= 3 * Time.ONE_HOUR && second < 3 * Time.ONE_HOUR + 5 * Time.ONE_MINUTE, "pause " + second);
    }

    @Test
    public void test__a_leg_without_a_pause_gets_the_default_one_half_an_hour() {
        final BusyBirdsMissionControl.MissionPlan plan = plans(aircraft, true, FULL_PAY).get(0);

        final int second = plan.getLegs().get(1).getPlannedDepTime() - plan.getLegs().get(0).getPlannedArrTime();
        assertTrue(second >= 30 * Time.ONE_MINUTE && second < 35 * Time.ONE_MINUTE, "pause " + second);
    }

    @Test
    public void test__the_times_of_a_leg_are_the_times_of_its_flight_mission_so_the_pause_is_the_gap_of_the_roster() {
        final BusyBirdsMissionControl.MissionPlan plan = plans(aircraft, true, FULL_PAY).get(0);

        for (final BusyBirdsMissionControl.Leg leg : plan.getLegs()) {
            final net.simforge.airways2.world.datamodel.FlightMissions.Mission mission = FlightMissionHelper.scheduleDispatchedMission(
                    world, aircraft, leg.getFromAirport(), leg.getToAirport(), leg.getPlannedDepTime());

            assertEquals(mission.getPlannedDepartureWorldTime(), leg.getPlannedDepTime());
            assertEquals(mission.getPlannedArrivalWorldTime(), leg.getPlannedArrTime(), leg.getFromAirport().getIcao() + "-" + leg.getToAirport().getIcao());
        }
    }

    @Test
    public void test__repositioning_to_the_departure_airport_is_paid_by_the_flight_only() {
        // the aircraft is somewhere else, it has to come to the departure airport first
        final Airports.Airport elsewhere = world.airports().create(48.7, 2.4, "ELS", "LFPO", "Orly");
        aircraft.setLocationAirportId(elsewhere.getId());

        final BusyBirdsMissionControl.MissionPlan plan =
                plans(aircraft, true, FULL_PAY).get(0);

        assertEquals(3, plan.getLegs().size());
        final BusyBirdsMissionControl.Leg first = plan.getLegs().get(0);
        assertEquals(BusyBirdsMissionControl.Leg.Type.Reposition, first.getType());
        assertEquals(0, first.getSalary().incomeShare());
        assertTrue(first.getSalary().flightPay() > 0);
        assertEquals(plan.getLegs().stream().mapToInt(l -> l.getSalary().flightPay()).sum(), plan.getTotalSalary().flightPay());
    }

    @Test
    public void test__a_more_expensive_type_pays_more_for_the_same_duration() {
        final AircraftTypes.AircraftType g700 = world.aircraftTypes().create("GA7C", "GL7");
        final Aircrafts.Aircraft glf = world.aircrafts().create(g700, "BB-GLA", base);
        glf.setAircraftOperatorId(aircraft.getAircraftOperatorId());

        final BusyBirdsMissionControl.Leg small = plans(aircraft, false, FULL_PAY).get(0).getLegs().get(0);
        final BusyBirdsMissionControl.Leg big = plans(glf, false, FULL_PAY).get(0).getLegs().get(0);

        assertTrue(big.getSalary().flightPay() > 0);
        // the rate is 500 against 200, the Gulfstream also flies faster, so the duration is shorter
        assertEquals(500, BusyBirdsPilotPay.baseHourlyRate("GA7C"));
        assertEquals(Math.round(500 * big.getPaidDuration().toSeconds() / 3600.0), big.getSalary().flightPay());
        assertEquals(Math.round(200 * small.getPaidDuration().toSeconds() / 3600.0), small.getSalary().flightPay());
    }

    @Test
    public void test__the_quote_has_what_the_pilot_gets_with_the_deductions_of_the_currency() {
        final int income = income();
        // the pilot has no currency at all and the level 0: a quarter of the flight pay, 0.5% of the income with a quarter off
        final PilotPayParams nothing = PilotPayParams.none();

        final BusyBirdsMissionControl.Leg full = plans(aircraft, false, FULL_PAY).get(0).getLegs().get(0);
        final BusyBirdsMissionControl.Leg poor = plans(aircraft, false, nothing).get(0).getLegs().get(0);

        final int baseFlightPay = BusyBirdsPilotPay.baseFlightPay("C25C", (int) full.getPaidDuration().toSeconds());
        assertEquals(baseFlightPay, full.getSalary().flightPay());
        assertEquals((int) Math.round(baseFlightPay * 0.25), poor.getSalary().flightPay());
        assertEquals((int) Math.round(income * 0.03), full.getSalary().incomeShare());
        assertEquals((int) Math.round(income * 0.005 * 0.75), poor.getSalary().incomeShare());
        assertTrue(poor.getSalary().total() < full.getSalary().total());
    }

    @Test
    public void test__a_leg_knows_what_its_pay_is_made_of() {
        final int income = income();
        final BusyBirdsMissionControl.Leg leg = plans(aircraft, false, FULL_PAY).get(0).getLegs().get(0);

        assertEquals(200, leg.getHourlyRate());
        assertEquals(income, leg.getBaseIncome());
        assertEquals(FULL_PAY.toByte(), leg.getPayParams().toByte());
    }

    @Test
    public void test__a_reposition_leg_has_no_base_income() {
        income();
        final BusyBirdsMissionControl.Leg back = plans(aircraft, true, FULL_PAY).get(0).getLegs().get(1);

        assertEquals(BusyBirdsMissionControl.Leg.Type.Reposition, back.getType());
        assertEquals(0, back.getBaseIncome());
        assertEquals(0, back.getSalary().incomeShare());
    }

}
