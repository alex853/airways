package net.simforge.airways2.world.processors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.commons.misc.JavaTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Once a day, at 00:00 UTC (like the rotation of the airport stats), writes the statistics of the world to the folder ./world-stats/ as a JSON file named by the date of the day
 * which has just begun: 2026-11-13.json is the state at the start of 2026-11-13. For now the file has the journeys and the passengers (the sizes of the groups) by every
 * status of a journey; more will be added. The folder is created by hand; when it does not exist, the day is lost and the log has the error. The world which was stopped at
 * midnight does not write the file for that day. See docs/world-stats.md.
 */
public class WorldStats {
    private static final Logger log = LoggerFactory.getLogger(WorldStats.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DEFAULT_FOLDER = Paths.get("./world-stats/");

    private static LocalDate lastDate = JavaTime.todayUtc();

    public static void process(final World world) {
        process(world, JavaTime.todayUtc(), DEFAULT_FOLDER);
    }

    static void process(final World world, final LocalDate today, final Path folder) {
        if (!today.isAfter(lastDate)) {
            return;
        }
        lastDate = today;

        try {
            final Path file = write(folder, today, collect(world));
            log.info("world-stats - {} is written", file);
        } catch (final Exception e) {
            log.error("world-stats - unable to write the stats of {} to {}", today, folder, e);
        }
    }

    static void resetForTest(final LocalDate date) {
        lastDate = date;
    }

    /** The statistics as they go to the file: the order of the keys is the order of the statuses. */
    static Map<String, Object> collect(final World world) {
        final Journeys.Status[] statuses = Journeys.Status.values();
        final long[] passengers = new long[statuses.length];
        final long[] journeys = new long[statuses.length];
        world.journeys().all().forEach(journey -> {
            final int index = journey.getStatus().ordinal();
            passengers[index] += journey.getGroupSize();
            journeys[index]++;
        });

        final Map<String, Object> byStatuses = new LinkedHashMap<>();
        for (int i = 0; i < statuses.length; i++) {
            final Map<String, Long> counts = new LinkedHashMap<>();
            counts.put("journeys", journeys[i]);
            counts.put("passengers", passengers[i]);
            byStatuses.put(statuses[i].name(), counts);
        }

        final Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("journeys", byStatuses);
        return stats;
    }

    /** Writes the stats to {@code <folder>/<date>.json} (the folder has to exist), a JSON for reading by a person. */
    static Path write(final Path folder, final LocalDate date, final Map<String, Object> stats) throws IOException {
        if (!Files.isDirectory(folder)) {
            throw new IOException("the folder " + folder + " does not exist");
        }
        final Path file = folder.resolve(date + ".json");
        Files.writeString(file, gson.toJson(stats) + System.lineSeparator(), StandardCharsets.UTF_8);
        return file;
    }
}
