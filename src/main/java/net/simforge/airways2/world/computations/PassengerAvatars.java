package net.simforge.airways2.world.computations;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The avatars of the passengers of a journey (docs/avatars.md): the initials (two capital English letters) and the icon. Nothing is stored: the id of the journey is the
 * seed, so the same journey always gives the same list, and a passenger does not depend on the size of the group (the passenger number k of the journey is always the
 * same one). The group of 4 gives 4 avatars, one for every passenger.
 * <p>
 * The icon is chosen from the pool of the common icons by the highest hash of the seed and the name of the file (not by the index in the list), so a new icon in the pool
 * changes the picture only of the passengers who get that new icon, and a changed flag of one icon only of the passengers who had it.
 * <p>
 * <b>The algorithm must not change after it is released:</b> the avatars of the journeys which exist would change. The values are fixed by PassengerAvatarsTest
 * (computed independently of this code).
 * <p>
 * The exception is the special groups (SpecialGroupAvatars): their avatars are the fixed list of the table.
 */
public final class PassengerAvatars {
    // the picture when the pool of the common icons is empty
    public static final String FALLBACK_ICON = "user.png";

    // a passenger number of a journey is less than this (it is a part of the seat key)
    private static final int MAX_GROUP_SIZE = 4096;

    private PassengerAvatars() {
    }

    /**
     * The initials and the icon of a passenger.
     */
    public record PassengerAvatar(String initials, String icon) {
    }

    /**
     * The avatars of all the passengers of a journey, the number k of the list is the passenger number k.
     *
     * @param iconPool the files which a passenger may get (the common icons), in any order
     */
    public static List<PassengerAvatar> of(final int journeyId, final int groupSize, final List<String> iconPool) {
        checkArgument(groupSize >= 0 && groupSize <= MAX_GROUP_SIZE, "the group size is 0 to %s: %s", MAX_GROUP_SIZE, groupSize);
        final List<PassengerAvatar> result = new ArrayList<>(groupSize);
        for (int index = 0; index < groupSize; index++) {
            result.add(passenger(journeyId, index, iconPool));
        }
        return result;
    }

    /**
     * The passenger number {@code index} (from 0) of a journey.
     */
    public static PassengerAvatar passenger(final int journeyId, final int index, final List<String> iconPool) {
        checkArgument(index >= 0 && index < MAX_GROUP_SIZE, "the passenger number is 0 to %s: %s", MAX_GROUP_SIZE - 1, index);
        // a special group has its own hand-made avatars: they are taken as they are, not by the hash
        final PassengerAvatar fixed = SpecialGroupAvatars.passenger(journeyId, index);
        if (fixed != null) {
            return fixed;
        }
        return hashed(journeyId, index, iconPool);
    }

    /**
     * The passenger by the hash of the journey (the ordinary journeys, and a passenger of a special group beyond its list). The tests of the algorithm use it so that they do not
     * depend on the table of the special groups.
     */
    static PassengerAvatar hashed(final int journeyId, final int index, final List<String> iconPool) {
        checkArgument(index >= 0 && index < MAX_GROUP_SIZE, "the passenger number is 0 to %s: %s", MAX_GROUP_SIZE - 1, index);
        final long state = mix(mix(journeyId) + index);

        final char first = (char) ('A' + Math.floorMod(mix(state + 1), 26));
        final char second = (char) ('A' + Math.floorMod(mix(state + 2), 26));

        String icon = FALLBACK_ICON;
        long best = 0;
        boolean found = false;
        for (final String file : iconPool) {
            final long score = mix(state ^ hash(file));
            if (!found || score > best) {
                best = score;
                icon = file;
                found = true;
            }
        }
        return new PassengerAvatar("" + first + second, icon);
    }

    /**
     * The place of a passenger in the row of the flight: the passengers of a transport flight are sorted by this key, so the order is random, the same every time, and the
     * passengers of a journey are not together. It does not depend on who else is on board, so the order of the ones who are on board does not change when the others come
     * or go.
     */
    public static long seatKey(final int transportFlightId, final int journeyId, final int index) {
        checkArgument(index >= 0 && index < MAX_GROUP_SIZE, "the passenger number is 0 to %s: %s", MAX_GROUP_SIZE - 1, index);
        return mix(mix(transportFlightId) ^ mix(((long) journeyId << 12) + index));
    }

    // SplitMix64: a good mix of the bits, the same on any machine
    static long mix(final long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // FNV-1a of the bytes of the name, 64 bits
    static long hash(final String text) {
        long h = 0xCBF29CE484222325L;
        for (final byte b : text.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xFF);
            h *= 0x100000001B3L;
        }
        return h;
    }
}
