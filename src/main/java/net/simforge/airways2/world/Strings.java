package net.simforge.airways2.world;

import java.util.Arrays;

public class Strings {
    private int[] indices = new int[1];
    private byte[] strings = new byte[0];

    void save() {
        throw new UnsupportedOperationException();
    }

    public String byId(final int id) {
        throw new UnsupportedOperationException();
    }

    public int findOrAdd(final String name) {
        final byte[] target = name.getBytes();
        for (int id = 1; id < indices.length; id++) {
            final int startingIndex = indices[id];
            final int length = Byte.toUnsignedInt(strings[startingIndex]);
            if (length != target.length) {
                continue;
            }
            final int result = Arrays.compare(
                    target, 0, target.length,
                    strings, startingIndex+1, target.length);
            if (result == 0) {
                return id;
            }
        }

        final int[] newIndices = new int[indices.length + 1];
        System.arraycopy(indices, 0, newIndices, 0, indices.length);
        final byte[] newStrings = new byte[strings.length + 1 + target.length];
        System.arraycopy(strings, 0, newStrings, 0, strings.length);

        final int newId = newIndices.length-1;
        final int newStartingPoint = strings.length;
        newIndices[newId] = newStartingPoint;
        newStrings[newStartingPoint] = (byte) target.length; // todo ak check strings longer than 128 bytes
        System.arraycopy(target, 0, newStrings, newStartingPoint+1, target.length);

        this.indices = newIndices;
        this.strings = newStrings;

        return newId;
    }
}
