package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

public class CityFlows {
    private final Storage<Flow> storage = Storage.<Flow>builder()
            .name("city_flows")
            .withInstantiator(Flow::new)
            .withIdOf(DataType.Unsigned24bit)
            .withDataField(DataField.of(DataType.Unsigned16bit)) // cityId
            .withDataField(DataField.of(DataType.Unsigned8bit)) // status
            .withDataField(DataField.of(DataType.Signed32bit)) // heartbeatTime
            .withDataField(DataField.of(DataType.Signed32bit)) // lastRedistributionTime
            .withDataField(DataField.of(DataType.Unsigned16bit)) // attraction, 1.0 corresponds to 100
            // unitsThreshold - skipped, not required
            // defaultAvailability - skipped, not required
            .withDataField(DataField.of(DataType.Unsigned16bit)) // mobility, 1.0 corresponds to 100
            .build();

    // todo rework all below
    private final DataField cityId = storage.getDataField(0);
    private final DataField status = storage.getDataField(1);

    public class Flow {
        public Flow(final int id) {

        }
    }
}
