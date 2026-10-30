package net.simforge.airways2.world.datamodel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransportFlightExtrasTest {

    @Test
    void aTransportFlightHasNoExtraRecordUntilItNeedsOne() {
        final TransportFlightExtras extras = new TransportFlightExtras();

        assertTrue(extras.byTransportFlightId(10).isEmpty());
        assertEquals(0, extras.all().count());
    }

    @Test
    void anExtraRecordIsFoundByItsTransportFlight() {
        final TransportFlightExtras extras = new TransportFlightExtras();

        final TransportFlightExtras.Extra extra = extras.create(10);
        extras.create(20);

        assertEquals(10, extra.getTransportFlightId());
        assertEquals(extra.getId(), extras.byTransportFlightId(10).orElseThrow().getId());
        assertEquals(20, extras.byTransportFlightId(20).orElseThrow().getTransportFlightId());
        assertEquals(2, extras.all().count());
    }

    @Test
    void aTransportFlightHasOnlyOneExtraRecord() {
        final TransportFlightExtras extras = new TransportFlightExtras();
        extras.create(10);

        assertThrows(IllegalArgumentException.class, () -> extras.create(10));
        assertThrows(IllegalArgumentException.class, () -> extras.create(0));
        assertEquals(1, extras.all().count());
    }

    @Test
    void getOrCreateGivesTheSameRecordTheNextTime() {
        final TransportFlightExtras extras = new TransportFlightExtras();

        final int first = extras.getOrCreate(10).getId();
        final int second = extras.getOrCreate(10).getId();

        assertEquals(first, second);
        assertEquals(1, extras.all().count());
    }

    @Test
    void theRecordIsDeletedWithItsTransportFlight() {
        final TransportFlightExtras extras = new TransportFlightExtras();
        extras.create(10);
        extras.create(20);

        assertTrue(extras.deleteByTransportFlightId(10));

        assertTrue(extras.byTransportFlightId(10).isEmpty());
        assertTrue(extras.byTransportFlightId(20).isPresent());
        assertEquals(1, extras.all().count());
        assertFalse(extras.deleteByTransportFlightId(10));
        // the transport flight can get a record again
        extras.create(10);
        assertTrue(extras.byTransportFlightId(10).isPresent());
    }

    @Test
    void theIncomeIsZeroUntilItIsSetAndKeptInThreeBytes() {
        final TransportFlightExtras extras = new TransportFlightExtras();
        final TransportFlightExtras.Extra extra = extras.create(10);

        assertEquals(0, extra.getIncome());
        extra.setIncome(12_345);
        assertEquals(12_345, extra.getIncome());
        extra.setIncome(TransportFlightExtras.MAX_INCOME);
        assertEquals(16_777_215, extra.getIncome());
        assertThrows(IllegalArgumentException.class, () -> extra.setIncome(-1));
        assertThrows(IllegalArgumentException.class, () -> extra.setIncome(TransportFlightExtras.MAX_INCOME + 1));
        assertEquals(16_777_215, extra.getIncome());
    }

    @Test
    void aRecordIs32BytesAndTheRecordsSurviveSaveAndLoad(@TempDir final Path dir) throws IOException {
        final TransportFlightExtras extras = new TransportFlightExtras();
        final TransportFlightExtras.Extra extra = extras.create(10);
        extra.setIncome(777_000);

        extras.save(dir);
        // 1 byte of the record deletion status, 3 bytes of the transport flight, 3 bytes of the income, 25 bytes of the reserve
        assertEquals(32, Files.readAllBytes(dir.resolve("transport-flight-extra")).length);

        final TransportFlightExtras loaded = new TransportFlightExtras();
        loaded.loadIfExists(dir);

        final TransportFlightExtras.Extra restored = loaded.byTransportFlightId(10).orElseThrow();
        assertEquals(extra.getId(), restored.getId());
        assertEquals(10, restored.getTransportFlightId());
        assertEquals(777_000, restored.getIncome());
        assertEquals(1, loaded.all().count());
    }
}
