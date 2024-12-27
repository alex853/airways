package net.simforge.airways2.world.storage;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.world.World;

import java.io.IOException;
import java.util.Optional;

public class Airport2City {
    private final Storage<Link> storage = Storage.<Link>builder()
            .name("airport2city")
            .withInstantiator(Link::new)
            .withIdOf(DataType.Unsigned24bit)
            .withDataField(DataField.of(DataType.Unsigned16bit)) // airportId
            .withDataField(DataField.of(DataType.Unsigned16bit)) // cityId
            .build();

    private final DataField airportIdField = storage.getDataField(0);
    private final DataField cityIdField = storage.getDataField(1);

    private Airport2City(final World world) throws IOException {
        this.storage.setRootPath(world.getRootPath());
        this.storage.loadIfExists();
    }

    public static Airport2City loadOrCreate(final World world) throws IOException {
        return new Airport2City(world);
    }

    public void save() throws IOException {
        storage.save();
    }

    public Optional<Link> byAirportIdAndCityId(final int airportId, final int cityId) {
        return storage.findFirst(l -> l.getAirportId() == airportId && l.getCityId() == cityId);
    }

    public Link create(final int airportId, final int cityId) {
        final int linkId = storage.addRecord();
        storage.set(linkId, airportIdField, airportId);
        storage.set(linkId, cityIdField, cityId);
        return new Link(linkId);
    }

    public class Link {
        private final int id;

        private Link(final int id) {
            this.id = id;
        }

        public int getAirportId() {
            return storage.getAsInt(id, airportIdField);
        }

        public int getCityId() {
            return storage.getAsInt(id, cityIdField);
        }
    }
}
