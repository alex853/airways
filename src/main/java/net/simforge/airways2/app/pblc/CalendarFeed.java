package net.simforge.airways2.app.pblc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The text of an iCalendar file (RFC 5545) for the calendar link of a pilot: one event for every flight. The lines end with CRLF and are folded at 75 octets.
 */
final class CalendarFeed {
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    /**
     * @param uid the same for the same flight forever, so a calendar updates the event and does not add one more
     */
    record Event(String uid, Instant start, Instant end, String summary, String description, String location) {
    }

    private CalendarFeed() {
    }

    static String build(final String name, final List<Event> events, final Instant now) {
        final StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//Simforge//Airways//EN");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH");
        line(out, "X-WR-CALNAME:" + escape(name));
        // calendars read the file by themselves, these are hints of how often
        line(out, "REFRESH-INTERVAL;VALUE=DURATION:PT1H");
        line(out, "X-PUBLISHED-TTL:PT1H");
        for (final Event event : events) {
            line(out, "BEGIN:VEVENT");
            line(out, "UID:" + event.uid());
            line(out, "DTSTAMP:" + UTC.format(now));
            line(out, "DTSTART:" + UTC.format(event.start()));
            line(out, "DTEND:" + UTC.format(event.end()));
            line(out, "SUMMARY:" + escape(event.summary()));
            if (event.description() != null && !event.description().isEmpty()) {
                line(out, "DESCRIPTION:" + escape(event.description()));
            }
            if (event.location() != null && !event.location().isEmpty()) {
                line(out, "LOCATION:" + escape(event.location()));
            }
            line(out, "END:VEVENT");
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }

    static String escape(final String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n");
    }

    // a line is folded by a CRLF and a space; a character is never cut in the middle
    private static void line(final StringBuilder out, final String text) {
        int octets = 0;
        final StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            final int codePoint = text.codePointAt(i);
            final String ch = new String(Character.toChars(codePoint));
            final int size = ch.getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > 75) {
                out.append(current).append("\r\n");
                current.setLength(0);
                current.append(' ');
                octets = 1;
            }
            current.append(ch);
            octets += size;
            i += ch.length();
        }
        out.append(current).append("\r\n");
    }
}
