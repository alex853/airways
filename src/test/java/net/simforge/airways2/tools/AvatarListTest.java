package net.simforge.airways2.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The list of the pictures: four independent flags of every row, the live file outside of the classpath which every change rewrites, the seed while there is no file.
 */
class AvatarListTest {
    private static final List<String> SEED = List.of("file,forbidden", "a.png,0", "b.png,0", "c.png,1", "d.png,0");

    private static AvatarList list(final Path dir) {
        return new AvatarList(dir.resolve("avatars").resolve("avatars.csv"), SEED);
    }

    @Test
    void theSeedIsTheListWhileThereIsNoFileAndNothingIsWritten(@TempDir final Path dir) {
        final AvatarList list = list(dir);

        assertEquals(4, list.size());
        assertEquals(List.of("a.png", "b.png", "d.png"), list.pilotFiles());
        assertEquals(List.of("a.png", "b.png", "d.png"), list.commonFiles());
        assertEquals(List.of(), list.specialFiles());
        assertFalse(Files.exists(dir.resolve("avatars")));
    }

    @Test
    void aForbiddenRowOfTheSeedIsNoneOfTheOtherThings(@TempDir final Path dir) {
        final AvatarList.Avatar c = list(dir).byId(3).orElseThrow();

        assertTrue(c.forbidden());
        assertFalse(c.common());
        assertFalse(c.pilot());
        assertFalse(c.special());
    }

    @Test
    void theFlagsAreIndependent(@TempDir final Path dir) {
        final AvatarList list = list(dir);

        list.setFlag("a.png", AvatarList.Flag.pilot, false);
        list.setFlag("b.png", AvatarList.Flag.common, false);
        list.setFlag("b.png", AvatarList.Flag.special, true);
        list.setFlag("d.png", AvatarList.Flag.special, true);

        assertEquals(List.of("b.png", "d.png"), list.pilotFiles());
        assertEquals(List.of("a.png", "d.png"), list.commonFiles());
        assertEquals(List.of("b.png", "d.png"), list.specialFiles());
        // a picture may be several things at once
        final AvatarList.Avatar d = list.byId(4).orElseThrow();
        assertTrue(d.common() && d.pilot() && d.special() && !d.forbidden());
    }

    @Test
    void aForbiddenPictureIsNowhereWhateverTheOtherFlagsAre(@TempDir final Path dir) {
        final AvatarList list = list(dir);

        list.setFlag("a.png", AvatarList.Flag.forbidden, true);

        assertEquals(Optional.empty(), list.shownFileOf(1));
        assertEquals(List.of("b.png", "d.png"), list.pilotFiles());
        assertEquals(List.of("b.png", "d.png"), list.commonFiles());
        assertEquals(Optional.empty(), list.idOfPilotFile("a.png"));
        // the flags stay: a picture which is allowed again is what it was
        list.setFlag("a.png", AvatarList.Flag.forbidden, false);
        assertEquals(Optional.of("a.png"), list.shownFileOf(1));
        assertEquals(Optional.of(1), list.idOfPilotFile("a.png"));
    }

    @Test
    void aPictureWhichAPilotMayNotChooseStaysShownToTheOneWhoHasIt(@TempDir final Path dir) {
        final AvatarList list = list(dir);

        list.setFlag("b.png", AvatarList.Flag.pilot, false);
        list.setFlag("b.png", AvatarList.Flag.special, true);

        assertEquals(Optional.empty(), list.idOfPilotFile("b.png"));
        assertEquals(Optional.of("b.png"), list.shownFileOf(2));
    }

    @Test
    void thePilotChoosesByTheExactNameOfAPictureHeMayChoose(@TempDir final Path dir) {
        final AvatarList list = list(dir);

        assertEquals(Optional.of(2), list.idOfPilotFile("b.png"));
        assertEquals(Optional.empty(), list.idOfPilotFile("c.png")); // forbidden
        assertEquals(Optional.empty(), list.idOfPilotFile("../b.png"));
        assertEquals(Optional.empty(), list.idOfPilotFile("B.PNG"));
        assertEquals(Optional.empty(), list.idOfPilotFile(null));
    }

    @Test
    void everyChangeRewritesTheFileAndItIsReadAgain(@TempDir final Path dir) throws IOException {
        final AvatarList list = list(dir);

        list.setFlag("b.png", AvatarList.Flag.special, true);
        list.setFlag("a.png", AvatarList.Flag.common, false);

        final Path file = dir.resolve("avatars").resolve("avatars.csv");
        assertEquals(List.of("file,forbidden,common,pilot,special", "a.png,0,0,1,0", "b.png,0,1,1,1", "c.png,1,0,0,0", "d.png,0,1,1,0"), Files.readAllLines(file));
        assertFalse(Files.exists(dir.resolve("avatars").resolve("avatars.csv.tmp")));
        // the file is the list after a restart
        final AvatarList again = list(dir);
        assertEquals(List.of("b.png"), again.specialFiles());
        assertEquals(List.of("b.png", "d.png"), again.commonFiles());
    }

    @Test
    void aChangeWhichChangesNothingDoesNotWriteTheFile(@TempDir final Path dir) {
        final AvatarList list = list(dir);

        list.setFlag("a.png", AvatarList.Flag.common, true);

        assertFalse(Files.exists(dir.resolve("avatars")));
    }

    @Test
    void anUnknownPictureIsRefused(@TempDir final Path dir) {
        final AvatarList list = list(dir);

        assertThrows(IllegalArgumentException.class, () -> list.setFlag("x.png", AvatarList.Flag.common, true));
        assertThrows(IllegalArgumentException.class, () -> list.setFlag("../a.png", AvatarList.Flag.common, true));
        assertThrows(IllegalArgumentException.class, () -> list.setFlag(null, AvatarList.Flag.common, true));
    }

    @Test
    void theListIsNotChangedWhenTheFileCannotBeWritten(@TempDir final Path dir) throws IOException {
        // a file is where the folder of the list has to be
        final Path blocker = dir.resolve("avatars");
        Files.writeString(blocker, "not a folder");
        final AvatarList list = new AvatarList(blocker.resolve("avatars.csv"), SEED);

        assertThrows(java.io.UncheckedIOException.class, () -> list.setFlag("a.png", AvatarList.Flag.pilot, false));

        assertTrue(list.byId(1).orElseThrow().pilot());
        assertEquals(List.of("a.png", "b.png", "d.png"), list.pilotFiles());
    }

    @Test
    void theNewPicturesOfTheSeedComeToTheEndOfTheLiveList(@TempDir final Path dir) throws IOException {
        final AvatarList old = list(dir);
        old.setFlag("a.png", AvatarList.Flag.special, true);
        final List<String> newerSeed = List.of("file,forbidden", "a.png,0", "b.png,0", "c.png,1", "d.png,0", "e.png,0", "f.png,1");

        final AvatarList newer = new AvatarList(dir.resolve("avatars").resolve("avatars.csv"), newerSeed);

        assertEquals(6, newer.size());
        assertEquals(Optional.of("e.png"), newer.shownFileOf(5));
        assertEquals(Optional.empty(), newer.shownFileOf(6)); // forbidden in the seed
        assertTrue(newer.byId(1).orElseThrow().special()); // what the admin has set stays
        assertTrue(newer.byId(5).orElseThrow().common());
        // the rows are written with the next change
        newer.setFlag("e.png", AvatarList.Flag.common, false);
        assertEquals(7, Files.readAllLines(dir.resolve("avatars").resolve("avatars.csv")).size());
    }

    @Test
    void theNewPicturesAreNotAddedWhenTheLiveListIsNotTheBeginningOfTheSeed(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve("avatars"));
        Files.write(dir.resolve("avatars").resolve("avatars.csv"), List.of("file,forbidden,common,pilot,special", "z.png,0,1,1,0"));

        final AvatarList list = list(dir);

        assertEquals(1, list.size());
        assertEquals(Optional.of("z.png"), list.shownFileOf(1));
    }

    @Test
    void aFileWithoutTheNewColumnsHasTheUsualValues(@TempDir final Path dir) throws IOException {
        Files.createDirectories(dir.resolve("avatars"));
        Files.write(dir.resolve("avatars").resolve("avatars.csv"), List.of("file,forbidden", "a.png,0", "b.png,1", "c.png,0,,0,1"));

        final AvatarList list = new AvatarList(dir.resolve("avatars").resolve("avatars.csv"), List.of("file,forbidden", "a.png,0", "b.png,1", "c.png,0"));

        assertTrue(list.byId(1).orElseThrow().common() && list.byId(1).orElseThrow().pilot());
        assertFalse(list.byId(2).orElseThrow().common());
        // an empty column is the usual value, a given one is what is given
        assertTrue(list.byId(3).orElseThrow().common());
        assertFalse(list.byId(3).orElseThrow().pilot());
        assertTrue(list.byId(3).orElseThrow().special());
    }

    @Test
    void theSeedOfTheResourcesIsTheFullList() {
        final AvatarList list = AvatarList.forFile(Path.of("not-there", "avatars.csv"));

        assertEquals(159, list.size());
        assertTrue(list.pilotFiles().contains("user_pilot.png"));
        assertEquals(List.of(), list.specialFiles());
    }
}
