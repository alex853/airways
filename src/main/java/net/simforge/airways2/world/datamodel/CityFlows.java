package net.simforge.airways2.world.datamodel;

import lombok.Getter;
import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.DataTypeUtils;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.processors.CityFlowHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkState;

public class CityFlows {
    private static final Logger log = LoggerFactory.getLogger(CityFlows.class);

    private final Storage<Flow> storage = Storage.<Flow>builder()
            .name("city_flows")
            .withInstantiator(Flow::new)
            .withIdOf(DataType.Unsigned24bit)
            // city flow id equals city id
            .withDataField(DataField.of(DataType.Unsigned8bit)) // status, unused at the moment
            .withDataField(DataField.of(DataType.Signed32bit)) // lastRedistributionTime
            .withDataField(DataField.of(DataType.Unsigned16bit)) // attraction factor, 1.0 corresponds to 1000, max attraction factor is 65x
            .withDataField(DataField.of(DataType.Unsigned16bit)) // mobility factor, 1.0 corresponds to 1000, max mobility factor is 65x
            .build();

    private final DataField statusField = storage.getDataField(0);
    private final DataField lastRedistributionTimeField = storage.getDataField(1);
    private final DataField attractionFactorField = storage.getDataField(2);
    private final DataField mobilityFactorField = storage.getDataField(3);

    private final World world;

    public CityFlows(final World world) {
        this.world = world;
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Stream<Flow> all() {
        return storage.all();
    }

    public Optional<Flow> nextForRedistribution(final int worldTime) {
        // todo ak3 skip it if status is disabled
        return storage.findFirst1(recordId -> readLastRedistributionTime(recordId) + CityFlowHelper.REDISTRIBUTION_PERIOD <= worldTime);
    }

    public Optional<Flow> byCityFlow(final City2CityFlows.Flow c2cFlow) {
        return storage.byId(c2cFlow.getFromCityId());
    }

    public Optional<Flow> byCityId(int cityId) {
        return storage.byId(cityId);
    }

    public void createMissingCityFlows() {
        final List<Integer> cityIds = world.cities().all().map(Cities.City::getId).sorted().toList();

        for (final int cityId : cityIds) {
            final Optional<Flow> flow = storage.byId(cityId);
            if (flow.isPresent()) {
                continue;
            }

            createFlow(cityId);
            log.info("city flow for '{}' city created", world.cities().byId(cityId).orElseThrow().getName());

            final Optional<Flow> newFlow = storage.byId(cityId);
            checkState(newFlow.isPresent(), "flow should exist here!");
        }
    }

    private void createFlow(final int cityId) {
        final int flowId = storage.addRecord();
        checkState(flowId == cityId);
        storage.set(flowId, statusField, 0);
        storage.set(flowId, lastRedistributionTimeField, 0);
        storage.set(flowId, attractionFactorField, DataTypeUtils.floatToU16When1to1000(CityFlowHelper.DEFAULT_ATTRACTION_FACTOR));
        storage.set(flowId, mobilityFactorField, DataTypeUtils.floatToU16When1to1000(CityFlowHelper.DEFAULT_MOBILITY_FACTOR));
    }

    @Getter
    public class Flow {
        private final int id;

        private Flow(final int id) {
            this.id = id;
        }

        public int getLastRedistributionTime() {
            return readLastRedistributionTime(id);
        }

        public void setLastRedistributionTime(final int lastRedistributionTime) {
            storage.set(id, lastRedistributionTimeField, lastRedistributionTime);
        }

        public float getAttractionFactor() {
            return DataTypeUtils.floatFromU16When1to1000(storage.getAsInt(id, attractionFactorField));
        }

        public void setAttractionFactor(float attractionFactor) {
            storage.set(id, attractionFactorField, DataTypeUtils.floatToU16When1to1000(attractionFactor));
        }

        public float getMobilityFactor() {
            return DataTypeUtils.floatFromU16When1to1000(storage.getAsInt(id, mobilityFactorField));
        }

        public void setMobilityFactor(float mobilityFactor) {
            storage.set(id, mobilityFactorField, DataTypeUtils.floatToU16When1to1000(mobilityFactor));
        }
    }

    private int readLastRedistributionTime(int recordId) {
        return storage.getAsInt(recordId, lastRedistributionTimeField);
    }
}
