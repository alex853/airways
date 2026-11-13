package net.simforge.airways2.world.processors;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Journeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The daily JSON with the statistics of the world.
 */
class WorldStatsTest {
    private World world;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        world.journeys().create(Journeys.Status.LookingForTickets, 1, 2, 4, CabinLayout.Service.Y);
        world.journeys().create(Journeys.Status.LookingForTickets, 1, 2, 3, CabinLayout.Service.Y);
        world.journeys().create(Journeys.Status.OnBoard, 1, 2, 2, CabinLayout.Service.Y);
    }

    private static JsonObject read(final Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    @Test
    void theFileHasTheJourneysAndThePassengersOfEveryStatusInTheOrderOfTheStatuses(@TempDir final Path folder) throws IOException {
        final Path file = WorldStats.write(folder, LocalDate.of(2026, 11, 13), WorldStats.collect(world));

        assertEquals("2026-11-13.json", file.getFileName().toString());
        final JsonObject journeys = read(file).getAsJsonObject("journeys");
        assertEquals(2, journeys.getAsJsonObject("LookingForTickets").get("journeys").getAsInt());
        assertEquals(7, journeys.getAsJsonObject("LookingForTickets").get("passengers").getAsInt());
        assertEquals(1, journeys.getAsJsonObject("OnBoard").get("journeys").getAsInt());
        assertEquals(2, journeys.getAsJsonObject("OnBoard").get("passengers").getAsInt());
        // a status without journeys is in the file with zeros
        assertEquals(0, journeys.getAsJsonObject("Finished").get("journeys").getAsInt());
        assertEquals(Journeys.Status.values().length, journeys.size());
        assertEquals("LookingForTickets", journeys.keySet().iterator().next());
    }

    @Test
    void theFileIsPrettyPrintedForReading(@TempDir final Path folder) throws IOException {
        final Path file = WorldStats.write(folder, LocalDate.of(2026, 11, 13), WorldStats.collect(world));

        final String text = Files.readString(file);
        assertTrue(text.startsWith("{\n  \"journeys\": {\n    \"LookingForTickets\": {\n      \"journeys\": 2,\n      \"passengers\": 7\n    },"), text);
    }

    @Test
    void theFileIsWrittenOnceWhenTheDayChanges(@TempDir final Path folder) throws IOException {
        WorldStats.resetForTest(LocalDate.of(2026, 11, 12));

        // the same day: nothing
        WorldStats.process(world, LocalDate.of(2026, 11, 12), folder);
        assertFalse(Files.exists(folder.resolve("2026-11-12.json")));

        // midnight: the file of the new day
        WorldStats.process(world, LocalDate.of(2026, 11, 13), folder);
        assertTrue(Files.exists(folder.resolve("2026-11-13.json")));

        // the same day again: not rewritten
        Files.writeString(folder.resolve("2026-11-13.json"), "marker");
        WorldStats.process(world, LocalDate.of(2026, 11, 13), folder);
        assertEquals("marker", Files.readString(folder.resolve("2026-11-13.json")));
    }

    @Test
    void aMissingFolderIsAnErrorInTheLogAndTheDayIsLostWithoutAnException(@TempDir final Path folder) {
        WorldStats.resetForTest(LocalDate.of(2026, 11, 12));
        final Path missing = folder.resolve("not-created");

        WorldStats.process(world, LocalDate.of(2026, 11, 13), missing);

        assertFalse(Files.exists(missing), "the folder is created by hand, not by the code");
        // the next day goes on
        WorldStats.process(world, LocalDate.of(2026, 11, 14), folder);
        assertTrue(Files.exists(folder.resolve("2026-11-14.json")));
    }
}
