package net.simforge.airways2.tools;

import net.simforge.airways2.world.datamodel.Journeys;

import java.util.List;
import java.util.Optional;

public class Tools {
    public static int random(final int min, final int max) {
        return min + (int) Math.round(Math.random() * (max - min));
    }

    public static <T> Optional<T> random(List<T> list) {
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.get(random(0, list.size()-1)));
    }

    public static int lastDigit(int v) {
        return v % 10;
    }
}
