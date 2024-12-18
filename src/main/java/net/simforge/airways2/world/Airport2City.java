package net.simforge.airways2.world;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

public class Airport2City {
    private final Storage<Link> storage = Storage.<Link>builder()
            .name("airport2city")
            .withInstantiator(Link::new)
            .withIdOf(DataType.Unsigned24bit)
            .withDataField(DataField.of(DataType.Unsigned16bit)) // airportId
            .withDataField(DataField.of(DataType.Unsigned16bit)) // cityId
            .build();

    private final DataField airportId = storage.getDataField(0);
    private final DataField cityId = storage.getDataField(1);

    public class Link {
        public Link(final int id) {

        }
    }
}
