package net.simforge.airways2.app.pblc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class G7StateLogTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-11-07T12:34:56.789Z");

    private static byte[] bytes(final String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static List<JsonNode> lines(final Path folder, final String day) throws IOException {
        final Path file = folder.resolve("state-log-" + day + ".jsonl");
        final List<JsonNode> result = new ArrayList<>();
        for (final String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            result.add(MAPPER.readTree(line));
        }
        return result;
    }

    private static long files(final Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return 0;
        }
        try (Stream<Path> list = Files.list(folder)) {
            return list.count();
        }
    }

    private static String events(final int count) {
        final StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            sb.append(i > 0 ? "," : "").append("{\"t\":").append(i).append(",\"sim\":1.5,\"event\":\"e").append(i).append("\",\"data\":{}}");
        }
        return sb.append("]").toString();
    }

    @Test
    void anEventIsALineWithTheFieldsOfTheRequest(@TempDir final Path dir) throws IOException {
        final G7StateLog stateLog = new G7StateLog(dir.resolve("g7"));

        final G7StateLog.Result result = stateLog.accept(bytes("{\"app\":\"G7\",\"source\":\"DU2\",\"session\":\"k3x9ab-lq2f81\",\"dropped\":0,\"events\":["
                + "{\"t\":1762500000123,\"sim\":41234.5,\"event\":\"fuel_start\",\"data\":{\"rawLeft\":\"1500.5\",\"gameState\":1,\"onGround\":true}}]}"), "1.2.3.4", NOW);

        assertEquals(G7StateLog.Result.Written, result);
        assertEquals(204, result.status());
        final List<JsonNode> lines = lines(dir.resolve("g7"), "2026-11-07");
        assertEquals(1, lines.size());
        final JsonNode line = lines.get(0);
        assertEquals("2026-11-07T12:34:56.789Z", line.get("received").asText());
        assertEquals("1.2.3.4", line.get("ip").asText());
        assertEquals("G7", line.get("app").asText());
        assertEquals("DU2", line.get("source").asText());
        assertEquals("k3x9ab-lq2f81", line.get("session").asText());
        assertEquals(1762500000123L, line.get("t").asLong());
        assertEquals(41234.5, line.get("sim").asDouble());
        assertEquals("fuel_start", line.get("event").asText());
        assertEquals("1500.5", line.get("data").get("rawLeft").asText());
        assertEquals(1, line.get("data").get("gameState").asInt());
        assertTrue(line.get("data").get("onGround").asBoolean());
    }

    @Test
    void theKeysOfTheLineAreInTheOrderOfTheDescription(@TempDir final Path dir) throws IOException {
        final G7StateLog stateLog = new G7StateLog(dir);

        stateLog.accept(bytes("{\"app\":\"G7\",\"source\":\"DU2\",\"session\":\"s\",\"events\":[{\"data\":{},\"event\":\"x\",\"sim\":1,\"t\":2}]}"), "1.2.3.4", NOW);

        final String line = Files.readAllLines(dir.resolve("state-log-2026-11-07.jsonl")).get(0);
        assertTrue(line.startsWith("{\"received\":\"2026-11-07T12:34:56.789Z\",\"ip\":\"1.2.3.4\",\"app\":\"G7\",\"source\":\"DU2\",\"session\":\"s\",\"t\":2,\"sim\":1,\"event\":\"x\",\"data\":{}}"), line);
    }

    @Test
    void everyEventIsALineAndTheRequestsAreAppended(@TempDir final Path dir) throws IOException {
        final G7StateLog stateLog = new G7StateLog(dir);

        stateLog.accept(bytes("{\"events\":" + events(3) + "}"), "1.2.3.4", NOW);
        stateLog.accept(bytes("{\"events\":" + events(2) + "}"), "1.2.3.4", NOW.plusSeconds(5));

        assertEquals(5, lines(dir, "2026-11-07").size());
    }

    @Test
    void theFileIsOfTheDayOfTheReceiving(@TempDir final Path dir) throws IOException {
        final G7StateLog stateLog = new G7StateLog(dir);

        stateLog.accept(bytes("{\"events\":" + events(1) + "}"), "1.2.3.4", Instant.parse("2026-11-07T23:59:59.999Z"));
        stateLog.accept(bytes("{\"events\":" + events(1) + "}"), "1.2.3.4", Instant.parse("2026-11-08T00:00:00.000Z"));

        assertEquals(1, lines(dir, "2026-11-07").size());
        assertEquals(1, lines(dir, "2026-11-08").size());
    }

    @Test
    void theExtraFieldsAreIgnored(@TempDir final Path dir) throws IOException {
        final G7StateLog stateLog = new G7StateLog(dir);

        stateLog.accept(bytes("{\"app\":\"G7\",\"secret\":\"x\",\"events\":[{\"t\":1,\"event\":\"a\",\"data\":{},\"extra\":5}]}"), "1.2.3.4", NOW);

        final JsonNode line = lines(dir, "2026-11-07").get(0);
        assertFalse(line.has("secret"));
        assertFalse(line.has("extra"));
    }

    @Test
    void moreThanTwoHundredEventsAreCut(@TempDir final Path dir) throws IOException {
        final G7StateLog stateLog = new G7StateLog(dir);

        stateLog.accept(bytes("{\"events\":" + events(250) + "}"), "1.2.3.4", NOW);

        final List<JsonNode> lines = lines(dir, "2026-11-07");
        assertEquals(200, lines.size());
        assertEquals("e199", lines.get(199).get("event").asText());
    }

    @Test
    void theLossOfTheClientIsALineOfItsOwnOnlyWhenItIsMoreThanZero(@TempDir final Path dir) throws IOException {
        final G7StateLog stateLog = new G7StateLog(dir);

        stateLog.accept(bytes("{\"app\":\"G7\",\"source\":\"DU2\",\"session\":\"s\",\"dropped\":7,\"events\":" + events(1) + "}"), "1.2.3.4", NOW);
        stateLog.accept(bytes("{\"app\":\"G7\",\"source\":\"DU2\",\"session\":\"s\",\"dropped\":0,\"events\":" + events(1) + "}"), "1.2.3.4", NOW);

        final List<JsonNode> lines = lines(dir, "2026-11-07");
        assertEquals(3, lines.size());
        assertEquals("client_dropped", lines.get(0).get("event").asText());
        assertEquals(7, lines.get(0).get("data").get("dropped").asInt());
        assertEquals("G7", lines.get(0).get("app").asText());
        assertEquals("e0", lines.get(1).get("event").asText());
        assertEquals("e0", lines.get(2).get("event").asText());
    }

    @Test
    void aRequestWithoutEventsWritesNothingButIsAccepted(@TempDir final Path dir) throws IOException {
        final G7StateLog stateLog = new G7StateLog(dir.resolve("g7"));

        assertEquals(G7StateLog.Result.Written, stateLog.accept(bytes("{\"events\":[]}"), "1.2.3.4", NOW));

        assertEquals(0, files(dir.resolve("g7")));
    }

    @Test
    void aBodyWhichIsNotJsonOrHasNoEventsIsABadRequest(@TempDir final Path dir) throws IOException {
        final G7StateLog stateLog = new G7StateLog(dir.resolve("g7"));

        assertEquals(400, stateLog.accept(bytes("not a json"), "1.2.3.4", NOW).status());
        assertEquals(400, stateLog.accept(bytes(""), "1.2.3.4", NOW).status());
        assertEquals(400, stateLog.accept(bytes("{\"app\":\"G7\"}"), "1.2.3.4", NOW).status());
        assertEquals(400, stateLog.accept(bytes("{\"events\":{}}"), "1.2.3.4", NOW).status());
        assertEquals(400, stateLog.accept(bytes("[1,2]"), "1.2.3.4", NOW).status());
        assertEquals(0, files(dir.resolve("g7")));
    }

    @Test
    void aBodyOverSixtyFourKilobytesIsTooLarge(@TempDir final Path dir) throws IOException {
        final G7StateLog stateLog = new G7StateLog(dir.resolve("g7"));
        final String big = "{\"events\":[{\"event\":\"a\",\"data\":{\"x\":\"" + "x".repeat(G7StateLog.MAX_BODY_BYTES) + "\"}}]}";

        assertEquals(413, stateLog.accept(bytes(big), "1.2.3.4", NOW).status());

        assertEquals(0, files(dir.resolve("g7")));
    }

    @Test
    void theFolderIsCreatedWhenItIsNotThere(@TempDir final Path dir) throws IOException {
        final G7StateLog stateLog = new G7StateLog(dir.resolve("not").resolve("yet"));

        assertEquals(204, stateLog.accept(bytes("{\"events\":" + events(1) + "}"), "1.2.3.4", NOW).status());

        assertEquals(1, lines(dir.resolve("not").resolve("yet"), "2026-11-07").size());
    }
}
