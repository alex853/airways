package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.DataTypeUtils;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.world.processors.CityFlowHelper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

public class City2CityFlows {
    private final Storage<Flow> storage = Storage.<Flow>builder()
            .name("city2city_flows")
            .withInstantiator(Flow::new)
            .withIdOf(DataType.Unsigned24bit)
            .withDataField(DataField.of(DataType.Unsigned16bit)) // fromCityId
            .withDataField(DataField.of(DataType.Unsigned16bit)) // toCityId
            .withDataField(DataField.of(DataType.Unsigned8bit)) // status
            .withDataField(DataField.of(DataType.Signed32bit)) // heartbeatTime
            .withDataField(DataField.of(DataType.Unsigned16bit)) // flow fraction [0.0000 (0) .. 1.0000 (65535)]
            .withDataField(DataField.of(DataType.Unsigned16bit)) // success rate [0.0000 (0) .. 1.0000 (65535)]
            .withDataField(DataField.of(DataType.Unsigned8bit)) // nextGroupSize [1 .. 255]
            .withDataField(DataField.of(DataType.Unsigned16bit)) // accumulatedFlow [0.00 - 255.00], 1st byte - integer part, 2nd byte - fractional
            .withDataField(DataField.of(DataType.Signed32bit)) // accumulatedFlowTime
            .build();

    private final DataField fromCityIdField = storage.getDataField(0);
    private final DataField toCityIdField = storage.getDataField(1);
    private final DataField statusField = storage.getDataField(2);
    private final DataField heartbeatTimeField = storage.getDataField(3);
    private final DataField flowFractionField = storage.getDataField(4);
    private final DataField successRateField = storage.getDataField(5);
    private final DataField nextGroupSizeField = storage.getDataField(6);
    private final DataField accumulatedFlowField = storage.getDataField(7);
    private final DataField accumulatedFlowTimeField = storage.getDataField(8);

    private static final int STATUS_INACTIVE = 0;
    private static final int STATUS_ACTIVE = 1;

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Collection<Flow> allFromCityId(final int fromCityId) {
        try (final Timing.Timer ignored = Timing.label("City2CityFlows - allFromCityId")) {
            return storage.filter(f -> f.getFromCityId() == fromCityId); // todo ak2 migrate to filter1
        }
    }

    public Optional<Flow> getFromCityIdToCityId(final int fromCityId, final int toCityId) {
        checkArgument(fromCityId >= 1);
        checkArgument(toCityId >= 1);

        return storage.findFirst1(id -> storage.getAsInt(id, fromCityIdField) == fromCityId && storage.getAsInt(id, toCityIdField) == toCityId);
    }

    public Flow createInactive(final int fromCityId, final int toCityId) {
        checkArgument(allFromCityId(fromCityId).stream().filter(f -> f.getToCityId() == toCityId).findFirst().isEmpty());

        final int id = storage.addRecord();
        final Flow flow = new Flow(id);
        storage.set(id, fromCityIdField, fromCityId);
        storage.set(id, toCityIdField, toCityId);
        flow.setActive(false);
        flow.setFlowFraction(0.0f);
        flow.setSuccessRate(CityFlowHelper.STARTING_SUCCESS_RATE);
        return flow;
    }

    public Optional<Flow> nextForHeartbeat(final int worldTime) {
        try (final Timing.Timer ignored = Timing.label("City2CityFlows - nextForHeartbeat")) {
            return storage.findFirst1(storage.nextForHeartbeatCondition(heartbeatTimeField, worldTime));
        }
    }

    public class Flow {
        private final int id;

        private Flow(final int id) {
            this.id = id;
        }

        public int getFromCityId() {
            return storage.getAsInt(id, fromCityIdField);
        }

        public int getToCityId() {
            return storage.getAsInt(id, toCityIdField);
        }

        public boolean isActive() {
            return storage.getAsInt(id, statusField) == STATUS_ACTIVE;
        }

        public void setActive(final boolean active) {
            storage.set(id, statusField, active ? STATUS_ACTIVE : STATUS_INACTIVE);
        }

        public int getHeartbeatTime() {
            return storage.getAsInt(id, heartbeatTimeField);
        }

        public void setHeartbeatTime(final int heartbeatTime) {
            storage.set(id, heartbeatTimeField, heartbeatTime);
        }

        public float getFlowFraction() {
            return DataTypeUtils.floatFromU16When1to65535(storage.getAsInt(id, flowFractionField));
        }
        
        public void setFlowFraction(final float flowFraction) {
            storage.set(id, flowFractionField, DataTypeUtils.floatToU16When1to65535(flowFraction));
        }

        public float getSuccessRate() {
            return DataTypeUtils.floatFromU16When1to65535(storage.getAsInt(id, successRateField));
        }
        
        public void setSuccessRate(final float successRate) {
            storage.set(id, successRateField, DataTypeUtils.floatToU16When1to65535(CityFlowHelper.boundSuccessRate(successRate)));
        }

        public int getNextGroupSize() {
            return storage.getAsInt(id, nextGroupSizeField);
        }
        
        public void setNextGroupSize(final int nextGroupSize) {
            storage.set(id, nextGroupSizeField, nextGroupSize);
        }
        
        public float getAccumulatedFlow() {
            return DataTypeUtils.floatFromU16When1byteInt1byteFraction(storage.getAsInt(id, accumulatedFlowField));
        }

        public void setAccumulatedFlow(final float accumulatedFlow) {
            storage.set(id, accumulatedFlowField, DataTypeUtils.floatToU16When1byteInt1byteFraction(accumulatedFlow));
        }

        public int getAccumulatedFlowTime() {
            return storage.getAsInt(id, accumulatedFlowTimeField);
        }
        
        public void setAccumulatedFlowTime(final int accumulatedFlowTime) {
            storage.set(id, accumulatedFlowTimeField, accumulatedFlowTime);
        }
    }
}
