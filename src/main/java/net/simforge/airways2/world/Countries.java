package net.simforge.airways2.world;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

public class Countries {
    private final Storage<Country> storage = Storage.<Country>builder()
            .name("countries")
            .withInstantiator(Country::new)
            .withIdOf(DataType.Unsigned16bit)
            .withDataField(DataField.of(DataType.PlainString).length(2)) // code
            .withDataField(DataField.of(DataType.PlainString).length(20)) // name
            .build();

    private final DataField codeField = storage.getDataField(0);
    private final DataField nameField = storage.getDataField(1);

    private Countries() {}

    public static Countries loadOrCreate() throws IOException {
        final Countries countries = new Countries();
        countries.storage.loadIfExists();
        return countries;
    }

    public void save() throws IOException {
        storage.save();
    }

    public Optional<Country> byId(final int countryId) {
        return storage.byId(countryId);
    }

    public Optional<Country> byCode(final String code) {
        checkNotNull(code, "code should not be null");
        return storage.findFirst(c -> code.equals(c.getCode()));
    }

    public Country create(final String code,
                          final String name) {
        final int recordId = storage.addRecord();
        storage.set(recordId, codeField, code);
        storage.set(recordId, nameField, name);
        return new Country(recordId);
    }

    public class Country {
        private final int id;

        private Country(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public String getCode() {
            return storage.getAsString(id, codeField);
        }

        public String getName() {
            return storage.getAsString(id, nameField);
        }
    }
}
