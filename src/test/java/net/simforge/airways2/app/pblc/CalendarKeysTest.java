package net.simforge.airways2.app.pblc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CalendarKeysTest {
    @TempDir
    Path folder;

    @Test
    void noSecretNoLinks() {
        final CalendarKeys keys = new CalendarKeys(folder.resolve("secret.txt"));
        assertFalse(keys.isReady());
        assertNull(keys.slugOf(7));
        assertTrue(keys.userIdOf("abc-0123").isEmpty());
    }

    @Test
    void linkRoundTrip() throws IOException {
        final CalendarKeys keys = new CalendarKeys(folder.resolve("calendar").resolve("secret.txt"));
        keys.regenerate();
        assertTrue(keys.isReady());

        final String slug = keys.slugOf(7);
        assertEquals(7, keys.userIdOf(slug).getAsInt());
        assertEquals(7, keys.userIdOf(slug + ".ics").getAsInt());
        // the same user always has the same link
        assertEquals(slug, keys.slugOf(7));
        assertNotEquals(slug, keys.slugOf(8));
    }

    @Test
    void wrongLinksAreRefused() throws IOException {
        final CalendarKeys keys = new CalendarKeys(folder.resolve("secret.txt"));
        keys.regenerate();
        final String slug = keys.slugOf(7);
        final String id = slug.substring(0, slug.lastIndexOf('-'));
        final String key = slug.substring(slug.lastIndexOf('-') + 1);

        // the key of another user, a broken key, no key, rubbish
        assertTrue(keys.userIdOf(keys.slugOf(8).substring(0, keys.slugOf(8).lastIndexOf('-')) + "-" + key).isEmpty());
        final char last = key.charAt(key.length() - 1);
        assertTrue(keys.userIdOf(id + "-" + key.substring(0, key.length() - 1) + (last == '0' ? '1' : '0')).isEmpty());
        assertTrue(keys.userIdOf(id + "-").isEmpty());
        assertTrue(keys.userIdOf(id).isEmpty());
        assertTrue(keys.userIdOf("").isEmpty());
        assertTrue(keys.userIdOf("-" + key).isEmpty());
        assertTrue(keys.userIdOf("!!!-" + key).isEmpty());
        assertTrue(keys.userIdOf(null).isEmpty());
    }

    @Test
    void regenerateChangesAllLinksAndSurvivesRestart() throws IOException {
        final Path file = folder.resolve("secret.txt");
        final CalendarKeys keys = new CalendarKeys(file);
        keys.regenerate();
        final String before = keys.slugOf(7);

        // the same file gives the same links after a restart
        assertEquals(before, new CalendarKeys(file).slugOf(7));

        keys.regenerate();
        assertNotEquals(before, keys.slugOf(7));
        assertTrue(keys.userIdOf(before).isEmpty(), "the old link does not work");
        assertEquals(7, keys.userIdOf(keys.slugOf(7)).getAsInt());
        assertEquals(keys.slugOf(7), new CalendarKeys(file).slugOf(7));
        assertFalse(Files.exists(folder.resolve("secret.txt.tmp")));
    }

    @Test
    void brokenFileIsNotReplacedByItself() throws IOException {
        final Path file = folder.resolve("secret.txt");
        Files.writeString(file, "not a secret");
        final CalendarKeys keys = new CalendarKeys(file);
        assertFalse(keys.isReady());
        assertEquals("not a secret", Files.readString(file));
    }
}
