package net.simforge.airways2.app.userstorage;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.world.DiskStorageStrategy;
import net.simforge.airways2.world.WorldStorageStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The users of the game: a small storage of its own, not a part of the world (the token is checked on every request of a user, it must not wait for the lock of the world).
 * It is saved at once after every change, with the backups of the folder like the world has (DiskStorageStrategy, './world-data/users').
 * <p>
 * The id of a user is the id of its record (it is referenced by the flights and the contexts of the sim tracker, so it never changes). The token is found by an index in memory.
 * <p>
 * The record is 64 bytes: the deletion status 1, token (PlainString, 40 chars) 41, personId 3, vatsimCid 3, reserve 16.
 */
public class UserStorage {
    private static final Logger log = LoggerFactory.getLogger(UserStorage.class);

    public static final int MIN_TOKEN_LENGTH = 10;
    public static final int MAX_TOKEN_LENGTH = 40;
    // personId and vatsimCid take 3 bytes
    public static final int MAX_NUMBER = (int) DataType.Unsigned24bit.maxValue();

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9]+");
    private static final Path LEGACY_ROOT = Paths.get("./users/");

    private static UserStorage instance;

    public static synchronized UserStorage getInstance() {
        if (instance == null) {
            instance = new UserStorage(new DiskStorageStrategy("users"), LEGACY_ROOT);
        }
        return instance;
    }

    private final Storage<UserRecord> storage = Storage.<UserRecord>builder()
            .name("users")
            .withInstantiator(UserRecord::new)
            .withIdOf(DataType.Unsigned16bit)
            // -------------------------------------------------- record deletion status    1 byte
            .withDataField(DataField.of(DataType.PlainString).length(MAX_TOKEN_LENGTH)) // token   41 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // personId                    3 bytes
            .withDataField(DataField.of(DataType.Unsigned24bit)) // vatsimCid                   3 bytes
            // ----------------------------------------------------- sum                    48 bytes
            // -------------------------------------------- reserved 16 bytes to make the record 64 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                    4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                    4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                    4 bytes
            .withDataField(DataField.of(DataType.Signed32bit)) // reserve                    4 bytes
            .build();

    private final DataField tokenField = storage.getDataField(0);
    private final DataField personIdField = storage.getDataField(1);
    private final DataField vatsimCidField = storage.getDataField(2);

    private final WorldStorageStrategy strategy;
    private final Map<String, Integer> userIdByToken = new HashMap<>();

    UserStorage(final WorldStorageStrategy strategy, final Path legacyRoot) {
        this.strategy = checkNotNull(strategy);
        load();
        if (storage.getCount() == 0) {
            importLegacyUsers(legacyRoot);
        }
    }

    public synchronized Optional<User> findByToken(final String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        final Integer userId = userIdByToken.get(token);
        return userId != null ? findById(userId) : Optional.empty();
    }

    public synchronized Optional<User> findById(final int userId) {
        return storage.byId(userId).map(UserRecord::toUser);
    }

    public synchronized List<User> all() {
        return storage.all().map(UserRecord::toUser).toList();
    }

    /**
     * Registers a new user. The token is 10-40 letters and digits and is not used by another user; personId and vatsimCid are 0 when they are not known.
     */
    public synchronized User create(final String token, final int personId, final int vatsimCid) {
        checkToken(token, null);
        checkNumber("personId", personId);
        checkNumber("vatsimCid", vatsimCid);

        final int userId = storage.addRecord();
        storage.set(userId, tokenField, token);
        storage.set(userId, personIdField, personId);
        storage.set(userId, vatsimCidField, vatsimCid);
        userIdByToken.put(token, userId);
        save();
        log.info("User storage - u/s #{} - registered, person {}, VATSIM CID {}", userId, personId, vatsimCid);
        return storage.byId(userId).orElseThrow().toUser();
    }

    public synchronized void setToken(final int userId, final String token) {
        final UserRecord record = recordOf(userId);
        checkToken(token, userId);

        userIdByToken.remove(record.getToken());
        storage.set(userId, tokenField, token);
        userIdByToken.put(token, userId);
        save();
        log.info("User storage - u/s #{} - token is changed", userId);
    }

    public synchronized void setPersonId(final int userId, final int personId) {
        recordOf(userId);
        checkNumber("personId", personId);

        storage.set(userId, personIdField, personId);
        save();
        log.info("User storage - u/s #{} - person is {}", userId, personId);
    }

    public synchronized void setVatsimCid(final int userId, final int vatsimCid) {
        recordOf(userId);
        checkNumber("vatsimCid", vatsimCid);

        storage.set(userId, vatsimCidField, vatsimCid);
        save();
        log.info("User storage - u/s #{} - VATSIM CID is {}", userId, vatsimCid);
    }

    private UserRecord recordOf(final int userId) {
        return storage.byId(userId).orElseThrow(() -> new IllegalArgumentException("user " + userId + " not found"));
    }

    private void checkToken(final String token, final Integer userId) {
        checkArgument(token != null && token.length() >= MIN_TOKEN_LENGTH && token.length() <= MAX_TOKEN_LENGTH,
                "the token is %s-%s characters", MIN_TOKEN_LENGTH, MAX_TOKEN_LENGTH);
        checkArgument(TOKEN_PATTERN.matcher(token).matches(), "the token is letters and digits only");
        final Integer owner = userIdByToken.get(token);
        checkArgument(owner == null || owner.equals(userId), "the token is used by another user");
    }

    private static void checkNumber(final String name, final int value) {
        checkArgument(value >= 0 && value <= MAX_NUMBER, "%s is from 0 to %s: %s", name, MAX_NUMBER, value);
    }

    private void load() {
        try {
            strategy.load(storage::loadIfExists);
        } catch (final IOException e) {
            throw new UncheckedIOException("unable to load the users", e);
        }
        userIdByToken.clear();
        storage.all().forEach(record -> userIdByToken.put(record.getToken(), record.id));
        log.info("User storage - loaded {} user(s)", storage.getCount());
    }

    private void save() {
        try {
            strategy.save(storage::save);
            if (strategy instanceof DiskStorageStrategy disk) {
                try {
                    disk.reduceWorldBackupCounts();
                } catch (final Exception e) {
                    log.warn("User storage - unable to reduce the backups", e);
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("unable to save the users", e);
        }
    }

    // todo ak2 TEMPORARY: the one-time import of the users from the property files './users/user-N.cfg' (the previous storage). It runs when the storage is empty.
    // The files are not touched. Delete it with LegacyUsersImport after the first start of the new storage on the production.
    private void importLegacyUsers(final Path legacyRoot) {
        final List<LegacyUsersImport.LegacyUser> legacyUsers = LegacyUsersImport.read(legacyRoot);
        if (legacyUsers.isEmpty()) {
            return;
        }

        for (final LegacyUsersImport.LegacyUser legacy : legacyUsers) {
            if (legacy.token().length() > MAX_TOKEN_LENGTH || userIdByToken.containsKey(legacy.token())) {
                log.warn("User storage - import - user {} has a token which is too long or is used by another user, skipped", legacy.userId());
                continue;
            }
            // the id of the user is kept: the records before it are taken and deleted
            int id;
            do {
                id = storage.addRecord();
                if (id < legacy.userId()) {
                    storage.deleteRecord(id);
                }
            } while (id < legacy.userId());
            if (id != legacy.userId()) {
                throw new IllegalStateException("user " + legacy.userId() + " got the record " + id);
            }
            storage.set(id, tokenField, legacy.token());
            storage.set(id, personIdField, Math.min(legacy.personId(), MAX_NUMBER));
            storage.set(id, vatsimCidField, Math.min(legacy.vatsimCid(), MAX_NUMBER));
            userIdByToken.put(legacy.token(), id);
            log.info("User storage - u/s #{} - imported from the property file", id);
        }
        save();
        log.info("User storage - imported {} user(s) from {}", storage.getCount(), legacyRoot.toAbsolutePath());
    }

    private final class UserRecord {
        private final int id;

        private UserRecord(final int id) {
            this.id = id;
        }

        String getToken() {
            return storage.getAsString(id, tokenField);
        }

        User toUser() {
            return new User(id, getToken(), storage.getAsInt(id, personIdField), storage.getAsInt(id, vatsimCidField));
        }
    }
}
