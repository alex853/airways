package net.simforge.airways2.world;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class World {
    private final String worldName;

    private final Strings strings;

    private final Countries countries;
    private final Cities cities;

    private World(final String worldName) throws IOException {
        this.worldName = worldName;

        this.strings = new Strings();

        this.countries = Countries.loadOrCreate(this);
        this.cities = Cities.loadOrCreate(this);
    }

    public static World loadOrCreate(final String worldName) throws IOException {
        return new World(worldName);
    }

    public Path getRootPath() {
        return Paths.get(worldName);
    }

    public void save() throws IOException {
        strings.save();
        countries.save();
        cities.save();
    }

    public Strings strings() {
        return strings;
    }

    public Countries countries() {
        return countries;
    }

    public Cities cities() {
        return cities;
    }
}
