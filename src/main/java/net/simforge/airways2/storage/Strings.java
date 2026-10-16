package net.simforge.airways2.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * Strings of the world, they are addressed by ids starting from 1. A string is stored in UTF-8, its length (up to 255
 * bytes) is stored in one unsigned byte before the string.
 */
public class Strings {
    private static final int MAX_STRING_LENGTH_IN_BYTES = 255;

    private int[] indices = new int[1];
    private byte[] strings = new byte[0];

    public Strings() {
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        final Path indicesPath = rootPath.resolve("string-index");
        final Path dataPath = rootPath.resolve("string-data");

        if (!Files.exists(indicesPath) || !Files.exists(dataPath)) {
            indices = new int[1];
            strings = new byte[0];
            return;
        }

        final IntBuffer intBuffer = ByteBuffer.wrap(Files.readAllBytes(indicesPath)).asIntBuffer();
        indices = new int[intBuffer.remaining()];
        intBuffer.get(indices);
        strings = Files.readAllBytes(dataPath);
    }

    public void save(final Path rootPath) throws IOException {
        final Path indicesPath = rootPath.resolve("string-index");
        final Path dataPath = rootPath.resolve("string-data");

        final ByteBuffer byteBuffer = ByteBuffer.allocate(indices.length * 4);
        byteBuffer.asIntBuffer().put(indices);

        if (!Files.exists(rootPath)) {
            Files.createDirectories(rootPath);
        }

        Files.write(indicesPath, byteBuffer.array(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.write(dataPath, strings, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    public String byId(final int id) {
        final int startingIndex = indices[id];
        final int length = Byte.toUnsignedInt(strings[startingIndex]);
        return new String(strings, startingIndex + 1, length, StandardCharsets.UTF_8);
    }

    public int find(String str) {
        final byte[] target = str.getBytes(StandardCharsets.UTF_8);
        for (int id = 1; id < indices.length; id++) {
            final int startingIndex = indices[id];
            final int length = Byte.toUnsignedInt(strings[startingIndex]);
            if (length != target.length) {
                continue;
            }
            final int result = Arrays.compare(
                    target, 0, target.length,
                    strings, startingIndex+1, startingIndex+1+target.length);
            if (result == 0) {
                return id;
            }
        }
        return -1;
    }

    public int findOrAdd(String str) {
        int foundId = find(str);
        if (foundId != -1) {
            return foundId;
        }

        final byte[] target = str.getBytes(StandardCharsets.UTF_8);
        // the length is stored in one unsigned byte, a longer string would get a wrong length
        if (target.length > MAX_STRING_LENGTH_IN_BYTES) {
            throw new IllegalArgumentException("String is too long, " + target.length + " bytes while max length is " + MAX_STRING_LENGTH_IN_BYTES + " bytes");
        }

        final int[] newIndices = new int[indices.length + 1];
        System.arraycopy(indices, 0, newIndices, 0, indices.length);
        final byte[] newStrings = new byte[strings.length + 1 + target.length];
        System.arraycopy(strings, 0, newStrings, 0, strings.length);

        final int newId = newIndices.length-1;
        final int newStartingPoint = strings.length;
        newIndices[newId] = newStartingPoint;
        newStrings[newStartingPoint] = (byte) target.length; // unsigned, up to 255, see Byte.toUnsignedInt in byId
        System.arraycopy(target, 0, newStrings, newStartingPoint+1, target.length);

        this.indices = newIndices;
        this.strings = newStrings;

        return newId;
    }
}
