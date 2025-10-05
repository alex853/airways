package net.simforge.airways2.world;

import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;
import net.simforge.airways2.storage.Strings;
import net.simforge.airways2.world.processors.*;
import net.simforge.airways2.world.datamodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class World {
    private static final Logger log = LoggerFactory.getLogger(World.class);

    private final WorldStorageStrategy worldStorageStrategy;

    private final Strings strings = new Strings();

    private final EventsToProcess eventsToProcess = new EventsToProcess();
    private final EventLog eventLog = new EventLog();

    private final Countries countries = new Countries(this.strings);
    private final Cities cities = new Cities(this.strings);
    private final Airports airports = new Airports(this.strings);
    private final Airport2City airport2city = new Airport2City();

    private final AircraftTypes aircraftTypes = new AircraftTypes();
    private final Aircrafts aircrafts = new Aircrafts(this.strings);
    private final AircraftOperators aircraftOperators = new AircraftOperators();
    private final FlightMissions flightMissions = new FlightMissions();

    private final ScheduledFlights scheduledFlights = new ScheduledFlights();

    private final TransportFlights transportFlights = new TransportFlights();

    private final Airport2AirportDailyFlightStats airport2airportDailyFlightStats = new Airport2AirportDailyFlightStats();

    private final CityFlows cityFlows = new CityFlows(this);
    private final City2CityFlows city2CityFlows = new City2CityFlows();
    private final Journeys journeys = new Journeys();

    private final Storage<Object> worldTime = Storage.builder()
            .name("world-time")
            .withDataField(DataField.of(DataType.Signed32bit))
            .build();

    private final FlightMissionControl flightMissionControl = new FlightMissionControl(this);

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

            world.eventsToProcess.loadIfExists(rootPath);
            world.eventLog.loadIfExists(rootPath);

            world.countries.loadIfExists(rootPath);
            world.cities.loadIfExists(rootPath);
            world.airports.loadIfExists(rootPath);
            world.airport2city.loadIfExists(rootPath);

            world.aircraftTypes.loadIfExists(rootPath);
            world.aircrafts.loadIfExists(rootPath);
            world.aircraftOperators.loadIfExists(rootPath);
            world.flightMissions.loadIfExists(rootPath);

            world.scheduledFlights.loadIfExists(rootPath);

            world.transportFlights.loadIfExists(rootPath);

            world.airport2airportDailyFlightStats.loadIfExists(rootPath);

            world.cityFlows.loadIfExists(rootPath);
            world.city2CityFlows.loadIfExists(rootPath);
            world.journeys.loadIfExists(rootPath);

            world.worldTime.loadIfExists(rootPath);
        });

        world.cityFlows.createMissingCityFlows();

        return world;
    }

    public void save() throws IOException {
        worldStorageStrategy.save(rootPath -> {
            strings.save(rootPath);

            eventsToProcess.save(rootPath);
            eventLog.save(rootPath);

            countries.save(rootPath);
            cities.save(rootPath);
            airports.save(rootPath);
            airport2city.save(rootPath);

            aircraftTypes.save(rootPath);
            aircrafts.save(rootPath);
            aircraftOperators.save(rootPath);
            flightMissions.save(rootPath);

            scheduledFlights.save(rootPath);

            transportFlights.save(rootPath);

            airport2airportDailyFlightStats.save(rootPath);

            cityFlows.save(rootPath);
            city2CityFlows.save(rootPath);
            journeys.save(rootPath);

            worldTime.save(rootPath);
        });
    }

    public EventsToProcess eventsToProcess() {
        return eventsToProcess;
    }

    public EventLog eventLog() {
        return eventLog;
    }

    public void log(final EventLog.EventType eventType, final EventLog.EventLogId object1, final FlightMissions.Mission mission, final Aircrafts.Aircraft aircraft, final EventLog.EventLogId object4) {
        eventLog.log(getWorldTime(), eventType, object1, EventLog.id(mission), EventLog.id(aircraft), object4);
    }

    public void log(final EventLog.EventType eventType, final EventLog.EventLogId object1, final FlightMissions.Mission mission, final Aircrafts.Aircraft aircraft) {
        eventLog.log(getWorldTime(), eventType, object1, EventLog.id(mission), EventLog.id(aircraft), null);
    }

    public void log(final EventLog.EventType eventType, final EventLog.EventLogId object1, final FlightMissions.Mission mission) {
        eventLog.log(getWorldTime(), eventType, object1, EventLog.id(mission), null, null);
    }

    public void log(final EventLog.EventType eventType, final EventLog.EventLogId object1) {
        eventLog.log(getWorldTime(), eventType, object1, null, null, null);
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

    public AircraftOperators aircraftOperators() {
        return aircraftOperators;
    }

    public FlightMissions flightMissions() {
        return flightMissions;
    }

    public FlightMissionControl flightMissionControl() {
        return flightMissionControl;
    }

    public ScheduledFlights scheduledFlights() {
        return scheduledFlights;
    }

    public TransportFlights transportFlights() {
        return transportFlights;
    }

    public Airport2AirportDailyFlightStats airport2airportDailyFlightStats() {
        return airport2airportDailyFlightStats;
    }

    public CityFlows cityFlows() {
        return cityFlows;
    }

    public City2CityFlows city2cityFlows() {
        return city2CityFlows;
    }

    public Journeys journeys() {
        return journeys;
    }

    public boolean process(final int expectedWorldTime) {
        final int processedWorldTime = getWorldTime();
        final int newWorldTime = processedWorldTime + worldTimeStep;

        if (expectedWorldTime < newWorldTime) {
            return false; // do not process world more frequent than 'worldTimeStep' setting
        }

        setWorldTime(newWorldTime);

        try {
            FlightMissionProcessor.process(this);
            RandomFlightMissionGenerator.process(this);
            ScheduledFlightMissionGenerator.process(this);
            TransportFlightProcessor.process(this);
            Airport2AirportDailyFlightStatsRotation.process(this);
            CityFlowsProcessor.process(this);
            City2CityFlowsProcessor.process(this);
            JourneyProcessor.process(this);

            MiscCleanups.process(this);
            FlightsCleanup.process(this);
        } catch (final RuntimeException e) {
            log.error("error during world processor", e);
        }

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
