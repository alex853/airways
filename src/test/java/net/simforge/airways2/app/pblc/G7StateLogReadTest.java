package net.simforge.airways2.app.pblc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reading of the log of G7: the lines as they are written, the filters, the limit, the date which cannot lead out of the folder, one request an hour.
 */
class G7StateLogReadTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 8);
    private static final Instant NOW = Instant.parse("2026-11-08T12:00:00.000Z");

    private Path folder;
    private G7StateLog stateLog;

    @BeforeEach
    void beforeEach(@TempDir final Path dir) {
        folder = dir.resolve("g7");
        stateLog = new G7StateLog(folder);
    }

    private void write(final String session, final String event, final int t, final Instant received) {
        final String body = "{\"app\":\"G7\",\"source\":\"DU2\",\"session\":\"" + session + "\",\"events\":[{\"t\":" + t + ",\"sim\":1,\"event\":\"" + event + "\",\"data\":{\"n\":" + t + "}}]}";
        assertEquals(204, stateLog.accept(body.getBytes(StandardCharsets.UTF_8), "1.2.3.4", received).status());
    }

    private static G7StateLog.ReadQuery query(final String date, final String from, final String to, final String event, final String session, final String limit, final String tail) {
        return G7StateLog.ReadQuery.parse(date, from, to, event, session, limit, tail, TODAY);
    }

    private static G7StateLog.ReadQuery all() {
        return query(null, null, null, null, null, null, null);
    }

    private static int t(final String line) {
        return Integer.parseInt(line.replaceAll(".*\"n\":(\\d+).*", "$1"));
    }

    @Test
    void theLinesAreGivenAsTheyAreWritten() throws IOException {
        write("s1", "fuel_start", 1, NOW);

        final List<String> lines = stateLog.read(all());

        assertEquals(Files.readAllLines(folder.resolve("state-log-2026-11-08.jsonl")), lines);
    }

    @Test
    void theDefaultDateIsTodayAndYesterdayIsAKeyword() throws IOException {
        write("s1", "a", 1, NOW);
        write("s1", "b", 2, NOW.minusSeconds(24 * 3600));

        assertEquals(1, t(stateLog.read(all()).get(0)));
        assertEquals(2, t(stateLog.read(query("yesterday", null, null, null, null, null, null)).get(0)));
        assertEquals(1, t(stateLog.read(query("2026-11-08", null, null, null, null, null, null)).get(0)));
        assertEquals(2, t(stateLog.read(query("2026-11-07", null, null, null, null, null, null)).get(0)));
    }

    @Test
    void aDayWithoutAFileIsNoLinesAndTheFolderMayNotExist() throws IOException {
        assertTrue(stateLog.read(all()).isEmpty());
        write("s1", "a", 1, NOW);
        assertTrue(stateLog.read(query("2026-01-01", null, null, null, null, null, null)).isEmpty());
    }

    @Test
    void theEventsAreFilteredByThePrefixesWithAComma() throws IOException {
        write("s1", "fuel_start", 1, NOW);
        write("s1", "game_state", 2, NOW);
        write("s1", "aircraftstate_x", 3, NOW);
        write("s1", "extpower_on", 4, NOW);

        assertEquals(List.of(1), stateLog.read(query(null, null, null, "fuel_", null, null, null)).stream().map(G7StateLogReadTest::t).toList());
        assertEquals(List.of(1, 2), stateLog.read(query(null, null, null, "fuel_,game_state", null, null, null)).stream().map(G7StateLogReadTest::t).toList());
        assertEquals(List.of(3, 4), stateLog.read(query(null, null, null, " aircraftstate_ , extpower_ ", null, null, null)).stream().map(G7StateLogReadTest::t).toList());
        assertTrue(stateLog.read(query(null, null, null, "nothing_", null, null, null)).isEmpty());
    }

    @Test
    void theSessionIsFiltered() throws IOException {
        write("s1", "a", 1, NOW);
        write("s2", "a", 2, NOW);
        write("s1", "a", 3, NOW);

        assertEquals(List.of(1, 3), stateLog.read(query(null, null, null, null, "s1", null, null)).stream().map(G7StateLogReadTest::t).toList());
    }

    @Test
    void theTimeOfTheReceivingIsFiltered() throws IOException {
        write("s", "a", 1, Instant.parse("2026-11-08T10:00:00Z"));
        write("s", "a", 2, Instant.parse("2026-11-08T11:00:00Z"));
        write("s", "a", 3, Instant.parse("2026-11-08T12:00:00Z"));

        assertEquals(List.of(2, 3), stateLog.read(query(null, "2026-11-08T11:00:00Z", null, null, null, null, null)).stream().map(G7StateLogReadTest::t).toList());
        assertEquals(List.of(1, 2), stateLog.read(query(null, null, "2026-11-08T11:00:00Z", null, null, null, null)).stream().map(G7StateLogReadTest::t).toList());
        assertEquals(List.of(2), stateLog.read(query(null, "2026-11-08T11:00:00Z", "2026-11-08T11:30:00Z", null, null, null, null)).stream().map(G7StateLogReadTest::t).toList());
        // an offset is an ISO time too
        assertEquals(List.of(3), stateLog.read(query(null, "2026-11-08T14:00:00+02:00", null, null, null, null, null)).stream().map(G7StateLogReadTest::t).toList());
    }

    @Test
    void theLimitTakesTheFirstLinesAndTailTakesTheLastOnes() throws IOException {
        for (int i = 1; i <= 10; i++) {
            write("s", "a", i, NOW);
        }

        assertEquals(List.of(1, 2, 3), stateLog.read(query(null, null, null, null, null, "3", null)).stream().map(G7StateLogReadTest::t).toList());
        assertEquals(List.of(8, 9, 10), stateLog.read(query(null, null, null, null, null, "3", "1")).stream().map(G7StateLogReadTest::t).toList());
        assertEquals(10, stateLog.read(query(null, null, null, null, null, "100", "1")).size());
        // the limit goes together with the filters
        assertEquals(List.of(9, 10), stateLog.read(query(null, null, null, "a", null, "2", "true")).stream().map(G7StateLogReadTest::t).toList());
    }

    @Test
    void theLimitIsTwoThousandByDefaultAndTwentyThousandAtMost() {
        assertEquals(2000, all().limit());
        assertEquals(20000, query(null, null, null, null, null, "20000", null).limit());
        assertEquals(20000, query(null, null, null, null, null, "999999", null).limit());
        assertEquals(1, query(null, null, null, null, null, "1", null).limit());
    }

    @Test
    void aWrongParameterIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> query("../x", null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> query("..\\..\\x", null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> query("2026-11-08/../../x", null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> query("2026-11-08.jsonl", null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> query("2026-13-45", null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> query("26-1-8", null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> query(null, "yesterday", null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> query(null, null, "2026-11-08", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> query(null, null, null, null, null, "0", null));
        assertThrows(IllegalArgumentException.class, () -> query(null, null, null, null, null, "-5", null));
        assertThrows(IllegalArgumentException.class, () -> query(null, null, null, null, null, "many", null));
    }

    @Test
    void aDateWhichLooksLikeAPathReadsNothingOutsideTheFolder() throws IOException {
        Files.createDirectories(folder);
        Files.writeString(folder.getParent().resolve("secret.txt"), "secret\n");

        assertThrows(IllegalArgumentException.class, () -> query("../secret.txt", null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> query("../../x", null, null, null, null, null, null));
    }

    // ---- the limit of one request an hour ----

    @Test
    void theSecondRequestInAnHourIsToWaitAndTheOneAfterTheHourIsFree() {
        final G7ReadLimiter limiter = new G7ReadLimiter();

        assertEquals(0, limiter.acquire("1.1.1.1", NOW));
        assertEquals(1800, limiter.acquire("1.1.1.1", NOW.plusSeconds(1800)));
        assertEquals(1, limiter.acquire("1.1.1.1", NOW.plusSeconds(3599)));
        // the refused ones do not move the hour
        assertEquals(0, limiter.acquire("1.1.1.1", NOW.plusSeconds(3600)));
        assertEquals(3600, limiter.acquire("1.1.1.1", NOW.plusSeconds(3600)));
    }

    @Test
    void theAddressesAreIndependent() {
        final G7ReadLimiter limiter = new G7ReadLimiter();

        assertEquals(0, limiter.acquire("1.1.1.1", NOW));
        assertEquals(0, limiter.acquire("2.2.2.2", NOW));
        assertTrue(limiter.acquire("1.1.1.1", NOW.plusSeconds(10)) > 0);
    }

    // ---- the endpoint ----

    private static MockHttpServletRequest request(final String ip) {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        return request;
    }

    private ResponseEntity<String> read(final G7StateLogController controller, final String ip, final String date, final String event, final String limit, final String tail) {
        return controller.read(date, null, null, event, null, limit, tail, request(ip));
    }

    @Test
    void theEndpointAnswersWithTextAndWithoutAKeyAndWithoutCors() throws IOException {
        final G7StateLogController controller = new G7StateLogController(folder);
        final LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        stateLog.accept("{\"events\":[{\"t\":1,\"event\":\"fuel_start\",\"data\":{\"n\":1}},{\"t\":2,\"event\":\"game_state\",\"data\":{\"n\":2}}]}".getBytes(StandardCharsets.UTF_8), "1.2.3.4", Instant.now());

        final ResponseEntity<String> response = read(controller, "9.9.9.9", today.toString(), "fuel_", "10", null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("text/plain;charset=UTF-8", response.getHeaders().getFirst("Content-Type"));
        assertNull(response.getHeaders().getFirst("Access-Control-Allow-Origin"));
        assertEquals(1, response.getBody().lines().count());
        assertTrue(response.getBody().endsWith("\n"));
        assertTrue(response.getBody().contains("\"event\":\"fuel_start\""));
    }

    @Test
    void theEndpointGivesAnEmptyBodyWhenThereIsNoData() {
        final G7StateLogController controller = new G7StateLogController(folder);

        final ResponseEntity<String> response = read(controller, "9.9.9.9", "2026-01-01", null, null, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("", response.getBody());
    }

    @Test
    void theEndpointRefusesAWrongDateWithoutCountingTheRequest() {
        final G7StateLogController controller = new G7StateLogController(folder);

        assertEquals(400, read(controller, "9.9.9.9", "../x", null, null, null).getStatusCode().value());
        assertEquals(400, read(controller, "9.9.9.9", "2026-11-08/../x", null, null, null).getStatusCode().value());
        // the mistakes did not use the hour
        assertEquals(200, read(controller, "9.9.9.9", "2026-01-01", null, null, null).getStatusCode().value());
    }

    @Test
    void theSecondRequestInAnHourIsTooManyRequests() {
        final G7StateLogController controller = new G7StateLogController(folder);

        assertEquals(200, read(controller, "9.9.9.9", "2026-01-01", null, null, null).getStatusCode().value());
        final ResponseEntity<String> second = read(controller, "9.9.9.9", "2026-01-01", null, null, null);

        assertEquals(429, second.getStatusCode().value());
        assertTrue(Long.parseLong(second.getHeaders().getFirst("Retry-After")) > 0);
        assertEquals(200, read(controller, "8.8.8.8", "2026-01-01", null, null, null).getStatusCode().value());
    }

    @Test
    void theWritingStillWorksAndTheReadingSeesIt() throws IOException {
        final G7StateLogController controller = new G7StateLogController(folder);
        final MockHttpServletRequest post = request("1.2.3.4");
        post.setContent("{\"app\":\"G7\",\"events\":[{\"t\":1,\"event\":\"x\",\"data\":{}}]}".getBytes(StandardCharsets.UTF_8));

        assertEquals(204, controller.stateLog(post).getStatusCode().value());

        assertEquals(1, read(controller, "9.9.9.9", null, null, null, null).getBody().lines().count());
    }
}
