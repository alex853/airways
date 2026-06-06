package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

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

    public Airport2City() {
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Optional<Link> byAirportIdAndCityId(final int airportId, final int cityId) {
        checkArgument(airportId > 0);
        checkArgument(cityId > 0);

        return storage.findFirst(recordId -> readAirportId(recordId) == airportId && readCityId(recordId) == cityId);
    }

    public Stream<Link> allByAirportId(final int airportId) {
        checkArgument(airportId > 0);

        return storage.filter(recordId -> readAirportId(recordId) == airportId);
    }

    public Stream<Link> allByCityId(final int cityId) {
        checkArgument(cityId > 0);

        return storage.filter(recordId -> readCityId(recordId) == cityId);
    }

    public Link create(final int airportId, final int cityId) {
        checkArgument(airportId >= 1);
        checkArgument(cityId >= 1);
        checkArgument(byAirportIdAndCityId(airportId, cityId).isEmpty());

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
            return readAirportId(id);
        }

        public int getCityId() {
            return readCityId(id);
        }
    }

    private int readAirportId(int recordId) {
        return storage.getAsInt(recordId, airportIdField);
    }

    private int readCityId(int recordId) {
        return storage.getAsInt(recordId, cityIdField);
    }
}
