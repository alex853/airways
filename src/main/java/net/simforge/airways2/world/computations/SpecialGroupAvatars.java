package net.simforge.airways2.world.computations;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The fixed avatars of the special groups (the immortal journeys, docs/special-group-journeys.md): a group is a hand-made set of travellers, so its passengers are not made by the
 * hash of the journey (PassengerAvatars) but taken from this table as they are: the number k of the list is the passenger number k of the journey.
 * <p>
 * The table is by the id of the journey. To add a group: the next entry with the avatars (the file of the icon, the initials of two capital English letters), then the size and the class of
 * the group by POST /admin/journey/special-group/params (the size of the group is the number of its avatars; a passenger with a number beyond the list gets an avatar by the hash).
 */
public final class SpecialGroupAvatars {
    private static final Pattern INITIALS = Pattern.compile("[A-Z]{2}");

    // journey id to the avatars of its passengers
    private static final Map<Integer, List<PassengerAvatars.PassengerAvatar>> GROUPS = Map.of(
            // Darth Vader with the captain Rex and the troopers
            1, List.of(
                    avatar("user_darth_vader.png", "DV"),
                    avatar("user_trooper_captain.png", "RX"),
                    avatar("user_trooper.png", "CR"),
                    avatar("user_trooper.png", "HV"),
                    avatar("user_trooper.png", "KX"),
                    avatar("user_trooper.png", "DX"),
                    avatar("user_trooper.png", "DU")
            )
    );

    private SpecialGroupAvatars() {
    }

    // the icon first, then the initials (the order of the table)
    private static PassengerAvatars.PassengerAvatar avatar(final String icon, final String initials) {
        if (icon == null || icon.isBlank() || !INITIALS.matcher(initials).matches()) {
            throw new IllegalArgumentException("A special group avatar is an icon and two capital letters: " + icon + " " + initials);
        }
        return new PassengerAvatars.PassengerAvatar(initials, icon);
    }

    /**
     * The avatars of the group of the journey, null if the journey has no fixed avatars.
     */
    public static List<PassengerAvatars.PassengerAvatar> of(final int journeyId) {
        return GROUPS.get(journeyId);
    }

    /**
     * The avatar of the passenger number {@code index} (from 0) of the journey, null if the journey has no fixed avatars or the number is beyond them.
     */
    public static PassengerAvatars.PassengerAvatar passenger(final int journeyId, final int index) {
        final List<PassengerAvatars.PassengerAvatar> group = GROUPS.get(journeyId);
        return group != null && index >= 0 && index < group.size() ? group.get(index) : null;
    }
}
