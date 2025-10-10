package net.simforge.airways2.tools;

public class Tools {
    public static int random(final int min, final int max) {
        return min + (int) Math.round(Math.random() * (max - min));
    }
}
