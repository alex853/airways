package net.simforge.airways2.app.userstorage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * TEMPORARY, delete it with the import in UserStorage after the first start of the storage on the production. Reads the users of the previous storage: the files
 * './users/user-N.cfg' (N is the id of the user) with the properties token, person.id, vatsim.cid. The old property last.location.airport is ignored. The files are
 * not changed, the tokens are not logged.
 */
final class LegacyUsersImport {
    private static final Logger log = LoggerFactory.getLogger(LegacyUsersImport.class);

    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("user-(\\d+)\\.cfg");

    record LegacyUser(int userId, String token, int personId, int vatsimCid) {
    }

    private LegacyUsersImport() {
    }

    // the users by the ascending id; a file without a token or with a wrong id is skipped
    static List<LegacyUser> read(final Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        final List<LegacyUser> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(root)) {
            files.forEach(file -> readFile(file, result));
        } catch (final IOException e) {
            log.error("User storage - import - unable to list folder {}", root.toAbsolutePath(), e);
        }
        result.sort(Comparator.comparingInt(LegacyUser::userId));
        return result;
    }

    private static void readFile(final Path file, final List<LegacyUser> result) {
        final Matcher matcher = FILE_NAME_PATTERN.matcher(file.getFileName().toString());
        if (!matcher.matches()) {
            return;
        }

        try {
            final int userId = Integer.parseInt(matcher.group(1));
            final Properties props = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            final String token = props.getProperty("token");
            if (token == null || token.isBlank() || userId < 1 || userId > 65535) {
                log.warn("User storage - import - {} has no token or an incorrect user id, skipped", file.getFileName());
                return;
            }
            result.add(new LegacyUser(userId, token.trim(), readInt(props, "person.id", file), readInt(props, "vatsim.cid", file)));
        } catch (final Exception e) {
            log.error("User storage - import - unable to read {}", file, e);
        }
    }

    // zero if the property is absent or is not a number
    private static int readInt(final Properties props, final String name, final Path file) {
        final String value = props.getProperty(name);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (final NumberFormatException e) {
            log.warn("User storage - import - {} has incorrect {}, ignored", file.getFileName(), name);
            return 0;
        }
    }
}
