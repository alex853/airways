package net.simforge.airways2.app.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.simforge.commons.io.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

public class FlightStats {
    private static final Logger log = LoggerFactory.getLogger(FlightStats.class);
    public static final File statsRoot = new File("./vatsim-tracker/stats/");

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();;
    private static LocalDate date;
    private static final Map<String, Integer> data = new TreeMap<>();

    static {
        load();
    }

    public static synchronized void event(final String event) {
        validateDateAndData();
        data.compute(event, (key, value) -> value != null ? value + 1 : 1);

        Integer dispatch = data.get("dispatchNewAndStart");
        Integer blocksOff = data.get("blocksOff");
        Integer takeoff = data.get("takeoff");
        Integer landing = data.get("landing");
        Integer finish = data.get("blocksOnAndFinish");

        if (dispatch != null && finish != null) {
            data.put("dispatchToFinishPercent", (int) Math.round((float)finish / (float)Math.max(1, dispatch) * 100.0));
        }
        if (dispatch != null && landing != null) {
            data.put("dispatchToLandingPercent", (int) Math.round((float)landing / (float)Math.max(1, dispatch) * 100.0));
        }
        if (dispatch != null && takeoff != null) {
            data.put("dispatchToTakeoffPercent", (int) Math.round((float)takeoff / (float)Math.max(1, dispatch) * 100.0));
        }
        if (dispatch != null && blocksOff != null) {
            data.put("dispatchToBlocksOffPercent", (int) Math.round((float)blocksOff / (float)Math.max(1, dispatch) * 100.0));
        }
    }

    public static Map<String, Integer> getStats() {
        return new TreeMap<>(data);
    }

    private static void validateDateAndData() {
        final LocalDate today = LocalDate.now();
        if (today.equals(date)) {
            return;
        }

        if (date != null) {
            save();
        }

        date = today;
        data.clear();
    }

    private static void load() {
        final LocalDate today = LocalDate.now();
        final File file = getFile(today);
        if (!file.exists()) {
            date = null;
            data.clear();
            return;
        }

        final String json;
        try {
            json = IOHelper.loadFile(file);
        } catch (IOException e) {
            log.error("unable to load flight stats data", e);
            return;
        }
        Type type = new TypeToken<Map<String, Integer>>(){}.getType();
        final Map<String, Integer> loadedData = gson.fromJson(json, type);

        date = today;
        data.clear();
        data.putAll(loadedData);
    }

    public static void save() {
        if (date == null) {
            return;
        }

        final File file = getFile(date);
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        final String json = gson.toJson(data);
        try {
            IOHelper.saveFile(file, json);
        } catch (IOException e) {
            log.error("unable to save flight stats data", e);
        }
    }

    private static File getFile(final LocalDate date) {
        return new File(statsRoot, date + ".json");
    }
}
