package net.simforge.airways2.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringsTest {
    private final Strings strings = new Strings();

    @Test
    public void test__ids_start_from_one_and_grow() {
        assertEquals(1, strings.findOrAdd("first"));
        assertEquals(2, strings.findOrAdd("second"));
        assertEquals(3, strings.findOrAdd("third"));
    }

    @Test
    public void test__find_or_add_returns_the_same_id_for_the_same_string() {
        final int id = strings.findOrAdd("Heathrow");

        assertEquals(id, strings.findOrAdd("Heathrow"));
        assertEquals(id, strings.find("Heathrow"));
    }

    @Test
    public void test__find_of_unknown_string_returns_minus_one() {
        strings.findOrAdd("known");

        assertEquals(-1, strings.find("unknown"));
        assertEquals(-1, new Strings().find("anything"));
    }

    @Test
    public void test__by_id_returns_the_string() {
        final int a = strings.findOrAdd("Alpha");
        final int b = strings.findOrAdd("Bravo");

        assertEquals("Alpha", strings.byId(a));
        assertEquals("Bravo", strings.byId(b));
    }

    @Test
    public void test__strings_of_the_same_length_are_different() {
        final int a = strings.findOrAdd("abcd");
        final int b = strings.findOrAdd("abce");

        assertNotEquals(a, b);
        assertEquals("abcd", strings.byId(a));
        assertEquals("abce", strings.byId(b));
    }

    @Test
    public void test__a_string_which_is_a_prefix_of_another_one_is_different() {
        final int longer = strings.findOrAdd("Frankfurt am Main");
        final int shorter = strings.findOrAdd("Frankfurt");

        assertNotEquals(longer, shorter);
        assertEquals("Frankfurt", strings.byId(shorter));
        assertEquals("Frankfurt am Main", strings.byId(longer));
    }

    @Test
    public void test__empty_string() {
        final int id = strings.findOrAdd("");

        assertEquals("", strings.byId(id));
        assertEquals(id, strings.findOrAdd(""));
        assertEquals("after", strings.byId(strings.findOrAdd("after")));
    }

    @Test
    public void test__non_ascii_string_is_stored_in_utf8() {
        final int id = strings.findOrAdd("Москва");

        assertEquals("Москва", strings.byId(id));
        assertEquals(id, strings.find("Москва"));
    }

    @Test
    public void test__string_of_the_max_length_of_255_bytes_is_stored_properly() {
        // the length is kept in one byte, a string above 127 bytes must not get a negative length
        final String max = "x".repeat(255);
        final String above127 = "y".repeat(200);

        final int maxId = strings.findOrAdd(max);
        final int aboveId = strings.findOrAdd(above127);

        assertEquals(max, strings.byId(maxId));
        assertEquals(above127, strings.byId(aboveId));
        assertEquals(maxId, strings.find(max));
        assertEquals(aboveId, strings.find(above127));
    }

    @Test
    public void test__string_longer_than_255_bytes_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> strings.findOrAdd("x".repeat(256)));
        // two bytes per letter
        assertThrows(IllegalArgumentException.class, () -> strings.findOrAdd("ж".repeat(128)));
    }

    @Test
    public void test__rejected_string_does_not_break_the_storage() {
        final int id = strings.findOrAdd("before");

        assertThrows(IllegalArgumentException.class, () -> strings.findOrAdd("x".repeat(300)));

        assertEquals("before", strings.byId(id));
        assertEquals(id + 1, strings.findOrAdd("after"));
    }

    @Test
    public void test__save_and_load_keep_all_the_strings(@TempDir final Path dir) throws IOException {
        final int a = strings.findOrAdd("Alpha");
        final int empty = strings.findOrAdd("");
        final int unicode = strings.findOrAdd("Köln");
        final int big = strings.findOrAdd("z".repeat(250));

        strings.save(dir);

        final Strings loaded = new Strings();
        loaded.loadIfExists(dir);
        assertEquals("Alpha", loaded.byId(a));
        assertEquals("", loaded.byId(empty));
        assertEquals("Köln", loaded.byId(unicode));
        assertEquals("z".repeat(250), loaded.byId(big));
        assertEquals(a, loaded.find("Alpha"));
        assertEquals(big + 1, loaded.findOrAdd("new string"));
    }

    @Test
    public void test__save_creates_the_directory(@TempDir final Path dir) throws IOException {
        final Path nested = dir.resolve("a").resolve("b");
        strings.findOrAdd("x");

        strings.save(nested);

        assertEquals(true, Files.exists(nested.resolve("string-index")));
        assertEquals(true, Files.exists(nested.resolve("string-data")));
    }

    @Test
    public void test__load_of_missing_files_gives_empty_strings(@TempDir final Path dir) throws IOException {
        strings.findOrAdd("will be dropped");

        strings.loadIfExists(dir);

        assertEquals(-1, strings.find("will be dropped"));
        assertEquals(1, strings.findOrAdd("new"));
    }
}
