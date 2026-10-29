package net.simforge.airways2.app.user;

import net.simforge.airways2.app.user.BusyBirdsController.DeductionDto;
import net.simforge.airways2.app.user.BusyBirdsController.PilotTermsDto;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PilotPay;
import net.simforge.airways2.world.computations.PilotPayParams;
import net.simforge.airways2.world.computations.PilotPayRates;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.processors.ShadowJetPilotPay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What the pilot gets on a type of aircraft at ShadowJet, as the page "Book Flight" shows it: the same formula as at BusyBirds, the rates of ShadowJet.
 */
class ShadowJetPilotTermsTest {
    private static final int DAY = 24 * Time.ONE_HOUR;

    private World world;
    private int now;
    private Airports.Airport airportA;
    private Airports.Airport airportB;

    @BeforeEach
    void beforeEach() {
        now = Time.START_TIME_EPOCH_SECONDS + 500 * DAY;
        world = World.create(new InMemoryStorageStrategy(), now);
        airportA = world.airports().create(37.72, -122.22, "OAK", "KOAK", "Oakland");
        airportB = world.airports().create(37.62, -122.38, "SFO", "KSFO", "San Francisco");
        world.aircraftTypes().create("A320", "320");
    }

    private PilotTermsDto terms(final String type) {
        return BusyBirdsController.buildPilotTerms(world, 1, type, ShadowJetPilotPay.baseHourlyRate(type), PilotPayRates.SHADOW_JET);
    }

    private static List<String> codes(final List<DeductionDto> deductions) {
        return deductions.stream().map(DeductionDto::getCode).toList();
    }

    @Test
    void aPilotWithoutFlightsOnTheTypeLosesTenPercentForEachMissingCriterion() {
        final PilotTermsDto terms = terms("A320");

        assertEquals("A320", terms.getAircraftType());
        assertFalse(terms.isTypeCurrent());

        // the base rate is 100, three deductions of 10% leave 70%
        assertEquals(100, terms.getHourlyRate());
        assertEquals(List.of("NoTypeCurrency", "NoNightCurrency", "CannotCarryPassengers"), codes(terms.getFlightPayDeductions()));
        assertEquals(List.of(10, 10, 10), terms.getFlightPayDeductions().stream().map(DeductionDto::getPercent).toList());
        assertEquals(70, terms.getFlightPayCoefficientPercent());
        assertEquals(70.0, terms.getEffectiveHourlyRate(), 0.0001);

        // the level 0 is 0.1% of the income, only the type currency counts: 90% is left
        assertEquals(0.1, terms.getIncomeSharePercent(), 0.0001);
        assertEquals(List.of("NoTypeCurrency"), codes(terms.getIncomeShareDeductions()));
        assertEquals(90, terms.getIncomeShareCoefficientPercent());
        assertEquals(0.09, terms.getEffectiveIncomeSharePercent(), 0.0001);
    }

    @Test
    void theBaseRateIsTheSameForEveryType() {
        assertEquals(100, ShadowJetPilotPay.baseHourlyRate("A320"));
        assertEquals(100, ShadowJetPilotPay.baseHourlyRate("B738"));
    }

    @Test
    void theRatesOfShadowJetAreNotTheRatesOfBusyBirds() {
        final PilotPayParams params = PilotPayParams.none();
        params.setManual(true);

        // everything is missing and the flight is manual: 10 + 10 + 10 + 50 is taken off
        assertEquals(20, PilotPay.flightPayCoefficientPercent(params, PilotPayRates.SHADOW_JET));
        // the same at BusyBirds hits the floor
        assertEquals(10, PilotPay.flightPayCoefficientPercent(params, PilotPayRates.BUSY_BIRDS));
        // and the default (the methods without the rates) is BusyBirds
        assertEquals(10, PilotPay.flightPayCoefficientPercent(params));
    }

    @Test
    void theBaseSharesOfTheLevels() {
        final float[] expected = {0.1f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f};
        for (int level = 0; level < expected.length; level++) {
            assertEquals(expected[level], PilotPay.baseIncomeSharePercent(level, PilotPayRates.SHADOW_JET), 0.0001f);
        }
    }

    @Test
    void theManualFlightTakesHalfOfTheFlightPayAndOfTheShare() {
        final PilotPayParams params = PilotPayParams.none();
        params.setTypeCurrency(true);
        params.setNightCurrency(true);
        params.setCanCarryPassengers(true);
        params.setExperienceLevel(3);
        params.setManual(true);

        assertEquals(50, PilotPay.flightPayCoefficientPercent(params, PilotPayRates.SHADOW_JET));
        assertEquals(50, PilotPay.incomeShareCoefficientPercent(params, PilotPayRates.SHADOW_JET));
        // 1000 base, 50% left
        assertEquals(500, PilotPay.flightPay(1000, params, PilotPayRates.SHADOW_JET));
        // level 3 is 1.5%, half of it is 0.75% of 100000
        assertEquals(750, PilotPay.incomeShare(100_000, params, PilotPayRates.SHADOW_JET));
        assertEquals(0.75, PilotPay.effectiveIncomeSharePercent(params, PilotPayRates.SHADOW_JET), 0.0001);
    }
}
