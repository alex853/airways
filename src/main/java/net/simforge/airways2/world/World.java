package net.simforge.airways2.world;

import net.simforge.airways2.storage.Strings;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class World {
    private final String worldName;

    private final Strings strings;

    private final Countries countries;
    private final Cities cities;
    private final Airports airports;
    private final Airport2City airport2city;

    private World(final String worldName) throws IOException {
        this.worldName = worldName;

        this.strings = Strings.loadOrCreate(getRootPath());

        this.countries = Countries.loadOrCreate(this);
        this.cities = Cities.loadOrCreate(this);
        this.airports = Airports.loadOrCreate(this);
        this.airport2city = Airport2City.loadOrCreate(this);
    }

    public static World loadOrCreate(final String worldName) throws IOException {
        return new World(worldName);
    }

    public Path getRootPath() {
        return Paths.get(worldName);
    }

    // todo ak2 safe saving via save to tmp and then renaming
    public void save() throws IOException {
        strings.save();
        countries.save();
        cities.save();
        airports.save();
        airport2city.save();
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

    public Airports airports() {
        return airports;
    }

    public Airport2City airport2city() {
        return airport2city;
    }
}
