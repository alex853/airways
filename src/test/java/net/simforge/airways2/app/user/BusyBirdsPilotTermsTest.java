package net.simforge.airways2.app.user;

import net.simforge.airways2.app.user.BusyBirdsController.DeductionDto;
import net.simforge.airways2.app.user.BusyBirdsController.PilotTermsDto;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.PilotLogbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the pilot gets on an aircraft, as the booking page shows it: the qualifications on the type, the hourly rate and the income share with the deductions.
 */
class BusyBirdsPilotTermsTest {
    private static final int DAY = 24 * Time.ONE_HOUR;

    private World world;
    private int now;
    private Airports.Airport airportA;
    private Airports.Airport airportB;
    private Aircrafts.Aircraft gulfstream;
    private Aircrafts.Aircraft citation;

    @BeforeEach
    void beforeEach() {
        now = Time.START_TIME_EPOCH_SECONDS + 500 * DAY;
        world = World.create(new InMemoryStorageStrategy(), now);
        airportA = world.airports().create(37.72, -122.22, "OAK", "KOAK", "Oakland");
        airportB = world.airports().create(37.62, -122.38, "SFO", "KSFO", "San Francisco");
        gulfstream = world.aircrafts().create(world.aircraftTypes().create("GA7C", "GL7"), "BB-GLA", airportA);
        citation = world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", airportA);
    }

    private PilotLogbook.Entry flight(final Aircrafts.Aircraft aircraft, final int endedSecondsAgo, final int minutes) {
        final int on = now - endedSecondsAgo;
        return world.pilotLogbook().create(1, aircraft.getId(), airportA.getId(), airportB.getId(), on - minutes * 60, on);
    }

    private static List<String> codes(final List<DeductionDto> deductions) {
        return deductions.stream().map(DeductionDto::getCode).toList();
    }

    @Test
    void aPilotWithoutFlightsOnTheTypeHasEveryDeduction() {
        final PilotTermsDto terms = BusyBirdsController.buildPilotTerms(world, 1, gulfstream);

        assertEquals("GA7C", terms.getAircraftType());
        assertEquals(0, terms.getExperienceLevel());
        assertFalse(terms.isTypeCurrent());
        assertFalse(terms.isNightFlying());
        assertFalse(terms.isCanCarryPassengers());

        // the rate of GA7C is 500, three deductions of 25% leave 25%
        assertEquals(500, terms.getHourlyRate());
        assertEquals(List.of("NoTypeCurrency", "NoNightCurrency", "CannotCarryPassengers"), codes(terms.getFlightPayDeductions()));
        assertEquals(25, terms.getFlightPayCoefficientPercent());
        assertEquals(125.0, terms.getEffectiveHourlyRate(), 0.0001);

        // level 0 is 0.5% of the income, only the type currency counts: 75% is left
        assertEquals(0.5, terms.getIncomeSharePercent(), 0.0001);
        assertEquals(List.of("NoTypeCurrency"), codes(terms.getIncomeShareDeductions()));
        assertEquals(75, terms.getIncomeShareCoefficientPercent());
        assertEquals(0.375, terms.getEffectiveIncomeSharePercent(), 0.0001);
    }

    @Test
    void aPilotWithEveryQualificationHasNoDeductions() {
        // 12 flights of an hour in the last days, two of them have a night takeoff and a night landing
        for (int i = 0; i < 12; i++) {
            final PilotLogbook.Entry e = flight(gulfstream, (i + 1) * DAY / 2, 60);
            if (i < 2) {
                e.setNightTakeoff(true);
                e.setNightLanding(true);
            }
        }

        final PilotTermsDto terms = BusyBirdsController.buildPilotTerms(world, 1, gulfstream);

        assertTrue(terms.isTypeCurrent());
        assertTrue(terms.isNightFlying());
        assertTrue(terms.isCanCarryPassengers());
        assertEquals(List.of(), terms.getFlightPayDeductions());
        assertEquals(100, terms.getFlightPayCoefficientPercent());
        assertEquals(500.0, terms.getEffectiveHourlyRate(), 0.0001);
        assertEquals(List.of(), terms.getIncomeShareDeductions());
        assertEquals(0.5, terms.getEffectiveIncomeSharePercent(), 0.0001);
    }

    @Test
    void theTermsFollowTheChosenAircraft() {
        for (int i = 0; i < 12; i++) {
            flight(gulfstream, (i + 1) * DAY / 2, 60);
        }

        final PilotTermsDto onGulfstream = BusyBirdsController.buildPilotTerms(world, 1, gulfstream);
        final PilotTermsDto onCitation = BusyBirdsController.buildPilotTerms(world, 1, citation);

        // the currency is of the type: nothing on the citation, and its rate is 200
        assertTrue(onGulfstream.isTypeCurrent());
        assertFalse(onCitation.isTypeCurrent());
        assertEquals("C25C", onCitation.getAircraftType());
        assertEquals(200, onCitation.getHourlyRate());
        assertEquals(50.0, onCitation.getEffectiveHourlyRate(), 0.0001);
    }

    @Test
    void theCoefficientsHaveTheFloorOfTenPercent() {
        // the manual flight is not chosen on the page yet, the deductions of the PilotPay are checked directly
        final net.simforge.airways2.world.computations.PilotPayParams params = net.simforge.airways2.world.computations.PilotPayParams.none();
        params.setManual(true);

        assertEquals(10, net.simforge.airways2.world.computations.PilotPay.flightPayCoefficientPercent(params));
        assertEquals(List.of("NoTypeCurrency", "NoNightCurrency", "CannotCarryPassengers", "Manual"),
                net.simforge.airways2.world.computations.PilotPay.flightPayDeductions(params).stream().map(Enum::name).toList());
    }
}
