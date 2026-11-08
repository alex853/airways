package net.simforge.airways2.world.computations;

import net.simforge.airways2.world.computations.PassengerAvatars.PassengerAvatar;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The avatars of the passengers come from the id of the journey only and never change: the values are fixed here, they were computed by an independent
 * implementation of the same algorithm (SplitMix64 and FNV-1a), see PassengerAvatars.
 */
class PassengerAvatarsTest {
    private static final List<String> POOL = List.of("a.png", "b.png", "c.png", "d.png", "e.png", "f.png", "g.png", "h.png");

    private static PassengerAvatar av(final String initials, final String icon) {
        return new PassengerAvatar(initials, icon);
    }

    @Test
    void theValuesAreFixedForever() {
        assertEquals(List.of(av("PJ", "a.png"), av("FB", "f.png"), av("TK", "h.png")), PassengerAvatars.of(1, 3, POOL));
        assertEquals(List.of(av("PU", "c.png"), av("QK", "d.png"), av("FC", "h.png")), PassengerAvatars.of(2, 3, POOL));
        assertEquals(List.of(av("RI", "g.png"), av("QA", "a.png"), av("DU", "h.png")), PassengerAvatars.of(12345, 3, POOL));
        assertEquals(List.of(av("QH", "f.png"), av("QH", "c.png"), av("SV", "d.png")), PassengerAvatars.of(600000, 3, POOL));
    }

    @Test
    void theBitsMixAsTheReferenceSplitMixDoes() {
        // the first output of SplitMix64 with the seed 0 is the known value 0xE220A8397B1DCDAF
        assertEquals(0xE220A8397B1DCDAFL, PassengerAvatars.mix(0));
        assertEquals(-2152535657050944081L, PassengerAvatars.mix(0));
        assertEquals(-7995527694508729151L, PassengerAvatars.mix(1));
        assertEquals(-6037484556526696461L, PassengerAvatars.hash("user.png"));
    }

    @Test
    void theSeatKeysAreFixedForever() {
        assertEquals(-5619637915455897396L, PassengerAvatars.seatKey(1, 1, 0));
        assertEquals(-8921502405250398175L, PassengerAvatars.seatKey(57834, 12345, 3));
    }

    @Test
    void withoutIconsThePictureIsTheFallback() {
        assertEquals(av("QU", "user.png"), PassengerAvatars.passenger(7, 0, List.of()));
    }

    @Test
    void theSameJourneyAlwaysGivesTheSameList() {
        assertEquals(PassengerAvatars.of(777, 6, POOL), PassengerAvatars.of(777, 6, POOL));
        assertEquals(PassengerAvatars.of(777, 6, POOL), PassengerAvatars.of(777, 6, new ArrayList<>(POOL)));
    }

    @Test
    void thePoolInAnotherOrderGivesTheSamePictures() {
        final List<String> reversed = new ArrayList<>(POOL);
        java.util.Collections.reverse(reversed);

        assertEquals(PassengerAvatars.of(777, 20, POOL), PassengerAvatars.of(777, 20, reversed));
    }

    @Test
    void aPassengerDoesNotDependOnTheSizeOfTheGroup() {
        final List<PassengerAvatar> four = PassengerAvatars.of(555, 4, POOL);
        final List<PassengerAvatar> nine = PassengerAvatars.of(555, 9, POOL);

        assertEquals(4, four.size());
        assertEquals(9, nine.size());
        assertEquals(four, nine.subList(0, 4));
        assertEquals(nine.get(7), PassengerAvatars.passenger(555, 7, POOL));
    }

    @Test
    void theInitialsAreTwoCapitalEnglishLetters() {
        for (int journeyId = 1; journeyId <= 2000; journeyId++) {
            for (final PassengerAvatar avatar : PassengerAvatars.of(journeyId, 3, POOL)) {
                assertTrue(avatar.initials().matches("[A-Z]{2}"), avatar.initials());
            }
        }
    }

    @Test
    void theIconIsOneOfThePoolAndIsSpreadAmongAllOfIt() {
        final Map<String, Integer> counts = new HashMap<>();
        final Map<String, Integer> letters = new HashMap<>();
        for (int journeyId = 1; journeyId <= 5000; journeyId++) {
            for (final PassengerAvatar avatar : PassengerAvatars.of(journeyId, 4, POOL)) {
                assertTrue(POOL.contains(avatar.icon()), avatar.icon());
                counts.merge(avatar.icon(), 1, Integer::sum);
                letters.merge(avatar.initials().substring(0, 1), 1, Integer::sum);
            }
        }
        // 20000 passengers, 8 icons: every one about 2500 times
        assertEquals(POOL.size(), counts.size());
        counts.values().forEach(count -> assertTrue(count > 2200 && count < 2800, "count " + count));
        // 20000 first letters over 26 letters: about 770 each
        assertEquals(26, letters.size());
        letters.values().forEach(count -> assertTrue(count > 600 && count < 940, "letter count " + count));
    }

    @Test
    void aNewIconInThePoolChangesOnlyThePassengersWhoGetTheNewOne() {
        final List<String> bigger = new ArrayList<>(POOL);
        bigger.add("new.png");
        int changed = 0;
        int total = 0;
        for (int journeyId = 1; journeyId <= 3000; journeyId++) {
            final PassengerAvatar before = PassengerAvatars.passenger(journeyId, 0, POOL);
            final PassengerAvatar after = PassengerAvatars.passenger(journeyId, 0, bigger);
            total++;
            if (!before.equals(after)) {
                changed++;
                assertEquals("new.png", after.icon());
                assertEquals(before.initials(), after.initials());
            }
        }
        // about one of nine gets the new icon
        assertTrue(changed > total / 12 && changed < total / 7, "changed " + changed + " of " + total);
    }

    @Test
    void anIconWhichIsTakenOutOfThePoolChangesOnlyThePassengersWhoHadIt() {
        final List<String> smaller = new ArrayList<>(POOL);
        smaller.remove("c.png");
        for (int journeyId = 1; journeyId <= 3000; journeyId++) {
            final PassengerAvatar before = PassengerAvatars.passenger(journeyId, 0, POOL);
            final PassengerAvatar after = PassengerAvatars.passenger(journeyId, 0, smaller);
            if (!before.icon().equals("c.png")) {
                assertEquals(before, after);
            } else {
                assertNotEquals("c.png", after.icon());
            }
        }
    }

    @Test
    void theSeatOrderMixesTheJourneysAndIsTheSameEveryTime() {
        // 60 journeys of 4 passengers
        record Seat(int journeyId, int index, long key) {
        }
        final List<Seat> seats = new ArrayList<>();
        for (int journeyId = 1000; journeyId < 1060; journeyId++) {
            for (int index = 0; index < 4; index++) {
                seats.add(new Seat(journeyId, index, PassengerAvatars.seatKey(42, journeyId, index)));
            }
        }
        seats.sort(java.util.Comparator.comparingLong(Seat::key));

        // neighbours of one journey: by chance about 3 of 240 places, never anywhere near the 180 of the order by the journeys
        int neighbours = 0;
        for (int i = 1; i < seats.size(); i++) {
            if (seats.get(i).journeyId() == seats.get(i - 1).journeyId()) {
                neighbours++;
            }
        }
        assertTrue(neighbours < 20, "neighbours of one journey " + neighbours);
        // the same keys again
        assertEquals(PassengerAvatars.seatKey(42, 1000, 2), seats.stream().filter(s -> s.journeyId() == 1000 && s.index() == 2).findFirst().orElseThrow().key());
    }

    @Test
    void theSeatOrderOfTheOnesWhoAreOnBoardDoesNotDependOnTheOthers() {
        // the order of two passengers is the order of their keys, whoever else is in the row
        final long a = PassengerAvatars.seatKey(9, 100, 0);
        final long b = PassengerAvatars.seatKey(9, 101, 0);
        assertEquals(Long.compare(a, b), Long.compare(PassengerAvatars.seatKey(9, 100, 0), PassengerAvatars.seatKey(9, 101, 0)));
        // another flight has another order
        boolean differs = false;
        for (int journeyId = 100; journeyId < 140 && !differs; journeyId++) {
            differs = Long.compare(PassengerAvatars.seatKey(9, journeyId, 0), PassengerAvatars.seatKey(9, journeyId + 1, 0))
                    != Long.compare(PassengerAvatars.seatKey(10, journeyId, 0), PassengerAvatars.seatKey(10, journeyId + 1, 0));
        }
        assertTrue(differs);
    }

    @Test
    void aGroupWhichIsTooBigAndANegativeNumberAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> PassengerAvatars.of(1, -1, POOL));
        assertThrows(IllegalArgumentException.class, () -> PassengerAvatars.of(1, 5000, POOL));
        assertThrows(IllegalArgumentException.class, () -> PassengerAvatars.passenger(1, -1, POOL));
        assertThrows(IllegalArgumentException.class, () -> PassengerAvatars.seatKey(1, 1, 4096));
        assertTrue(PassengerAvatars.of(1, 0, POOL).isEmpty());
    }
}
