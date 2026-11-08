package net.simforge.airways2.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The list of the pictures (icons) and what each of them may be used for (docs/avatars.md). Every row has four independent flags, not one kind:
 * <ul>
 * <li>forbidden: the picture is not shown anywhere, even to a person who has chosen it (the icons of actions, the pictures which are not wanted);</li>
 * <li>common: it may be given to a passenger at random;</li>
 * <li>pilot: a pilot may choose it for himself;</li>
 * <li>special: it is for the special journeys (they live for ever, like Darth Vader), a pilot cannot choose it and a passenger does not get it at random
 * (so a picture is special and neither common nor pilot, but the flags do not exclude each other: the admin decides).</li>
 * </ul>
 * The live list is a CSV file (file, forbidden, common, pilot, special) outside of the classpath, the admin page changes it and every change rewrites the file at once
 * (a temporary file and a move). While there is no such file the list is the seed of the resources (avatars.csv: file, forbidden); the file is created by the first change.
 * The avatarId of a person is the number of the row of the file, the first row after the header is 1, 0 is no avatar. The rows are never moved or deleted, otherwise
 * everybody gets another picture: a picture which is not wanted is forbidden, new pictures are added to the end of the seed (they are added to the end of the live list
 * when it is read, with the usual flags, while the names of the rows of the file are the first names of the seed).
 */
public final class AvatarList {
    private static final Logger log = LoggerFactory.getLogger(AvatarList.class);

    public static final String HEADER = "file,forbidden,common,pilot,special";

    /**
     * The four flags of a picture, by the names of the columns.
     */
    public enum Flag {
        forbidden, common, pilot, special
    }

    /**
     * A picture of the list and what it may be used for.
     */
    public record Avatar(int id, String file, boolean forbidden, boolean common, boolean pilot, boolean special) {
        public boolean get(final Flag flag) {
            return switch (flag) {
                case forbidden -> forbidden;
                case common -> common;
                case pilot -> pilot;
                case special -> special;
            };
        }

        Avatar with(final Flag flag, final boolean value) {
            return new Avatar(id, file,
                    flag == Flag.forbidden ? value : forbidden,
                    flag == Flag.common ? value : common,
                    flag == Flag.pilot ? value : pilot,
                    flag == Flag.special ? value : special);
        }
    }

    private final Path path;
    private final List<String> seedLines;
    private List<Avatar> rows; // replaced as a whole, guarded by the lock of the list

    /**
     * @param path      the live file of the list; it does not need to exist
     * @param seedLines the lines of the seed: the header and the rows (file, forbidden), the list while the live file does not exist
     */
    public AvatarList(final Path path, final List<String> seedLines) {
        this.path = path;
        this.seedLines = List.copyOf(seedLines);
    }

    /**
     * The list of the live file with the seed from avatars.csv of the resources.
     */
    public static AvatarList forFile(final Path path) {
        return new AvatarList(path, readSeedFromResources());
    }

    // ---- reading

    /**
     * The picture by the avatarId, empty if there is no such picture (0, a number out of the list).
     */
    public synchronized Optional<Avatar> byId(final int avatarId) {
        final List<Avatar> all = rows();
        return avatarId < 1 || avatarId > all.size() ? Optional.empty() : Optional.of(all.get(avatarId - 1));
    }

    /**
     * The file of the picture which can be shown by the avatarId: empty if there is no such picture or it is forbidden.
     */
    public synchronized Optional<String> shownFileOf(final int avatarId) {
        return byId(avatarId).filter(avatar -> !avatar.forbidden()).map(Avatar::file);
    }

    public synchronized List<Avatar> all() {
        return rows();
    }

    public synchronized int size() {
        return rows().size();
    }

    /**
     * The files which a pilot may choose (flag pilot, not forbidden), in the order of the list.
     */
    public synchronized List<String> pilotFiles() {
        return rows().stream().filter(avatar -> avatar.pilot() && !avatar.forbidden()).map(Avatar::file).toList();
    }

    /**
     * The files which a passenger gets at random (flag common, not forbidden), in the order of the list.
     */
    public synchronized List<String> commonFiles() {
        return rows().stream().filter(avatar -> avatar.common() && !avatar.forbidden()).map(Avatar::file).toList();
    }

    /**
     * The files of the special journeys (flag special, not forbidden), in the order of the list.
     */
    public synchronized List<String> specialFiles() {
        return rows().stream().filter(avatar -> avatar.special() && !avatar.forbidden()).map(Avatar::file).toList();
    }

    /**
     * The avatarId of a picture which a pilot may choose, by its file name: empty if there is no such picture, it is forbidden or a pilot may not choose it. The name
     * must be exactly the name of the list, so nothing else (a path, another file) can be chosen.
     */
    public synchronized Optional<Integer> idOfPilotFile(final String file) {
        if (file == null) {
            return Optional.empty();
        }
        return rows().stream()
                .filter(avatar -> avatar.file().equals(file) && avatar.pilot() && !avatar.forbidden())
                .map(Avatar::id)
                .findFirst();
    }

    // ---- changing

    /**
     * Sets a flag of a picture and rewrites the live file. The list is not changed if the file cannot be written.
     *
     * @throws IllegalArgumentException if there is no such picture
     * @throws UncheckedIOException     if the file cannot be written
     */
    public synchronized Avatar setFlag(final String file, final Flag flag, final boolean value) {
        final List<Avatar> all = rows();
        final Avatar old = all.stream().filter(avatar -> avatar.file().equals(file)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("there is no picture " + file));
        if (old.get(flag) == value) {
            return old; // nothing changes, the file is not rewritten
        }

        final Avatar changed = old.with(flag, value);
        final List<Avatar> next = new ArrayList<>(all);
        next.set(old.id() - 1, changed);
        write(next);
        rows = List.copyOf(next);
        log.info("Avatars - {} - {} is {}", file, flag, value);
        return changed;
    }

    // ---- the file

    private List<Avatar> rows() {
        if (rows == null) {
            rows = load();
        }
        return rows;
    }

    private List<Avatar> load() {
        final List<Avatar> seed = parse(seedLines);
        if (path == null || !Files.isRegularFile(path)) {
            return seed;
        }

        final List<Avatar> live;
        try {
            live = parse(Files.readAllLines(path, StandardCharsets.UTF_8));
        } catch (final IOException e) {
            // the live list is the truth and it cannot be read: the seed would show other flags and would be written over the file by the next change
            throw new UncheckedIOException("the list of the avatars cannot be read: " + path, e);
        }

        // the new pictures of the seed come to the end of the live list when the live list is the beginning of the seed
        if (seed.size() > live.size()) {
            for (int i = 0; i < live.size(); i++) {
                if (!live.get(i).file().equals(seed.get(i).file())) {
                    log.error("Avatars - the row {} of {} is {}, the seed has {}, the new pictures of the seed are not added", i + 1, path, live.get(i).file(), seed.get(i).file());
                    return live;
                }
            }
            final List<Avatar> merged = new ArrayList<>(live);
            for (int i = live.size(); i < seed.size(); i++) {
                merged.add(seed.get(i));
            }
            log.info("Avatars - {} new pictures of the seed are added to the end of the list", seed.size() - live.size());
            return List.copyOf(merged);
        }
        return live;
    }

    // The lines are the header and the rows. The columns of a row are file, forbidden, common, pilot, special; a column which is missing or empty has the usual value: a
    // picture which is not forbidden is common and a pilot may choose it, it is not special; a forbidden picture is none of them.
    static List<Avatar> parse(final List<String> lines) {
        final List<Avatar> result = new ArrayList<>();
        boolean header = true;
        for (final String line : lines) {
            if (header) {
                header = false;
                continue;
            }
            if (line.isBlank()) {
                continue;
            }
            final String[] parts = line.split(",", -1);
            final boolean forbidden = flagOf(parts, 1, false);
            result.add(new Avatar(result.size() + 1, parts[0].trim(), forbidden,
                    flagOf(parts, 2, !forbidden), flagOf(parts, 3, !forbidden), flagOf(parts, 4, false)));
        }
        return List.copyOf(result);
    }

    private static boolean flagOf(final String[] parts, final int index, final boolean byDefault) {
        if (index >= parts.length || parts[index].isBlank()) {
            return byDefault;
        }
        return "1".equals(parts[index].trim());
    }

    private void write(final List<Avatar> list) {
        final StringBuilder text = new StringBuilder(HEADER).append('\n');
        for (final Avatar avatar : list) {
            text.append(avatar.file()).append(',').append(bit(avatar.forbidden())).append(',').append(bit(avatar.common())).append(',')
                    .append(bit(avatar.pilot())).append(',').append(bit(avatar.special())).append('\n');
        }
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            final Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (final IOException e) {
            log.error("Avatars - the list cannot be written to {}", path, e);
            throw new UncheckedIOException("the list of the avatars cannot be written", e);
        }
    }

    private static int bit(final boolean value) {
        return value ? 1 : 0;
    }

    private static List<String> readSeedFromResources() {
        try (InputStream stream = AvatarList.class.getResourceAsStream("/avatars.csv")) {
            if (stream == null) {
                log.error("avatars.csv is not found, there are no avatars");
                return List.of(HEADER);
            }
            final BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return reader.lines().toList();
        } catch (final IOException e) {
            log.error("avatars.csv cannot be read, there are no avatars", e);
            return List.of(HEADER);
        }
    }
}
