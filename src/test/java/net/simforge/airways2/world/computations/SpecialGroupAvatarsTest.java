package net.simforge.airways2.world.computations;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fixed avatars of the special groups (docs/special-group-journeys.md): the journey of the table gets its list as it is, any other journey is made by the hash as before.
 */
public class SpecialGroupAvatarsTest {
    private static final List<String> POOL = List.of("a.png", "b.png", "c.png", "d.png", "e.png");

    @Test
    public void theJourneyOfTheTableGetsItsListAsItIs() {
        final List<PassengerAvatars.PassengerAvatar> group = SpecialGroupAvatars.of(1);

        assertNotNull(group);
        assertEquals(new PassengerAvatars.PassengerAvatar("DV", "user_darth_vader.png"), group.get(0));
        // the same list comes out of the generator of the passengers, whatever the pool is
        assertEquals(group, PassengerAvatars.of(1, group.size(), POOL));
        assertEquals(group, PassengerAvatars.of(1, group.size(), List.of()));
        for (int i = 0; i < group.size(); i++) {
            assertEquals(group.get(i), PassengerAvatars.passenger(1, i, POOL));
        }
    }

    @Test
    public void aSmallerGroupIsTheFirstOfTheList() {
        final List<PassengerAvatars.PassengerAvatar> group = SpecialGroupAvatars.of(1);

        assertEquals(group.subList(0, 3), PassengerAvatars.of(1, 3, POOL));
    }

    @Test
    public void aPassengerBeyondTheListIsMadeByTheHash() {
        final List<PassengerAvatars.PassengerAvatar> group = SpecialGroupAvatars.of(1);
        final int beyond = group.size();

        assertNull(SpecialGroupAvatars.passenger(1, beyond));
        final PassengerAvatars.PassengerAvatar avatar = PassengerAvatars.passenger(1, beyond, POOL);
        assertTrue(POOL.contains(avatar.icon()));
        assertEquals(avatar, PassengerAvatars.passenger(1, beyond, POOL));
    }

    @Test
    public void otherJourneysAreMadeByTheHashAsBefore() {
        assertNull(SpecialGroupAvatars.of(2));
        assertNull(SpecialGroupAvatars.passenger(2, 0));

        final PassengerAvatars.PassengerAvatar avatar = PassengerAvatars.passenger(2, 0, POOL);
        assertTrue(POOL.contains(avatar.icon()));
        assertEquals(avatar, PassengerAvatars.passenger(2, 0, POOL));
    }

    @Test
    public void everyAvatarOfTheTableIsAnIconAndTwoCapitalLetters() {
        for (int journeyId = 1; journeyId <= 100; journeyId++) {
            final List<PassengerAvatars.PassengerAvatar> group = SpecialGroupAvatars.of(journeyId);
            if (group == null) {
                continue;
            }
            assertTrue(!group.isEmpty(), "journey " + journeyId);
            for (final PassengerAvatars.PassengerAvatar avatar : group) {
                assertTrue(Pattern.matches("[A-Z]{2}", avatar.initials()), "journey " + journeyId + " " + avatar);
                assertTrue(avatar.icon().endsWith(".png"), "journey " + journeyId + " " + avatar);
            }
        }
    }

    @Test
    public void theListOfTheTableCannotBeChanged() {
        final List<PassengerAvatars.PassengerAvatar> group = SpecialGroupAvatars.of(1);

        assertThrows(UnsupportedOperationException.class, () -> group.add(new PassengerAvatars.PassengerAvatar("XX", "x.png")));
    }
}
