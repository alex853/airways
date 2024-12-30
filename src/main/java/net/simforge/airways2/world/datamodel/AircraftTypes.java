package net.simforge.airways2.world.datamodel;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.world.World;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

public class AircraftTypes {
    private final Storage<AircraftType> storage = Storage.<AircraftType>builder()
            .name("aircraft-types")
            .withInstantiator(AircraftType::new)
            .withIdOf(DataType.Unsigned16bit)
            .withDataField(DataField.of(DataType.PlainString).length(4)) // icao
            .withDataField(DataField.of(DataType.PlainString).length(3)) // iata
            .build();

    private final DataField icaoField = storage.getDataField(0);
    private final DataField iataField = storage.getDataField(1);

    private AircraftTypes(final World world) throws IOException {
        this.storage.setRootPath(world.getRootPath());
        this.storage.loadIfExists();
    }

    public static AircraftTypes loadOrCreate(final World world) throws IOException {
        return new AircraftTypes(world);
    }

    public void save() throws IOException {
        storage.save();
    }

    public AircraftType create(final String icao,
                               final String iata) {
        final int recordId = storage.addRecord();
        storage.set(recordId, icaoField, icao);
        storage.set(recordId, iataField, iata);
        return new AircraftType(recordId);
    }

    public Collection<AircraftType> all() {
        return storage.all();
    }

    public Optional<AircraftType> byId(final int aircraftTypeId) {
        return storage.byId(aircraftTypeId);
    }

    public Optional<AircraftType> byIcao(final String icao) {
        checkNotNull(icao, "icao is mandatory");
        return storage.findFirst(t -> t.getIcao().equals(icao));
    }

    public class AircraftType {
        private final int id;

        private AircraftType(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public String getIcao() {
            return storage.getAsString(id, icaoField);
        }

        public String getIata() {
            return storage.getAsString(id, iataField);
        }
    }
}
