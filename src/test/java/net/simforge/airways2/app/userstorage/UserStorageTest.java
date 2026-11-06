package net.simforge.airways2.app.userstorage;

import net.simforge.airways2.world.WorldStorageStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    public void test__a_registered_user_is_found_by_id_and_by_token(@TempDir final Path dir) {
        final UserStorage storage = new UserStorage(folder(dir.resolve("data")));

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
        final UserStorage first = new UserStorage(folder(dir.resolve("data")));
        first.create(TOKEN, 5, 913904);
        first.create("second1234567890zz", 0, 0);

        final UserStorage second = new UserStorage(folder(dir.resolve("data")));

        assertEquals(2, second.all().size());
        assertEquals(5, second.findByToken(TOKEN).orElseThrow().getPersonId());
        assertEquals(2, second.findByToken("second1234567890zz").orElseThrow().getId());
    }

    @Test
    public void test__a_token_of_forty_characters_fits(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve("data"));
        final String longToken = "a1".repeat(20);
        final UserStorage first = new UserStorage(folder(dir.resolve("data")));
        first.create(longToken, 1, 1);

        final UserStorage second = new UserStorage(folder(dir.resolve("data")));

        assertEquals(longToken, second.findByToken(longToken).orElseThrow().getToken());
    }

    @Test
    public void test__a_wrong_token_is_refused(@TempDir final Path dir) {
        final UserStorage storage = new UserStorage(folder(dir.resolve("data")));
        storage.create(TOKEN, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> storage.create("short", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> storage.create("a".repeat(41), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> storage.create("with space 1234567", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> storage.create(TOKEN, 0, 0)); // used already
        assertEquals(1, storage.all().size());
    }

    @Test
    public void test__a_number_out_of_three_bytes_is_refused(@TempDir final Path dir) {
        final UserStorage storage = new UserStorage(folder(dir.resolve("data")));

        assertThrows(IllegalArgumentException.class, () -> storage.create(TOKEN, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> storage.create(TOKEN, 0, UserStorage.MAX_NUMBER + 1));
        assertTrue(storage.create(TOKEN, UserStorage.MAX_NUMBER, UserStorage.MAX_NUMBER).getPersonId() > 0);
    }

    @Test
    public void test__person_cid_and_token_are_changed(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve("data"));
        final UserStorage storage = new UserStorage(folder(dir.resolve("data")));
        final int userId = storage.create(TOKEN, 0, 0).getId();

        storage.setPersonId(userId, 7);
        storage.setVatsimCid(userId, 123456);
        storage.setToken(userId, "newtoken1234567890");

        final UserStorage reloaded = new UserStorage(folder(dir.resolve("data")));
        final User user = reloaded.findById(userId).orElseThrow();
        assertEquals(7, user.getPersonId());
        assertEquals(123456, user.getVatsimCid());
        assertEquals("newtoken1234567890", user.getToken());
        assertTrue(reloaded.findByToken(TOKEN).isEmpty()); // the old token does not open
        assertTrue(reloaded.findByToken("newtoken1234567890").isPresent());
    }

    @Test
    public void test__the_token_can_be_set_to_itself_but_not_to_the_token_of_another_user(@TempDir final Path dir) {
        final UserStorage storage = new UserStorage(folder(dir.resolve("data")));
        final int first = storage.create(TOKEN, 0, 0).getId();
        storage.create("second1234567890zz", 0, 0);

        storage.setToken(first, TOKEN);

        assertThrows(IllegalArgumentException.class, () -> storage.setToken(first, "second1234567890zz"));
        assertThrows(IllegalArgumentException.class, () -> storage.setPersonId(99, 1));
    }

    @Test
    public void test__a_user_without_a_token_cannot_log_in_and_is_kept(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve("data"));
        final UserStorage storage = new UserStorage(folder(dir.resolve("data")));

        final User created = storage.create(null, 0, 0);

        assertEquals(1, created.getId());
        assertNull(created.getToken());
        assertTrue(storage.findByToken("").isEmpty());
        assertTrue(storage.findByToken(null).isEmpty());
        final UserStorage reloaded = new UserStorage(folder(dir.resolve("data")));
        assertNull(reloaded.findById(1).orElseThrow().getToken());
        assertTrue(reloaded.findByToken("").isEmpty());
    }

    @Test
    public void test__two_users_without_a_token_do_not_clash(@TempDir final Path dir) {
        final UserStorage storage = new UserStorage(folder(dir.resolve("data")));

        storage.create(null, 0, 0);
        storage.create(null, 0, 0);

        assertEquals(2, storage.all().size());
    }

    @Test
    public void test__a_generated_token_is_saved_and_opens_the_user(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve("data"));
        final UserStorage storage = new UserStorage(folder(dir.resolve("data")));
        final int userId = storage.create(null, 3, 0).getId();

        final String token = storage.generateToken(userId);

        assertEquals(UserStorage.GENERATED_TOKEN_LENGTH, token.length());
        assertTrue(token.matches("[A-Za-z0-9]+"));
        assertEquals(userId, storage.findByToken(token).orElseThrow().getId());
        final UserStorage reloaded = new UserStorage(folder(dir.resolve("data")));
        assertEquals(userId, reloaded.findByToken(token).orElseThrow().getId());
        assertEquals(3, reloaded.findByToken(token).orElseThrow().getPersonId());
    }

    @Test
    public void test__a_new_generated_token_replaces_the_old_one(@TempDir final Path dir) {
        final UserStorage storage = new UserStorage(folder(dir.resolve("data")));
        final int userId = storage.create(TOKEN, 0, 0).getId();

        final String token = storage.generateToken(userId);

        assertFalse(TOKEN.equals(token));
        assertTrue(storage.findByToken(TOKEN).isEmpty());
        assertTrue(storage.findByToken(token).isPresent());
        assertThrows(IllegalArgumentException.class, () -> storage.generateToken(99));
    }
}
