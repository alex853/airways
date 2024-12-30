package net.simforge.airways2.world;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.storage.Strings;
import net.simforge.airways2.world.processors.FlightMissionProcessor;
import net.simforge.airways2.world.processors.RandomFlightMissionGenerator;
import net.simforge.airways2.world.datamodel.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.common.base.Preconditions.checkState;

public class World {
    private final String worldName;

    private final Strings strings;

    private final Events events;

    private final Countries countries;
    private final Cities cities;
    private final Airports airports;
    private final Airport2City airport2city;

    private final AircraftTypes aircraftTypes;
    private final Aircrafts aircrafts;
    private final FlightMissions flightMissions;

    private final Storage<Object> worldTime;
    private static final int worldTimeStep = 10;
    private int lastSavedAtWorldTime;
    private static final int saveWorldPeriod = Time.ONE_MINUTE;

    private World(final String worldName) throws IOException {
        this.worldName = worldName;

        this.strings = Strings.loadOrCreate(getRootPath());

        this.events = Events.loadOrCreate(this);

        this.countries = Countries.loadOrCreate(this);
        this.cities = Cities.loadOrCreate(this);
        this.airports = Airports.loadOrCreate(this);
        this.airport2city = Airport2City.loadOrCreate(this);

        this.aircraftTypes = AircraftTypes.loadOrCreate(this);
        this.aircrafts = Aircrafts.loadOrCreate(this);
        this.flightMissions = FlightMissions.loadOrCreate(this);

        this.worldTime = Storage.builder()
                .name("world-time")
                .withDataField(DataField.of(DataType.Signed32bit))
                .build();
        this.worldTime.setRootPath(getRootPath());
        this.worldTime.loadIfExists();

        this.lastSavedAtWorldTime = readWorldTime();
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

        events.save();

        countries.save();
        cities.save();
        airports.save();
        airport2city.save();

        aircraftTypes.save();
        aircrafts.save();
        flightMissions.save();

        worldTime.save();
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

    public boolean process() throws IOException {
        final int processedWorldTime = readWorldTime();
//        final int actualRealWorldTime = Time.now();
        final int actualRealWorldTime = processedWorldTime + 10;

        final int diff = actualRealWorldTime - processedWorldTime;
        checkState(diff >= 0);

        final int newWorldTime = processedWorldTime + worldTimeStep;

        if (newWorldTime > actualRealWorldTime) {
            return false; // do not process world more frequent than 'worldTimeStep' setting
        }

        RandomFlightMissionGenerator.process(this, newWorldTime);
        FlightMissionProcessor.process(this, newWorldTime);

        writeWorldTime(newWorldTime);

        if (lastSavedAtWorldTime + saveWorldPeriod < newWorldTime) {
            save();
            lastSavedAtWorldTime = newWorldTime;
        }

        return true;
    }

    private int readWorldTime() {
        return worldTime.getCount() == 1
                ? worldTime.getAsInt(1, worldTime.getDataField(0))
                : Time.now();
    }

    private void writeWorldTime(final int newWorldTime) {
        if (worldTime.getCount() == 0) {
            worldTime.addRecord();
        }
        worldTime.set(1, worldTime.getDataField(0), newWorldTime);
    }

    public void setWorldTime(int newWorldTime) {
        writeWorldTime(newWorldTime);
    }
}
