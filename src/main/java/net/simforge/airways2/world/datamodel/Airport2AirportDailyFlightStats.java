package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

import static com.google.common.base.Preconditions.checkArgument;

public class Airport2AirportDailyFlightStats {
    private static final Logger log = LoggerFactory.getLogger(Airport2AirportDailyFlightStats.class);

    private final Storage<FlightStats> storage = Storage.<FlightStats>builder()
            .name("airport2airport-daily-flight-stats")
            .withInstantiator(FlightStats::new)
            .withIdOf(DataType.Signed32bit)
            .withDataField(DataField.of(DataType.Unsigned16bit)) // fromAirportId
            .withDataField(DataField.of(DataType.Unsigned16bit)) // toAirportId
            .withDataField(DataField.of(DataType.Unsigned8bit)) // today
            .withDataField(DataField.of(DataType.Unsigned8bit)) // today-1
            .withDataField(DataField.of(DataType.Unsigned8bit)) // today-2
            .withDataField(DataField.of(DataType.Unsigned8bit)) // today-3
            .withDataField(DataField.of(DataType.Unsigned8bit)) // today-4
            .withDataField(DataField.of(DataType.Unsigned8bit)) // today-5
            .withDataField(DataField.of(DataType.Unsigned8bit)) // today-6
            .withDataField(DataField.of(DataType.Unsigned8bit)) // today-7
            .build();

    private final DataField fromAirportIdField = storage.getDataField(0);
    private final DataField toAirportIdField = storage.getDataField(1);
    private final DataField todayCountField = storage.getDataField(2);

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public void incrementTodayCount(final int fromAirportId, final int toAirportId) {
        checkArgument(fromAirportId > 0);
        checkArgument(toAirportId > 0);

        final FlightStats flightStats = storage
                .findFirst(e -> e.getFromAirportId() == fromAirportId && e.getToAirportId() == toAirportId) // todo ak3 indexed access can be put here
                .orElseGet(() -> createFlightStats(fromAirportId, toAirportId));
        flightStats.incrementTodayCount();
    }

    private FlightStats createFlightStats(final int fromAirportId, final int toAirportId) {
        checkArgument(fromAirportId > 0);
        checkArgument(toAirportId > 0);

        final int id = storage.addRecord();
        storage.set(id, fromAirportIdField, fromAirportId);
        storage.set(id, toAirportIdField, toAirportId);

        log.info("daily flight stats {} -> {} - created", fromAirportId, toAirportId);

        return new FlightStats(id);
    }

    public void rotateCountersAtMidnight() {
        throw new UnsupportedOperationException("AirportToAirportDailyFlightStats.shiftCountersAtMidnight not implemented");
    }

    public class FlightStats {
        private final int id;

        private FlightStats(final int id) {
            this.id = id;
        }

        public int getFromAirportId() {
            return storage.getAsInt(id, fromAirportIdField);
        }

        public int getToAirportId() {
            return storage.getAsInt(id, toAirportIdField);
        }

        public void incrementTodayCount() {
            final int current = storage.getAsInt(id, todayCountField);
            final int newValue = Math.min(current + 1, 255);
            storage.set(id, todayCountField, newValue);
            log.info("daily flight stats {} -> {} - new today count {}", getFromAirportId(), getToAirportId(), newValue);
        }
    }
}
