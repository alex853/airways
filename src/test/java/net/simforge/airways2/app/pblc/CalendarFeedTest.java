package net.simforge.airways2.app.pblc;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CalendarFeedTest {
    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

    @Test
    void emptyCalendar() {
        final String text = CalendarFeed.build("Airways flights", List.of(), NOW);
        assertTrue(text.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"));
        assertTrue(text.endsWith("END:VCALENDAR\r\n"));
        assertFalse(text.contains("BEGIN:VEVENT"));
    }

    @Test
    void oneEvent() {
        final String text = CalendarFeed.build("Airways flights", List.of(new CalendarFeed.Event(
                "flight-5@airways.simforge.net",
                Instant.parse("2026-11-10T08:30:00Z"), Instant.parse("2026-11-10T10:05:00Z"),
                "UUEE → ULLI", "Aircraft RA-1 (A320)\nhttps://x/y", "UUEE Sheremetyevo, Moscow")), NOW);
        assertTrue(text.contains("BEGIN:VEVENT\r\nUID:flight-5@airways.simforge.net\r\nDTSTAMP:20261109T100000Z\r\nDTSTART:20261110T083000Z\r\nDTEND:20261110T100500Z\r\n"));
        assertTrue(text.contains("SUMMARY:UUEE → ULLI\r\n"));
        // the line break and the comma are escaped
        assertTrue(text.contains("DESCRIPTION:Aircraft RA-1 (A320)\\nhttps://x/y\r\n"));
        assertTrue(text.contains("LOCATION:UUEE Sheremetyevo\\, Moscow\r\n"));
        assertTrue(text.contains("END:VEVENT\r\nEND:VCALENDAR\r\n"));
    }

    @Test
    void longLinesAreFoldedAtSeventyFiveOctetsWithoutCuttingCharacters() {
        final String longName = "→".repeat(60);
        final String text = CalendarFeed.build(longName, List.of(), NOW);
        for (final String line : text.split("\r\n")) {
            assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75, line);
        }
        // unfolded it is the same text
        assertTrue(text.replace("\r\n ", "").contains("X-WR-CALNAME:" + longName + "\r\n"));
    }
}
