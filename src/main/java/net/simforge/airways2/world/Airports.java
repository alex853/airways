package net.simforge.airways2.world;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

public class Airports {
    private final Storage<Airport> storage = Storage.<Airport>builder()
            .name("airports")
            .withInstantiator(Airport::new)
            .withIdOf(DataType.Unsigned16bit)
            .withDataField(DataField.of(DataType.LatLong24bit)) // latitude
            .withDataField(DataField.of(DataType.LatLong24bit)) // longitude
            .withDataField(DataField.of(DataType.PlainString).length(3)) // iata
            .withDataField(DataField.of(DataType.PlainString).length(4)) // icao
            .withDataField(DataField.of(DataType.PlainString).length(20)) // name
            .build();

    private final DataField latitudeField = storage.getDataField(0);
    private final DataField longitudeField = storage.getDataField(1);
    private final DataField iataField = storage.getDataField(2);
    private final DataField icaoField = storage.getDataField(3);
    private final DataField nameField = storage.getDataField(4);

    public class Airport {
        public Airport(final int id) {

        }
    }
}
