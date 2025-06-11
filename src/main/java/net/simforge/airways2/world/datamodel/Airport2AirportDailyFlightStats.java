package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
    private final DataField todayM1CountField = storage.getDataField(3);
    private final DataField todayM2CountField = storage.getDataField(4);
    private final DataField todayM3CountField = storage.getDataField(5);
    private final DataField todayM4CountField = storage.getDataField(6);
    private final DataField todayM5CountField = storage.getDataField(7);
    private final DataField todayM6CountField = storage.getDataField(8);
    private final DataField todayM7CountField = storage.getDataField(9);

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

    public void rotateCountsAtMidnight() {
        log.info("midnight count rotation - started");

        final List<FlightStats> toBeRemoved = new ArrayList<>();

        final Collection<FlightStats> all = storage.all();
        all.forEach(c -> {
            c.rotateCountsAtMidnight();
            if (c.getTotalCount() == 0) {
                toBeRemoved.add(c);
            }
        });

        toBeRemoved.forEach(c -> storage.deleteRecord(c.id));

        log.info("midnight count rotation - DONE, processed {} records, removed {} records", all.size(), toBeRemoved.size());
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

        public int getTotalCount() {
            return storage.getAsInt(id, todayCountField)
                    + storage.getAsInt(id, todayM1CountField)
                    + storage.getAsInt(id, todayM2CountField)
                    + storage.getAsInt(id, todayM3CountField)
                    + storage.getAsInt(id, todayM4CountField)
                    + storage.getAsInt(id, todayM5CountField)
                    + storage.getAsInt(id, todayM6CountField)
                    + storage.getAsInt(id, todayM7CountField);
        }

        void rotateCountsAtMidnight() {
            storage.set(id, todayM7CountField, storage.getAsInt(id, todayM6CountField));
            storage.set(id, todayM6CountField, storage.getAsInt(id, todayM5CountField));
            storage.set(id, todayM5CountField, storage.getAsInt(id, todayM4CountField));
            storage.set(id, todayM4CountField, storage.getAsInt(id, todayM3CountField));
            storage.set(id, todayM3CountField, storage.getAsInt(id, todayM2CountField));
            storage.set(id, todayM2CountField, storage.getAsInt(id, todayM1CountField));
            storage.set(id, todayM1CountField, storage.getAsInt(id, todayCountField));
        }
    }
}
