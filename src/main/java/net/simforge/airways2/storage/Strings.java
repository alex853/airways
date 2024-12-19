package net.simforge.airways2.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

// todo ak2 tests!!!!
public class Strings {
    private final Path rootPath;
    private final Path indicesPath;
    private final Path dataPath;

    private int[] indices = new int[1];
    private byte[] strings = new byte[0];

    private Strings(final Path rootPath) {
        this.rootPath = rootPath;
        this.indicesPath = rootPath.resolve("string-index");
        this.dataPath = rootPath.resolve("string-data");
    }

    public static Strings loadOrCreate(final Path rootPath) throws IOException {
        final Strings result = new Strings(rootPath);
        result.loadIfExists();
        return result;
    }

    private void loadIfExists() throws IOException {
        if (!Files.exists(indicesPath) || !Files.exists(dataPath)) {
            return;
        }

        final IntBuffer intBuffer = ByteBuffer.wrap(Files.readAllBytes(indicesPath)).asIntBuffer();
        indices = new int[intBuffer.remaining()];
        intBuffer.get(indices);
        strings = Files.readAllBytes(dataPath);
    }

    public void save() throws IOException {
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
        return new String(strings, startingIndex + 1, length);
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
                    strings, startingIndex+1, startingIndex+1+target.length);
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
        newStrings[newStartingPoint] = (byte) target.length; // todo ak2 check the case when strings longer than 128 bytes, this can lead to negative values here!
        System.arraycopy(target, 0, newStrings, newStartingPoint+1, target.length);

        this.indices = newIndices;
        this.strings = newStrings;

        return newId;
    }
}
