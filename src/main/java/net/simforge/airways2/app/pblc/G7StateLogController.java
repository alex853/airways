package net.simforge.airways2.app.pblc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * The log of the states of G7 (docs/g7-state-log.md): the writing (POST) and the reading (GET). No authentication and no tokens: the client sends the body as
 * text/plain to avoid the CORS preflight, the body is JSON anyway; the reading is limited to one request an hour from an address.
 */
@RestController
@RequestMapping("/g7")
public class G7StateLogController {
    private static final Logger log = LoggerFactory.getLogger(G7StateLogController.class);

    private final G7StateLog stateLog;
    private final G7ReadLimiter readLimiter = new G7ReadLimiter();

    // next to the folder of the world data
    public G7StateLogController() {
        this(Paths.get("./g7/"));
    }

    // for the tests
    G7StateLogController(final Path folder) {
        this.stateLog = new G7StateLog(folder);
    }

    @PostMapping("/state-log")
    public ResponseEntity<Void> stateLog(final HttpServletRequest request) {
        if (request.getContentLengthLong() > G7StateLog.MAX_BODY_BYTES) {
            return answer(G7StateLog.Result.TooLarge);
        }
        final byte[] body;
        try {
            // one byte more than allowed is enough to see that the body is too big
            body = request.getInputStream().readNBytes(G7StateLog.MAX_BODY_BYTES + 1);
        } catch (final IOException e) {
            return answer(G7StateLog.Result.BadRequest);
        }
        return answer(stateLog.accept(body, request.getRemoteAddr(), Instant.now()));
    }

    /**
     * The lines of the log of one day as they are written (JSON lines, text/plain), nothing is added; no lines is 200 and an empty body. Parameters: date
     * (yyyy-MM-dd, yesterday, today; by default today, UTC as the files are), from and to (ISO times, the time of the receiving), event (prefixes with a comma),
     * session, limit (2000 by default, 20000 at most), tail=1 (the last lines instead of the first ones). 400 for a wrong parameter, 429 when it is the second
     * request in an hour from the address. There is no CORS header here.
     */
    @GetMapping("/state-log/read")
    public ResponseEntity<String> read(@RequestParam(name = "date", required = false) final String date,
                                       @RequestParam(name = "from", required = false) final String from,
                                       @RequestParam(name = "to", required = false) final String to,
                                       @RequestParam(name = "event", required = false) final String event,
                                       @RequestParam(name = "session", required = false) final String session,
                                       @RequestParam(name = "limit", required = false) final String limit,
                                       @RequestParam(name = "tail", required = false) final String tail,
                                       final HttpServletRequest request) {
        final Instant now = Instant.now();

        // the parameters are checked first: a request with a mistake is refused and is not counted
        final G7StateLog.ReadQuery query;
        try {
            query = G7StateLog.ReadQuery.parse(date, from, to, event, session, limit, tail, LocalDate.ofInstant(now, ZoneOffset.UTC));
        } catch (final IllegalArgumentException e) {
            return text(400, e.getMessage() + "\n", null);
        }

        final long wait = readLimiter.acquire(request.getRemoteAddr(), now);
        if (wait > 0) {
            return text(429, "One request an hour, try again in " + wait + " seconds\n", wait);
        }

        try {
            final StringBuilder body = new StringBuilder();
            for (final String line : stateLog.read(query)) {
                body.append(line).append('\n');
            }
            return text(200, body.toString(), null);
        } catch (final IOException e) {
            log.error("G7 state log - unable to read the log", e);
            return text(500, "", null);
        }
    }

    private static ResponseEntity<String> text(final int status, final String body, final Long retryAfterSeconds) {
        final ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).header("Content-Type", "text/plain;charset=UTF-8");
        if (retryAfterSeconds != null) {
            builder.header("Retry-After", String.valueOf(retryAfterSeconds));
        }
        return builder.body(body);
    }

    private static ResponseEntity<Void> answer(final G7StateLog.Result result) {
        return ResponseEntity.status(result.status())
                .header("Access-Control-Allow-Origin", "*")
                .build();
    }
}
