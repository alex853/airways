package net.simforge.airways2.world;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.storage.Strings;
import net.simforge.airways2.world.processors.FlightMissionProcessor;
import net.simforge.airways2.world.processors.RandomFlightMissionGenerator;
import net.simforge.airways2.world.datamodel.*;

import java.io.IOException;

public class World {
    private final WorldStorageStrategy worldStorageStrategy;

    private final Strings strings = new Strings();

    private final Events events = new Events();

    private final Countries countries = new Countries(this.strings);
    private final Cities cities = new Cities(this.strings);
    private final Airports airports = new Airports(this.strings);
    private final Airport2City airport2city = new Airport2City();

    private final AircraftTypes aircraftTypes = new AircraftTypes();
    private final Aircrafts aircrafts = new Aircrafts(this.strings);
    private final FlightMissions flightMissions = new FlightMissions();

    private final Storage<Object> worldTime = Storage.builder()
            .name("world-time")
            .withDataField(DataField.of(DataType.Signed32bit))
            .build();

    private static final int worldTimeStep = 10;

    private World(final WorldStorageStrategy worldStorageStrategy) {
        this.worldStorageStrategy = worldStorageStrategy;
    }

    public static World create(final WorldStorageStrategy strategy, int startWorldTime) {
        final World world = new World(strategy);
        world.setWorldTime(startWorldTime);
        return world;
    }

    public static World load(final WorldStorageStrategy strategy) throws IOException {
        final World world = new World(strategy);
        strategy.load(rootPath -> {
            world.strings.loadIfExists(rootPath);

            world.events.loadIfExists(rootPath);

            world.countries.loadIfExists(rootPath);
            world.cities.loadIfExists(rootPath);
            world.airports.loadIfExists(rootPath);
            world.airport2city.loadIfExists(rootPath);

            world.aircraftTypes.loadIfExists(rootPath);
            world.aircrafts.loadIfExists(rootPath);
            world.flightMissions.loadIfExists(rootPath);

            world.worldTime.loadIfExists(rootPath);
        });

        return world;
    }

    public void save() throws IOException {
        worldStorageStrategy.save(rootPath -> {
            strings.save(rootPath);

            events.save(rootPath);

            countries.save(rootPath);
            cities.save(rootPath);
            airports.save(rootPath);
            airport2city.save(rootPath);

            aircraftTypes.save(rootPath);
            aircrafts.save(rootPath);
            flightMissions.save(rootPath);

            worldTime.save(rootPath);
        });
    }

    public Strings strings() {
        return strings;
    }

    public Events events() {
        return events;
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

    public AircraftTypes aircraftTypes() {
        return aircraftTypes;
    }

    public Aircrafts aircrafts() {
        return aircrafts;
    }

    public FlightMissions flightMissions() {
        return flightMissions;
    }

    public boolean process(final int expectedWorldTime) {
        final int processedWorldTime = getWorldTime();
        final int newWorldTime = processedWorldTime + worldTimeStep;

        if (expectedWorldTime < newWorldTime) {
            return false; // do not process world more frequent than 'worldTimeStep' setting
        }

        RandomFlightMissionGenerator.process(this, newWorldTime);
        FlightMissionProcessor.process(this, newWorldTime);

        setWorldTime(newWorldTime);

        return !(expectedWorldTime > newWorldTime);
    }

    public int getWorldTime() {
        if (worldTime.getCount() == 1) {
            return worldTime.getAsInt(1, worldTime.getDataField(0));
        } else {
            throw new IllegalStateException("unable to read world time");
        }
    }

    public void setWorldTime(int newWorldTime) {
        if (worldTime.getCount() == 0) {
            worldTime.addRecord();
        }
        worldTime.set(1, worldTime.getDataField(0), newWorldTime);
    }
}
