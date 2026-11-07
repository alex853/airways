package net.simforge.airways2.app.pblc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
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
