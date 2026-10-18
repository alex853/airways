package net.simforge.airways2.app.userstorage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserStorageTest {
    private static void writeUser(final Path dir, final String content) throws IOException {
        Files.writeString(dir.resolve("user-1.cfg"), content);
    }

    @Test
    public void test__person_id_and_vatsim_cid_are_read(@TempDir final Path dir) throws IOException {
        writeUser(dir, "token=abc123def456ghi789\nperson.id=1\nvatsim.cid=913904\n");

        final User user = new UserStorage(dir).findById(1).orElseThrow();

        assertEquals(1, user.getPersonId());
        assertEquals(913904, user.getVatsimCid());
    }

    @Test
    public void test__user_without_person_and_cid_has_zeros(@TempDir final Path dir) throws IOException {
        writeUser(dir, "token=abc123def456ghi789\n");

        final User user = new UserStorage(dir).findById(1).orElseThrow();

        assertEquals(0, user.getPersonId());
        assertEquals(0, user.getVatsimCid());
    }

    @Test
    public void test__not_a_number_is_ignored(@TempDir final Path dir) throws IOException {
        writeUser(dir, "token=abc123def456ghi789\nperson.id=one\nvatsim.cid= \n");

        final User user = new UserStorage(dir).findById(1).orElseThrow();

        assertEquals(0, user.getPersonId());
        assertEquals(0, user.getVatsimCid());
    }

    @Test
    public void test__old_last_location_airport_property_is_ignored_and_the_file_is_not_touched(@TempDir final Path dir) throws IOException {
        final String content = "token=abc123def456ghi789\nlast.location.airport=KOAK\nperson.id=1\n";
        writeUser(dir, content);

        final UserStorage storage = new UserStorage(dir);

        assertTrue(storage.findByToken("abc123def456ghi789").isPresent());
        assertEquals(content, Files.readString(dir.resolve("user-1.cfg")));
    }
}
