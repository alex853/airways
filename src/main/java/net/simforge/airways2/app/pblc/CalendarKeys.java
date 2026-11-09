package net.simforge.airways2.app.pblc;

import net.simforge.airways2.app.tools.Id;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.OptionalInt;

/**
 * The keys of the calendar links of the pilots (docs/calendar.md). The link of a user is {@code <sqids of the user id>-<key>}, where the key is HMAC-SHA256 of the user
 * id with the secret of the server (the first 16 bytes, hex). Nothing is stored per user; the secret is one file outside of git, it is created and replaced only by the admin
 * (regenerate): all the links given out before stop working. While there is no secret no link works.
 */
public class CalendarKeys {
    private static final Logger log = LoggerFactory.getLogger(CalendarKeys.class);

    static final String FOLDER_PROPERTY = "airways.calendar.folder";
    private static final int SECRET_BYTES = 32;
    private static final int KEY_BYTES = 16;

    private static CalendarKeys instance;

    public static synchronized CalendarKeys getInstance() {
        if (instance == null) {
            instance = new CalendarKeys(Paths.get(System.getProperty(FOLDER_PROPERTY, "./calendar"), "secret.txt"));
        }
        return instance;
    }

    private final Path file;
    private volatile byte[] secret;

    CalendarKeys(final Path file) {
        this.file = file;
        this.secret = load(file);
    }

    private static byte[] load(final Path file) {
        if (!Files.exists(file)) {
            log.warn("calendar - no secret file, the calendar links do not work until the admin generates the secret");
            return null;
        }
        try {
            final byte[] result = HexFormat.of().parseHex(Files.readString(file, StandardCharsets.UTF_8).trim());
            if (result.length != SECRET_BYTES) {
                throw new IllegalArgumentException("wrong length of the secret");
            }
            return result;
        } catch (final IOException | IllegalArgumentException e) {
            // the file is not replaced by itself: a broken file is a mistake which the admin has to look at
            log.error("calendar - unable to read the secret file, the calendar links do not work", e);
            return null;
        }
    }

    public boolean isReady() {
        return secret != null;
    }

    /**
     * A new random secret, saved to the file (a temporary file and a move over it) before it is used. If the file can not be written the old secret stays.
     */
    public synchronized void regenerate() throws IOException {
        final byte[] fresh = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(fresh);

        final Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        final Path tmp = parent.resolve(file.getFileName() + ".tmp");
        Files.writeString(tmp, HexFormat.of().formatHex(fresh) + "\n", StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }

        secret = fresh;
        log.info("calendar - the secret is regenerated, all the old calendar links stopped to work");
    }

    /**
     * The link of the user without the extension, null while there is no secret.
     */
    public String slugOf(final int userId) {
        final byte[] current = secret;
        return current == null ? null : Id.encode(userId) + "-" + keyOf(current, userId);
    }

    /**
     * The user of the link ({@code .ics} at the end is allowed), empty when the link is wrong or there is no secret.
     */
    public OptionalInt userIdOf(final String slugWithExtension) {
        final byte[] current = secret;
        if (current == null || slugWithExtension == null) {
            return OptionalInt.empty();
        }
        final String slug = slugWithExtension.endsWith(".ics")
                ? slugWithExtension.substring(0, slugWithExtension.length() - ".ics".length())
                : slugWithExtension;
        final int dash = slug.lastIndexOf('-');
        if (dash <= 0) {
            return OptionalInt.empty();
        }
        final String idPart = slug.substring(0, dash);
        final String keyPart = slug.substring(dash + 1);

        final int userId;
        try {
            userId = Id.decode(idPart);
        } catch (final RuntimeException e) {
            return OptionalInt.empty();
        }
        // only the canonical text of the id is accepted
        if (userId <= 0 || !Id.encode(userId).equals(idPart)) {
            return OptionalInt.empty();
        }
        final boolean same = MessageDigest.isEqual(keyOf(current, userId).getBytes(StandardCharsets.UTF_8), keyPart.getBytes(StandardCharsets.UTF_8));
        return same ? OptionalInt.of(userId) : OptionalInt.empty();
    }

    private static String keyOf(final byte[] secret, final int userId) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            final byte[] full = mac.doFinal(("calendar:" + userId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(full, 0, KEY_BYTES);
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
