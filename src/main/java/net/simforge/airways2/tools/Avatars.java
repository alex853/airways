package net.simforge.airways2.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The pictures which can be the avatar of a pilot. The list is avatars.csv of the resources (columns: file, forbidden): the file is the name of an icon (the
 * 32x32 icons of FatCow on simforge.net), forbidden is 1 for a picture which must not be shown. The avatarId of a person is the number of the row of the file,
 * the first row after the header is 1, 0 is no avatar. The rows are never moved or deleted, otherwise everybody gets another picture: a picture which is not
 * wanted any more is forbidden, new pictures are added to the end.
 * <p>
 * The list stays on the server: the pages get only the name of the picture of the pilot and never the list of all of them.
 */
public final class Avatars {
    private static final Logger log = LoggerFactory.getLogger(Avatars.class);

    // the pictures for a person who has no avatar yet: by the parity of the id of the person
    public static final String DEFAULT_FOR_ODD_PERSON_ID = "user_pilot.png";
    public static final String DEFAULT_FOR_EVEN_PERSON_ID = "user_pilot_civil.png";

    private record Row(String file, boolean forbidden) {
    }

    private static volatile List<Row> rows;

    private Avatars() {
    }

    /**
     * The file of the picture by the avatarId, empty if there is no such picture (0, a number out of the list) or it is forbidden.
     */
    public static Optional<String> fileOf(final int avatarId) {
        final List<Row> all = rows();
        if (avatarId < 1 || avatarId > all.size()) {
            return Optional.empty();
        }
        final Row row = all.get(avatarId - 1);
        return row.forbidden() ? Optional.empty() : Optional.of(row.file());
    }

    /**
     * The picture to show for a person: its own if it has one which is allowed, otherwise one of two pilots by the parity of the id of the person.
     */
    public static String shownFile(final int personId, final int avatarId) {
        return fileOf(avatarId).orElse(personId % 2 != 0 ? DEFAULT_FOR_ODD_PERSON_ID : DEFAULT_FOR_EVEN_PERSON_ID);
    }

    /**
     * The number of the pictures in the list (forbidden ones too).
     */
    public static int size() {
        return rows().size();
    }

    private static List<Row> rows() {
        List<Row> result = rows;
        if (result == null) {
            synchronized (Avatars.class) {
                result = rows;
                if (result == null) {
                    result = load();
                    rows = result;
                }
            }
        }
        return result;
    }

    private static List<Row> load() {
        final List<Row> loaded = new ArrayList<>();
        try (InputStream stream = Avatars.class.getResourceAsStream("/avatars.csv")) {
            if (stream == null) {
                log.error("avatars.csv is not found, there are no avatars");
                return List.of();
            }
            final BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            reader.readLine(); // the header
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                final String[] parts = line.split(",", -1);
                loaded.add(new Row(parts[0].trim(), parts.length > 1 && "1".equals(parts[1].trim())));
            }
        } catch (final IOException e) {
            log.error("avatars.csv cannot be read, there are no avatars", e);
            return List.of();
        }
        return List.copyOf(loaded);
    }
}
