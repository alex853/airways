package net.simforge.airways2.world.computations;

import net.simforge.airways2.tools.CabinLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShadowJetPricingTest {

    @Test
    void thePriceOfAPassengerIsTheFixedPartAndThePartByTheDistance() {
        // economy: 30 + 0.12 per nautical mile; 600 nm is 102 for a passenger
        assertEquals(102, ShadowJetPricing.price(600, 1, CabinLayout.Service.Y));
    }

    @Test
    void thePriceGrowsWithThePassengers() {
        assertEquals(1020, ShadowJetPricing.price(600, 10, CabinLayout.Service.Y));
        assertEquals(2 * ShadowJetPricing.price(600, 10, CabinLayout.Service.Y), ShadowJetPricing.price(600, 20, CabinLayout.Service.Y));
    }

    @Test
    void thePriceGrowsWithTheDistance() {
        assertTrue(ShadowJetPricing.price(3000, 10, CabinLayout.Service.Y) > ShadowJetPricing.price(600, 10, CabinLayout.Service.Y));
    }

    @Test
    void theBetterTheClassTheMoreThePassengerPays() {
        final int y = ShadowJetPricing.price(1000, 1, CabinLayout.Service.Y);
        final int w = ShadowJetPricing.price(1000, 1, CabinLayout.Service.W);
        final int j = ShadowJetPricing.price(1000, 1, CabinLayout.Service.J);
        final int f = ShadowJetPricing.price(1000, 1, CabinLayout.Service.F);

        assertTrue(y < w && w < j && j < f, y + " " + w + " " + j + " " + f);
        assertEquals(1.0, ShadowJetPricing.classFactor(CabinLayout.Service.Y));
    }

    @Test
    void theFactorsOfTheClasses() {
        assertEquals(1.0, ShadowJetPricing.classFactor(CabinLayout.Service.Y));
        assertEquals(1.5, ShadowJetPricing.classFactor(CabinLayout.Service.W));
        assertEquals(4.0, ShadowJetPricing.classFactor(CabinLayout.Service.J));
        assertEquals(7.0, ShadowJetPricing.classFactor(CabinLayout.Service.F));
        // 600 nm: 102 for the economy, four times that for the business, seven times for the first
        assertEquals(408, ShadowJetPricing.price(600, 1, CabinLayout.Service.J));
        assertEquals(714, ShadowJetPricing.price(600, 1, CabinLayout.Service.F));
    }

    @Test
    void noPassengersNoPrice() {
        assertEquals(0, ShadowJetPricing.price(600, 0, CabinLayout.Service.Y));
    }

    @Test
    void negativeNumbersAreNotAllowed() {
        assertThrows(IllegalArgumentException.class, () -> ShadowJetPricing.price(-1, 1, CabinLayout.Service.Y));
        assertThrows(IllegalArgumentException.class, () -> ShadowJetPricing.price(600, -1, CabinLayout.Service.Y));
    }
}
