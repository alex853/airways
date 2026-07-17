package net.simforge.airways2.pilottracker;

import net.simforge.commons.io.IOHelper;
import net.simforge.commons.misc.JavaTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class SimTrackerTools {
    private static final Logger log = LoggerFactory.getLogger(SimTrackerTools.class);

    public static void savePosrep(int userId, String posrep) {
        try {
            String filenameTemplate = "./sim-tracker/posreps/{dateFolder}/user-{userId}.csv";
            String dateFolder = JavaTime.yMd.format(JavaTime.todayUtc());
            String filename = filenameTemplate
                    .replace("{dateFolder}", dateFolder)
                    .replace("{userId}", String.valueOf(userId));

            File file = new File(filename);
            boolean mkdirSuccess = file.getParentFile().mkdirs();
            if (!mkdirSuccess) {
                throw new IOException("Unable to create the folder");
            }

            String content = file.exists() ? IOHelper.loadFile(file) : "";
            content += posrep + "\n";

            IOHelper.saveFile(file, content);
        } catch (IOException e) {
            log.error("Failed to save posrep '{}' for user {}", posrep, userId, e);
        }
    }
}
