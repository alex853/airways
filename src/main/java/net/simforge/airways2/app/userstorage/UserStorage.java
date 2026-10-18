package net.simforge.airways2.app.userstorage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Temporary users storage based on property files.
 * <p>
 * The folder './users/' contains files like user-1.cfg, user-2.cfg, where the number in the file name is
 * the user id (starts from 1). Supported properties:
 * <pre>
 * token=abcdef1234567890
 * last.location.airport=WSSS
 * person.id=1
 * vatsim.cid=1234567
 * </pre>
 * All the files are loaded into memory on the first access.
 */
public class UserStorage {
    private static final Logger log = LoggerFactory.getLogger(UserStorage.class);

    private static final Path ROOT = Paths.get("./users/");
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("user-(\\d+)\\.cfg");
    private static final String PROP_TOKEN = "token";
    private static final String PROP_LAST_LOCATION_AIRPORT = "last.location.airport";
    private static final String PROP_PERSON_ID = "person.id";
    private static final String PROP_VATSIM_CID = "vatsim.cid";

    private static UserStorage instance;

    public static synchronized UserStorage getInstance() {
        if (instance == null) {
            instance = new UserStorage(ROOT);
        }
        return instance;
    }

    private final Path root;
    private final Map<Integer, User> usersById = new HashMap<>();
    private final Map<String, User> usersByToken = new HashMap<>();

    UserStorage(final Path root) {
        this.root = root;
        load();
    }

    public synchronized Optional<User> findByToken(final String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(usersByToken.get(token));
    }

    public synchronized Optional<User> findById(final int userId) {
        return Optional.ofNullable(usersById.get(userId));
    }

    public synchronized void updateLastLocationAirport(final int userId, final String airportIcao) {
        final User user = usersById.get(userId);
        if (user == null) {
            log.warn("User storage - user #{} not found, last location airport {} is not saved", userId, airportIcao);
            return;
        }

        if (airportIcao.equals(user.getLastLocationAirport().orElse(null))) {
            return;
        }

        user.setLastLocationAirport(airportIcao);

        try {
            save(user);
            log.info("User storage - user #{} last location airport is {}", userId, airportIcao);
        } catch (final IOException e) {
            log.error("User storage - unable to save user #{}", userId, e);
        }
    }

    private void load() {
        if (!Files.isDirectory(root)) {
            log.warn("User storage - folder {} does not exist, no users loaded", root.toAbsolutePath());
            return;
        }

        try (Stream<Path> files = Files.list(root)) {
            files.forEach(this::loadFile);
        } catch (final IOException e) {
            log.error("User storage - unable to list folder {}", root.toAbsolutePath(), e);
        }

        log.info("User storage - loaded {} user(s) from {}", usersById.size(), root.toAbsolutePath());
    }

    private void loadFile(final Path file) {
        final Matcher matcher = FILE_NAME_PATTERN.matcher(file.getFileName().toString());
        if (!matcher.matches()) {
            return;
        }

        try {
            final int userId = Integer.parseInt(matcher.group(1));
            final Properties props = readProperties(file);

            final String token = props.getProperty(PROP_TOKEN);
            if (token == null || token.isBlank()) {
                log.warn("User storage - {} has no token, skipped", file.getFileName());
                return;
            }
            if (userId < 1) {
                log.warn("User storage - {} has incorrect user id, skipped", file.getFileName());
                return;
            }
            if (usersByToken.containsKey(token)) {
                log.warn("User storage - {} has token which is already used by another user, skipped", file.getFileName());
                return;
            }
            if (token.length() < 10 || token.length() > 20) {
                log.warn("User storage - {} token length is out of expected range 10-20", file.getFileName());
            }

            final User user = new User(userId, token, props.getProperty(PROP_LAST_LOCATION_AIRPORT),
                    readInt(props, PROP_PERSON_ID, file), readInt(props, PROP_VATSIM_CID, file));
            usersById.put(userId, user);
            usersByToken.put(token, user);
        } catch (final Exception e) {
            log.error("User storage - unable to load {}", file, e);
        }
    }

    private void save(final User user) throws IOException {
        final Path file = root.resolve("user-" + user.getId() + ".cfg");

        // other properties of the file are preserved
        final Properties props = Files.exists(file) ? readProperties(file) : new Properties();
        props.setProperty(PROP_TOKEN, user.getToken());
        user.getLastLocationAirport().ifPresent(icao -> props.setProperty(PROP_LAST_LOCATION_AIRPORT, icao));
        if (user.getPersonId() > 0) {
            props.setProperty(PROP_PERSON_ID, String.valueOf(user.getPersonId()));
        }
        if (user.getVatsimCid() > 0) {
            props.setProperty(PROP_VATSIM_CID, String.valueOf(user.getVatsimCid()));
        }

        final Path tmpFile = root.resolve("user-" + user.getId() + ".cfg.tmp");
        try (Writer writer = Files.newBufferedWriter(tmpFile, StandardCharsets.UTF_8)) {
            props.store(writer, null);
        }
        Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING);
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
            log.warn("User storage - {} has incorrect {}={}, ignored", file.getFileName(), name, value);
            return 0;
        }
    }

    private static Properties readProperties(final Path file) throws IOException {
        final Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return props;
    }
}
