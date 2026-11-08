package net.simforge.airways2.tools;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * The pictures which can be the avatar of a pilot or of a passenger (docs/avatars.md). The list and the four flags of every picture (forbidden, common, pilot, special)
 * are in {@link AvatarList}; this class is its single instance of the server and the short ways to the questions the game asks. The live file of the list is
 * ./avatars/avatars.csv (the folder is the system property airways.avatars.folder, the tests give another one), it is outside of the classpath: the admin page changes it.
 * The pictures are the 32x32 icons of FatCow on simforge.net.
 * <p>
 * The list stays on the server: the pages get only the name of the picture of the pilot and never the list of all of them, the admin page gets all of it.
 */
public final class Avatars {
    // the pictures for a person who has no avatar yet: by the parity of the id of the person
    public static final String DEFAULT_FOR_ODD_PERSON_ID = "user_pilot.png";
    public static final String DEFAULT_FOR_EVEN_PERSON_ID = "user_pilot_civil.png";

    private static final class Holder {
        static final AvatarList LIST = AvatarList.forFile(Paths.get(System.getProperty("airways.avatars.folder", "./avatars"), "avatars.csv"));
    }

    private Avatars() {
    }

    /**
     * The single list of the server.
     */
    public static AvatarList shared() {
        return Holder.LIST;
    }

    /**
     * The file of the picture by the avatarId, empty if there is no such picture (0, a number out of the list) or it is forbidden.
     */
    public static Optional<String> fileOf(final int avatarId) {
        return shared().shownFileOf(avatarId);
    }

    /**
     * The files which a pilot may choose (the flag pilot, not forbidden), in the order of the list.
     */
    public static List<String> allowedFiles() {
        return shared().pilotFiles();
    }

    /**
     * The files which a passenger gets at random (the flag common, not forbidden), in the order of the list.
     */
    public static List<String> commonFiles() {
        return shared().commonFiles();
    }

    /**
     * The files of the special journeys (the flag special, not forbidden), in the order of the list.
     */
    public static List<String> specialFiles() {
        return shared().specialFiles();
    }

    /**
     * The avatarId of a picture which a pilot may choose by its file name, empty if there is no such picture, it is forbidden or a pilot may not choose it.
     */
    public static Optional<Integer> idOfAllowedFile(final String file) {
        return shared().idOfPilotFile(file);
    }

    /**
     * The picture to show for a person: its own if it has one which is not forbidden (the pilot may have chosen it when the picture was allowed to him, it stays),
     * otherwise one of two pilots by the parity of the id of the person.
     */
    public static String shownFile(final int personId, final int avatarId) {
        return fileOf(avatarId).orElse(personId % 2 != 0 ? DEFAULT_FOR_ODD_PERSON_ID : DEFAULT_FOR_EVEN_PERSON_ID);
    }

    /**
     * The number of the pictures in the list (forbidden ones too).
     */
    public static int size() {
        return shared().size();
    }
}
