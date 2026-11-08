package net.simforge.airways2.app.admin;

import net.simforge.airways2.tools.AvatarList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The admin side of the list of the pictures: the whole list and a change of one flag.
 */
class AvatarsControllerTest {
    private AvatarList list;
    private Path file;

    @BeforeEach
    void beforeEach(@TempDir final Path dir) {
        file = dir.resolve("avatars").resolve("avatars.csv");
        list = new AvatarList(file, List.of("file,forbidden", "a.png,0", "b.png,1"));
    }

    @Test
    void theListIsTheWholeListWithTheFourFlags() {
        final List<AvatarsController.AvatarDto> dtos = AvatarsController.list(list);

        assertEquals(2, dtos.size());
        assertEquals(1, dtos.get(0).getId());
        assertEquals("a.png", dtos.get(0).getFile());
        assertTrue(dtos.get(0).isCommon() && dtos.get(0).isPilot());
        assertFalse(dtos.get(0).isForbidden() || dtos.get(0).isSpecial());
        assertTrue(dtos.get(1).isForbidden()); // a forbidden picture is in the list too
    }

    @Test
    void aChangeOfAFlagIsAnswerWithTheRowAndIsInTheFile() throws IOException {
        final AvatarsController.AvatarDto changed = AvatarsController.setFlag(list, "a.png", "special", true);

        assertTrue(changed.isSpecial());
        assertTrue(changed.isCommon() && changed.isPilot()); // the other flags are not touched
        assertEquals(List.of("file,forbidden,common,pilot,special", "a.png,0,1,1,1", "b.png,1,0,0,0"), Files.readAllLines(file));

        AvatarsController.setFlag(list, "a.png", "special", false);
        assertEquals("a.png,0,1,1,0", Files.readAllLines(file).get(1));
    }

    @Test
    void aWrongFlagIsABadRequestAndAnUnknownPictureIsNotFound() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> AvatarsController.setFlag(list, "a.png", "hidden", true)).getStatus().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> AvatarsController.setFlag(list, "zzz.png", "common", true)).getStatus().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> AvatarsController.setFlag(list, "../a.png", "common", true)).getStatus().value());
        assertFalse(Files.exists(file));
    }

    @Test
    void aFileWhichCannotBeWrittenIsAServerError() throws IOException {
        Files.writeString(file.getParent().getParent().resolve("avatars"), "not a folder");

        assertEquals(500, assertThrows(ResponseStatusException.class, () -> AvatarsController.setFlag(list, "a.png", "special", true)).getStatus().value());
        assertFalse(list.byId(1).orElseThrow().special());
    }
}
