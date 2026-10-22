package net.simforge.airways2.tools;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The list of the avatars (avatars.csv of the resources): the avatarId is the number of the row, 0 is none, a forbidden picture is not shown.
 */
class AvatarsTest {
    @Test
    void theFirstRowIsOneAndNumbersGoByTheRowsOfTheFile() {
        assertEquals(Optional.of("user.png"), Avatars.fileOf(1));
        // the rows are never moved: these two are the default pilots
        assertEquals(Optional.of(Avatars.DEFAULT_FOR_ODD_PERSON_ID), Avatars.fileOf(106));
        assertEquals(Optional.of(Avatars.DEFAULT_FOR_EVEN_PERSON_ID), Avatars.fileOf(107));
    }

    @Test
    void zeroAndNumbersOutOfTheListAreNoPicture() {
        assertEquals(Optional.empty(), Avatars.fileOf(0));
        assertEquals(Optional.empty(), Avatars.fileOf(-1));
        assertEquals(Optional.empty(), Avatars.fileOf(Avatars.size() + 1));
        assertEquals(Optional.of("user_zorro.png"), Avatars.fileOf(Avatars.size()));
    }

    @Test
    void aForbiddenPictureIsNotShown() {
        // the second row, user_add.png, is an icon of an action, not a man: it is forbidden
        assertEquals(Optional.empty(), Avatars.fileOf(2));
    }

    @Test
    void aPersonWithoutAPictureGetsOneOfTwoPilotsByTheParityOfItsId() {
        assertEquals("user_pilot.png", Avatars.shownFile(1, 0));
        assertEquals("user_pilot.png", Avatars.shownFile(7, 0));
        assertEquals("user_pilot_civil.png", Avatars.shownFile(2, 0));
        assertEquals("user_pilot_civil.png", Avatars.shownFile(100, 0));
    }

    @Test
    void aPersonWithAPictureKeepsItEvenIfThePersonIdIsOdd() {
        assertEquals("user.png", Avatars.shownFile(1, 1));
        assertEquals("user_zorro.png", Avatars.shownFile(3, Avatars.size()));
    }

    @Test
    void aForbiddenPictureIsReplacedByThePilotOfTheParity() {
        assertEquals("user_pilot.png", Avatars.shownFile(1, 2));
        assertEquals("user_pilot_civil.png", Avatars.shownFile(2, 2));
    }

    @Test
    void theFilesAreDifferentPngNames() {
        final Set<String> seen = new HashSet<>();
        for (int id = 1; id <= Avatars.size(); id++) {
            // a forbidden row has no file by fileOf, so the names are read through the allowed ones only: the allowed ones are png names without a path
            final Optional<String> file = Avatars.fileOf(id);
            file.ifPresent(f -> {
                assertTrue(f.endsWith(".png") && !f.contains("/") && !f.contains("\\") && !f.contains(".."), f);
                assertTrue(seen.add(f), "a picture twice in the list: " + f);
            });
        }
        assertTrue(seen.size() > 100);
    }
}
