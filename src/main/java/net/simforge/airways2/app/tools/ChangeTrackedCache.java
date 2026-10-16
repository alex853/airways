package net.simforge.airways2.app.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A value which is built from the data of a few storages and is rebuilt only when the data changes.
 * <p>
 * The cache does not know what the data is: it is given a supplier of the change stamps of the storages the value depends on
 * (see {@code Storage#getLastChangedAtMillis}, they can be read from any thread without any lock) and a builder of the value.
 * While the stamps stay the same the cached value is returned without any lock and without any work.
 * When they differ from the ones the value was built with, the value is rebuilt, by one thread, the others wait for it.
 * <p>
 * The stamps are read <b>before</b> the value is built: if the data changes during the build, the value is stamped with the older
 * stamps, so it is rebuilt once more at the next call and an outdated value is never kept.
 */
public class ChangeTrackedCache<V> {
    private static final Logger log = LoggerFactory.getLogger(ChangeTrackedCache.class);

    private record Snapshot<V>(long[] stamps, V value) {
    }

    private final String name;
    private final Supplier<long[]> stampsSupplier;
    private final Supplier<V> builder;

    private volatile Snapshot<V> snapshot;
    private volatile int rebuildCount;

    /**
     * @param name           is used in logs
     * @param stampsSupplier returns the current change stamps of the storages the value depends on, it is called on every
     *                       {@link #get()}, so it must be cheap and must not take any lock
     * @param builder        builds the value, it may take any lock it needs
     */
    public ChangeTrackedCache(final String name, final Supplier<long[]> stampsSupplier, final Supplier<V> builder) {
        this.name = checkNotNull(name);
        this.stampsSupplier = checkNotNull(stampsSupplier);
        this.builder = checkNotNull(builder);
    }

    public V get() {
        final Snapshot<V> current = snapshot;
        if (current != null && Arrays.equals(current.stamps(), stampsSupplier.get())) {
            return current.value();
        }

        synchronized (this) {
            // somebody could have rebuilt the value while this thread was waiting
            final long[] stamps = stampsSupplier.get();
            final Snapshot<V> rechecked = snapshot;
            if (rechecked != null && Arrays.equals(rechecked.stamps(), stamps)) {
                return rechecked.value();
            }

            final long startedAt = System.nanoTime();
            final V value = builder.get();
            snapshot = new Snapshot<>(stamps, value);
            rebuildCount++;
            log.info("{} - rebuilt in {} ms, stamps {}", name, (System.nanoTime() - startedAt) / 1_000_000, Arrays.toString(stamps));

            return value;
        }
    }

    /**
     * How many times the value has been built, it is intended for tests and statistics.
     */
    public int getRebuildCount() {
        return rebuildCount;
    }
}
