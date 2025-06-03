package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

public class City2CityFlows {
    private final Storage<Flow> storage = Storage.<Flow>builder()
            .name("city2city_flows")
            .withInstantiator(Flow::new)
            .withIdOf(DataType.Unsigned24bit)
            .withDataField(DataField.of(DataType.Unsigned24bit)) // fromFlowId
            .withDataField(DataField.of(DataType.Unsigned16bit)) // fromCityId
            .withDataField(DataField.of(DataType.Unsigned24bit)) // toFlowId
            .withDataField(DataField.of(DataType.Unsigned16bit)) // toCityId
            .withDataField(DataField.of(DataType.Signed32bit)) // heartbeatTime
            .withDataField(DataField.of(DataType.Unsigned8bit)) // status (was active)
            // units - skipped, not required
            .withDataField(DataField.of(DataType.Unsigned16bit)) // percentage [0.0000 (0) .. 1.0000 (16383)]
            .withDataField(DataField.of(DataType.Unsigned8bit)) // nextGroupSize [1 .. 255]
            .withDataField(DataField.of(DataType.Unsigned16bit)) // accumulatedFlow [0.00 - 255.00], 1st byte - integer part, 2nd byte - fractional
            .withDataField(DataField.of(DataType.Signed32bit)) // accumulatedFlowDt
            .build();

    // todo ak1 rework all below
    private final DataField cityId = storage.getDataField(0);
    private final DataField status = storage.getDataField(1);
    // ...

    public class Flow {
        public Flow(final int id) {

        }
    }
}
