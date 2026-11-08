package net.simforge.airways2.app.pblc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * The log of the states which the G7 tool sends (docs/g7-state-log.md). It computes nothing and keeps nothing in memory: every event of a request is one line of
 * JSON in the file of the day (state-log-yyyy-MM-dd.jsonl in the folder, the day is the day of the receiving, UTC).
 */
public class G7StateLog {
    private static final Logger log = LoggerFactory.getLogger(G7StateLog.class);

    public static final int MAX_BODY_BYTES = 64 * 1024;
    public static final int MAX_EVENTS = 200;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter RECEIVED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    /**
     * What the endpoint answers: 204 the lines are written, 400 the body is not JSON or has no array of events, 413 the body is too big, 500 the file is not written.
     */
    public enum Result {
        Written(204), BadRequest(400), TooLarge(413), Failed(500);

        private final int status;

        Result(final int status) {
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    public static final int DEFAULT_READ_LIMIT = 2000;
    public static final int MAX_READ_LIMIT = 20000;
    // the date of the file comes from the request: only this form is accepted, so nothing but a name of a file of the day can be built from it
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final Path folder;
    private final Object fileLock = new Object();

    public G7StateLog(final Path folder) {
        this.folder = folder;
    }

    public Result accept(final byte[] body, final String ip, final Instant received) {
        if (body.length > MAX_BODY_BYTES) {
            return Result.TooLarge;
        }

        final JsonNode root;
        try {
            // the body is JSON whatever the Content-Type is
            root = MAPPER.readTree(new String(body, StandardCharsets.UTF_8));
        } catch (final IOException e) {
            return Result.BadRequest;
        }
        if (root == null || !root.isObject() || !root.path("events").isArray()) {
            return Result.BadRequest;
        }

        final StringBuilder lines = new StringBuilder();
        final String receivedText = RECEIVED_FORMAT.format(received);

        // a loss of the client is seen in the log as a line of its own
        final long dropped = root.path("dropped").asLong(0);
        if (dropped > 0) {
            final ObjectNode line = header(receivedText, ip, root);
            line.put("event", "client_dropped");
            line.putObject("data").put("dropped", dropped);
            lines.append(line).append('\n');
        }

        int count = 0;
        for (final JsonNode event : root.get("events")) {
            if (count == MAX_EVENTS) {
                break; // the rest is thrown away
            }
            if (!event.isObject()) {
                continue;
            }
            count++;
            final ObjectNode line = header(receivedText, ip, root);
            for (final String name : new String[]{"t", "sim", "event", "data"}) {
                if (event.has(name)) {
                    line.set(name, event.get(name));
                }
            }
            lines.append(line).append('\n');
        }

        if (lines.length() == 0) {
            return Result.Written;
        }

        final Path file = folder.resolve("state-log-" + DAY_FORMAT.format(received) + ".jsonl");
        try {
            synchronized (fileLock) {
                Files.createDirectories(folder);
                Files.writeString(file, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (final IOException e) {
            log.error("G7 state log - unable to write {}", file, e);
            return Result.Failed;
        }
        return Result.Written;
    }

    /**
     * What to read from the log of one day: the lines as they were written, in the order of the file.
     *
     * @param date          the day of the file (UTC, the same day as in the name of the file)
     * @param from          the lines received not before it, null if there is no such limit
     * @param to            the lines received not after it, null if there is no such limit
     * @param eventPrefixes the lines whose event starts with one of them, empty for all
     * @param session       the lines of one session, null for all
     * @param limit         the number of lines, 1 to MAX_READ_LIMIT
     * @param tail          the last lines instead of the first ones
     */
    public record ReadQuery(LocalDate date, Instant from, Instant to, List<String> eventPrefixes, String session, int limit, boolean tail) {

        /**
         * The query of the parameters of the request. Everything is checked here, an unusable parameter is IllegalArgumentException (a bad request). The date is
         * yyyy-MM-dd, or 'yesterday', or 'today', by default today (UTC, as the files are); from and to are ISO times (with Z or an offset); the limit is clamped to the
         * maximum; tail is 1 or true.
         */
        public static ReadQuery parse(final String date, final String from, final String to, final String event, final String session,
                                      final String limit, final String tail, final LocalDate today) {
            return new ReadQuery(parseDate(date, today), parseTime("from", from), parseTime("to", to), parsePrefixes(event),
                    session == null || session.isEmpty() ? null : session, parseLimit(limit), "1".equals(tail) || "true".equalsIgnoreCase(tail));
        }

        private static LocalDate parseDate(final String date, final LocalDate today) {
            if (date == null || date.isEmpty() || "today".equals(date)) {
                return today;
            }
            if ("yesterday".equals(date)) {
                return today.minusDays(1);
            }
            if (!DATE_PATTERN.matcher(date).matches()) {
                throw new IllegalArgumentException("date is yyyy-MM-dd, today or yesterday");
            }
            try {
                return LocalDate.parse(date);
            } catch (final DateTimeParseException e) {
                throw new IllegalArgumentException("date is not a date");
            }
        }

        private static Instant parseTime(final String name, final String value) {
            if (value == null || value.isEmpty()) {
                return null;
            }
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (final DateTimeParseException e) {
                throw new IllegalArgumentException(name + " is an ISO time, like 2026-11-08T12:00:00Z");
            }
        }

        private static List<String> parsePrefixes(final String event) {
            final List<String> result = new ArrayList<>();
            if (event != null) {
                for (final String part : event.split(",")) {
                    if (!part.isBlank()) {
                        result.add(part.trim());
                    }
                }
            }
            return result;
        }

        private static int parseLimit(final String limit) {
            if (limit == null || limit.isEmpty()) {
                return DEFAULT_READ_LIMIT;
            }
            final long value;
            try {
                value = Long.parseLong(limit.trim());
            } catch (final NumberFormatException e) {
                throw new IllegalArgumentException("limit is a number from 1");
            }
            if (value < 1) {
                throw new IllegalArgumentException("limit is a number from 1");
            }
            return (int) Math.min(value, MAX_READ_LIMIT);
        }
    }

    /**
     * The lines of the log of one day which fit the query, as they are written in the file (without the line breaks). Nothing if the file of the day does not exist.
     * Only the file of the day of the query is read, the name of it is built from the checked date.
     */
    public List<String> read(final ReadQuery query) throws IOException {
        final Path file = folder.resolve("state-log-" + query.date() + ".jsonl");
        if (!Files.isRegularFile(file)) {
            return List.of();
        }

        final boolean needsParsing = query.from() != null || query.to() != null || !query.eventPrefixes().isEmpty() || query.session() != null;
        final ArrayDeque<String> found = new ArrayDeque<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || (needsParsing && !fits(line, query))) {
                    continue;
                }
                found.addLast(line);
                if (found.size() > query.limit()) {
                    if (!query.tail()) {
                        found.removeLast();
                        break; // the first lines are enough
                    }
                    found.removeFirst();
                }
            }
        }
        return new ArrayList<>(found);
    }

    private static boolean fits(final String line, final ReadQuery query) {
        final JsonNode node;
        try {
            node = MAPPER.readTree(line);
        } catch (final IOException e) {
            return false;
        }
        if (node == null || !node.isObject()) {
            return false;
        }
        if (query.session() != null && !query.session().equals(node.path("session").asText(null))) {
            return false;
        }
        if (!query.eventPrefixes().isEmpty()) {
            final String event = node.path("event").asText(null);
            if (event == null || query.eventPrefixes().stream().noneMatch(event::startsWith)) {
                return false;
            }
        }
        if (query.from() != null || query.to() != null) {
            final Instant received;
            try {
                received = Instant.parse(node.path("received").asText(""));
            } catch (final DateTimeParseException e) {
                return false;
            }
            return (query.from() == null || !received.isBefore(query.from())) && (query.to() == null || !received.isAfter(query.to()));
        }
        return true;
    }

    // the fields of the line which are the same for all the events of the request
    private static ObjectNode header(final String received, final String ip, final JsonNode root) {
        final ObjectNode line = MAPPER.createObjectNode();
        line.put("received", received);
        line.put("ip", ip);
        for (final String name : new String[]{"app", "source", "session"}) {
            if (root.has(name)) {
                line.set(name, root.get(name));
            }
        }
        return line;
    }
}
