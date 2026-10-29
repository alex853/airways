package net.simforge.airways2.app.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryReportTest {
    @Test
    void theNumbersAreInMegabytesAndTheUsedOneIsWithinTheMax() {
        assertTrue(MemoryReport.getMaxMB() > 0);
        assertTrue(MemoryReport.getTotalMB() <= MemoryReport.getMaxMB());
        assertTrue(MemoryReport.getUsedMB() <= MemoryReport.getTotalMB());
    }

    @Test
    void printDoesNotFailAndCanBeCalledOften() {
        assertDoesNotThrow(() -> {
            MemoryReport.print();
            MemoryReport.print();
        });
    }
}
