package net.simforge.airways2.app.userstorage;

import net.simforge.airways2.world.WorldStorageStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserStorageTest {
    private static final String TOKEN = "abc123def456ghi789";

    // the storage is written to one folder, without the backups; the folder is not created by the strategy, as DiskStorageStrategy does not create it (the operation does)
    private static WorldStorageStrategy folder(final Path dir) {
        return new WorldStorageStrategy() {
            @Override
            public void load(final WorldIOOperation loadingOps) throws IOException {
                loadingOps.perform(dir);
            }

            @Override
            public void save(final WorldIOOperation savingOps) throws IOException {
                savingOps.perform(dir);
            }
        };
    }

    private static void writeLegacyUser(final Path dir, final int userId, final String content) throws IOException {
        Files.writeString(dir.resolve("user-" + userId + ".cfg"), content);
    }

    @Test
    public void test__a_registered_user_is_found_by_id_and_by_token(@TempDir final Path dir) {
        final UserStorage storage = new UserStorage(folder(dir.resolve("data")), dir.resolve("legacy"));

        final User created = storage.create(TOKEN, 5, 913904);

        assertEquals(1, created.getId());
        assertEquals(TOKEN, created.getToken());
        assertEquals(5, created.getPersonId());
        assertEquals(913904, created.getVatsimCid());
        assertEquals(1, storage.findByToken(TOKEN).orElseThrow().getId());
        assertEquals(TOKEN, storage.findById(1).orElseThrow().getToken());
        assertTrue(storage.findByToken("unknowntoken1").isEmpty());
        assertTrue(storage.findByToken(null).isEmpty());
        assertTrue(storage.findById(2).isEmpty());
    }

    @Test
    public void test__users_survive_a_restart(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve("data"));
        final UserStorage first = new UserStorage(folder(dir.resolve("data")), dir.resolve("legacy"));
        first.create(TOKEN, 5, 913904);
        first.create("second1234567890zz", 0, 0);

        final UserStorage second = new UserStorage(folder(dir.resolve("data")), dir.resolve("legacy"));

        assertEquals(2, second.all().size());
        assertEquals(5, second.findByToken(TOKEN).orElseThrow().getPersonId());
        assertEquals(2, second.findByToken("second1234567890zz").orElseThrow().getId());
    }

    @Test
    public void test__a_token_of_forty_characters_fits(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve("data"));
        final String longToken = "a1".repeat(20);
        final UserStorage first = new UserStorage(folder(dir.resolve("data")), dir.resolve("legacy"));
        first.create(longToken, 1, 1);

        final UserStorage second = new UserStorage(folder(dir.resolve("data")), dir.resolve("legacy"));

        assertEquals(longToken, second.findByToken(longToken).orElseThrow().getToken());
    }

    @Test
    public void test__a_wrong_token_is_refused(@TempDir final Path dir) {
        final UserStorage storage = new UserStorage(folder(dir.resolve("data")), dir.resolve("legacy"));
        storage.create(TOKEN, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> storage.create("short", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> storage.create("a".repeat(41), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> storage.create("with space 1234567", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> storage.create(TOKEN, 0, 0)); // used already
        assertEquals(1, storage.all().size());
    }

    @Test
    public void test__a_number_out_of_three_bytes_is_refused(@TempDir final Path dir) {
        final UserStorage storage = new UserStorage(folder(dir.resolve("data")), dir.resolve("legacy"));

        assertThrows(IllegalArgumentException.class, () -> storage.create(TOKEN, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> storage.create(TOKEN, 0, UserStorage.MAX_NUMBER + 1));
        assertTrue(storage.create(TOKEN, UserStorage.MAX_NUMBER, UserStorage.MAX_NUMBER).getPersonId() > 0);
    }

    @Test
    public void test__person_cid_and_token_are_changed(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve("data"));
        final UserStorage storage = new UserStorage(folder(dir.resolve("data")), dir.resolve("legacy"));
        final int userId = storage.create(TOKEN, 0, 0).getId();

        storage.setPersonId(userId, 7);
        storage.setVatsimCid(userId, 123456);
        storage.setToken(userId, "newtoken1234567890");

        final UserStorage reloaded = new UserStorage(folder(dir.resolve("data")), dir.resolve("legacy"));
        final User user = reloaded.findById(userId).orElseThrow();
        assertEquals(7, user.getPersonId());
        assertEquals(123456, user.getVatsimCid());
        assertEquals("newtoken1234567890", user.getToken());
        assertTrue(reloaded.findByToken(TOKEN).isEmpty()); // the old token does not open
        assertTrue(reloaded.findByToken("newtoken1234567890").isPresent());
    }

    @Test
    public void test__the_token_can_be_set_to_itself_but_not_to_the_token_of_another_user(@TempDir final Path dir) {
        final UserStorage storage = new UserStorage(folder(dir.resolve("data")), dir.resolve("legacy"));
        final int first = storage.create(TOKEN, 0, 0).getId();
        storage.create("second1234567890zz", 0, 0);

        storage.setToken(first, TOKEN);

        assertThrows(IllegalArgumentException.class, () -> storage.setToken(first, "second1234567890zz"));
        assertThrows(IllegalArgumentException.class, () -> storage.setPersonId(99, 1));
    }

    // ---- the one-time import of the property files of the previous storage (temporary) ----

    @Test
    public void test__legacy_files_are_imported_with_their_ids(@TempDir final Path dir) throws IOException {
        final Path legacy = Files.createDirectories(dir.resolve("legacy"));
        writeLegacyUser(legacy, 1, "token=abc123def456ghi789\nperson.id=1\nvatsim.cid=913904\n");
        writeLegacyUser(legacy, 4, "token=second1234567890zz\nlast.location.airport=KOAK\n");

        final UserStorage storage = new UserStorage(folder(Files.createDirectories(dir.resolve("data"))), legacy);

        final User first = storage.findById(1).orElseThrow();
        assertEquals(1, first.getPersonId());
        assertEquals(913904, first.getVatsimCid());
        final User fourth = storage.findByToken("second1234567890zz").orElseThrow();
        assertEquals(4, fourth.getId()); // the id of the file name, not the next free one
        assertEquals(0, fourth.getPersonId());
        assertEquals(0, fourth.getVatsimCid());
        assertTrue(storage.findById(2).isEmpty());
        assertTrue(storage.findById(3).isEmpty());
    }

    @Test
    public void test__not_a_number_in_a_legacy_file_is_ignored(@TempDir final Path dir) throws IOException {
        final Path legacy = Files.createDirectories(dir.resolve("legacy"));
        writeLegacyUser(legacy, 1, "token=abc123def456ghi789\nperson.id=one\nvatsim.cid= \n");

        final User user = new UserStorage(folder(Files.createDirectories(dir.resolve("data"))), legacy).findById(1).orElseThrow();

        assertEquals(0, user.getPersonId());
        assertEquals(0, user.getVatsimCid());
    }

    @Test
    public void test__legacy_files_are_not_touched_and_are_imported_once(@TempDir final Path dir) throws IOException {
        final Path legacy = Files.createDirectories(dir.resolve("legacy"));
        final String content = "token=abc123def456ghi789\nperson.id=1\n";
        writeLegacyUser(legacy, 1, content);
        final Path data = Files.createDirectories(dir.resolve("data"));
        new UserStorage(folder(data), legacy);

        // the file changes after the import, the storage already has the users and does not read the files again
        writeLegacyUser(legacy, 1, "token=changed1234567890zz\nperson.id=9\n");
        final UserStorage second = new UserStorage(folder(data), legacy);

        assertEquals(1, second.findById(1).orElseThrow().getPersonId());
        assertTrue(second.findByToken("abc123def456ghi789").isPresent());
        assertFalse(second.findByToken("changed1234567890zz").isPresent());
    }

    @Test
    public void test__a_legacy_file_without_a_token_is_skipped(@TempDir final Path dir) throws IOException {
        final Path legacy = Files.createDirectories(dir.resolve("legacy"));
        writeLegacyUser(legacy, 1, "person.id=1\n");

        final UserStorage storage = new UserStorage(folder(Files.createDirectories(dir.resolve("data"))), legacy);

        assertTrue(storage.all().isEmpty());
    }
}
