package net.simforge.airways2.app.pblc;

import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * The log of the states of G7 (docs/g7-state-log.md). No authentication: the client sends the body as text/plain to avoid the CORS preflight, the body is JSON anyway.
 */
@RestController
@RequestMapping("/g7")
public class G7StateLogController {
    // next to the folder of the world data
    private final G7StateLog stateLog = new G7StateLog(Paths.get("./g7/"));

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

    private static ResponseEntity<Void> answer(final G7StateLog.Result result) {
        return ResponseEntity.status(result.status())
                .header("Access-Control-Allow-Origin", "*")
                .build();
    }
}
