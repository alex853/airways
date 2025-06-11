package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.SparseMatrixStorage;

import static com.google.common.base.Preconditions.checkArgument;

public class Airport2AirportDailyFlightStats {
    private final SparseMatrixStorage<FlightStats> storage = SparseMatrixStorage
            .<FlightStats>builder(DataType.Unsigned16bit, DataType.Unsigned16bit)
            .name("airport2airport-daily-flight-stats")
            .withInstantiator(FlightStats::new)
            .withDataField(DataField.of(DataType.Unsigned8bit)) // today
            .withDataField(DataField.of(DataType.Unsigned8bit)) // today-1
            .withDataField(DataField.of(DataType.Unsigned8bit)) // today-2
            .withDataField(DataField.of(DataType.Unsigned8bit)) // today-3
            .withDataField(DataField.of(DataType.Unsigned8bit)) // today-4
            .withDataField(DataField.of(DataType.Unsigned8bit)) // today-5
            .withDataField(DataField.of(DataType.Unsigned8bit)) // today-6
            .withDataField(DataField.of(DataType.Unsigned8bit)) // today-7
            .build();

    private final DataField todayCountField = storage.getDataField(2);

    public void incrementTodayCount(final int airport1Id, final int airport2Id) {
        checkArgument(airport1Id > 0);
        checkArgument(airport2Id > 0);

        final FlightStats flightStats = storage
                .byIds(airport1Id, airport2Id)
                .orElseGet(() -> storage.addRecord(airport1Id, airport2Id));
        flightStats.incrementTodayCount();
    }

    public void rotateCountersAtMidnight() {
        throw new UnsupportedOperationException("AirportToAirportDailyFlightStats.shiftCountersAtMidnight not implemented");
    }

    public class FlightStats {
        private final int airport1Id;
        private final int airport2Id;

        private FlightStats(final int airport1Id, final int airport2Id) {
            this.airport1Id = airport1Id;
            this.airport2Id = airport2Id;
        }

        public void incrementTodayCount() {
            final int current = storage.getAsInt(airport1Id, airport2Id, todayCountField);
            storage.set(airport1Id, airport2Id, todayCountField, Math.min(current + 1, 255));
        }
    }
}
