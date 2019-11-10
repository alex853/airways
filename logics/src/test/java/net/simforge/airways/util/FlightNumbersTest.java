/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.util;

import net.simforge.airways.model.Airline;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FlightNumbersTest {
    private Airline airline;
    
    @Before
    public void beforeTest() {
        airline = new Airline();
        airline.setIata("AB");
        airline.setIcao("ABC");
        airline.setName("ABC Airlines");
    }
    
    @Test
    public void test_makeFlightNumber_AB1() {
        assertEquals("AB001", FlightNumbers.makeFlightNumber("AB", 1));
    }

    @Test
    public void test_makeFlightNumber_AB12() {
        assertEquals("AB012", FlightNumbers.makeFlightNumber("AB", 12));
    }

    @Test
    public void test_makeFlightNumber_AB123() {
        assertEquals("AB123", FlightNumbers.makeFlightNumber("AB", 123));
    }

    @Test
    public void test_makeFlightNumber_AB1234() {
        assertEquals("AB1234", FlightNumbers.makeFlightNumber("AB", 1234));
    }

    @Test
    public void test_increaseFlightNumber_AB011() {
        assertEquals("AB011", FlightNumbers.increaseFlightNumber("AB010"));
    }

    @Test
    public void test_increaseFlightNumber_AB999() {
        assertEquals("AB1000", FlightNumbers.increaseFlightNumber("AB999"));
    }

    @Test
    public void test_make_callsign_ABC1() {
        assertEquals("ABC1", FlightNumbers.makeCallsign(airline, "AB001"));
    }

    @Test
    public void test_makeFlightNumber_ABC12() {
        assertEquals("ABC12", FlightNumbers.makeCallsign(airline, "AB012"));
    }

    @Test
    public void test_makeFlightNumber_ABC123() {
        assertEquals("ABC123", FlightNumbers.makeCallsign(airline, "AB123"));
    }

    @Test
    public void test_makeFlightNumber_ABC1234() {
        assertEquals("ABC1234", FlightNumbers.makeCallsign(airline, "AB1234"));
    }

}
