package net.simforge.airways2.world.datamodel;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.storage.DataField;
import net.simforge.airways2.storage.DataType;
import net.simforge.airways2.storage.Storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class EventLog {
    private final Storage<Event> storage = Storage.<Event>builder()
            .name("event-log")
            .withInstantiator(Event::new)
            .withIdOf(DataType.Signed32bit)
            .withDataField(DataField.of(DataType.Signed32bit)) // time
            .withDataField(DataField.of(DataType.Unsigned16bit)) // event type
            .withDataField(DataField.of(DataType.Unsigned8bit)) // object1 type
            .withDataField(DataField.of(DataType.Signed32bit)) // object1 id
            .withDataField(DataField.of(DataType.Unsigned8bit)) // object2 type
            .withDataField(DataField.of(DataType.Signed32bit)) // object2 id
            .withDataField(DataField.of(DataType.Unsigned8bit)) // object3 type
            .withDataField(DataField.of(DataType.Signed32bit)) // object3 id
            .withDataField(DataField.of(DataType.Unsigned8bit)) // object4 type
            .withDataField(DataField.of(DataType.Signed32bit)) // object4 id
            .build();

    private final DataField timeField = storage.getDataField(0);
    private final DataField eventTypeField = storage.getDataField(1);
    private final DataField object1TypeField = storage.getDataField(2);
    private final DataField object1IdField = storage.getDataField(3);
    private final DataField object2TypeField = storage.getDataField(4);
    private final DataField object2IdField = storage.getDataField(5);
    private final DataField object3TypeField = storage.getDataField(6);
    private final DataField object3IdField = storage.getDataField(7);
    private final DataField object4TypeField = storage.getDataField(8);
    private final DataField object4IdField = storage.getDataField(9);

    public EventLog() {
    }

    public void loadIfExists(final Path rootPath) throws IOException {
        this.storage.loadIfExists(rootPath);
    }

    public void save(final Path rootPath) throws IOException {
        storage.save(rootPath);
    }

    public Stream<Event> all() {
        return storage.all();
    }

    @Deprecated
    public Collection<EventLog.Event> filter(final Predicate<EventLog.Event> condition) {
        return storage.filter(condition); // todo ak1 migrate to filter1
    }

    public void deleteById(final int id) {
        storage.deleteRecord(id);
    }

    public void log(final int time, final EventType eventType, final EventLogId object1, final EventLogId object2, final EventLogId object3, final EventLogId object4) {
        final int recordId = storage.addRecord();
        storage.set(recordId, timeField, time);
        storage.set(recordId, eventTypeField, eventType.code);

        if (object1 != null) {
            storage.set(recordId, object1TypeField, object1.getType().code);
            storage.set(recordId, object1IdField, object1.getId());
        }

        if (object2 != null) {
            storage.set(recordId, object2TypeField, object2.getType().code);
            storage.set(recordId, object2IdField, object2.getId());
        }

        if (object3 != null) {
            storage.set(recordId, object3TypeField, object3.getType().code);
            storage.set(recordId, object3IdField, object3.getId());
        }

        if (object4 != null) {
            storage.set(recordId, object4TypeField, object4.getType().code);
            storage.set(recordId, object4IdField, object4.getId());
        }
    }

    public void printDeletedRecordInfo() {
        storage.printDeletedRecordInfo();
    }

    public class Event {
        private final int id;

        private Event(final int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public int getTime() {
            return storage.getAsInt(id, timeField);
        }

        public int getTypeRaw() {
            return storage.getAsInt(id, eventTypeField);
        }

        public EventType getType() {
            return EventType.byCode(getTypeRaw());
        }

        public int getObject1TypeRaw() {
            return storage.getAsInt(id, object1TypeField);
        }

        public ObjectType getObject1Type() {
            return ObjectType.byCode(getObject1TypeRaw());
        }

        public int getObject1Id() {
            return storage.getAsInt(id, object1IdField);
        }

        public int getObject2TypeRaw() {
            return storage.getAsInt(id, object2TypeField);
        }

        public ObjectType getObject2Type() {
            return ObjectType.byCode(getObject2TypeRaw());
        }

        public int getObject2Id() {
            return storage.getAsInt(id, object2IdField);
        }

        public int getObject3TypeRaw() {
            return storage.getAsInt(id, object3TypeField);
        }

        public ObjectType getObject3Type() {
            return ObjectType.byCode(getObject3TypeRaw());
        }

        public int getObject3Id() {
            return storage.getAsInt(id, object3IdField);
        }

        public int getObject4TypeRaw() {
            return storage.getAsInt(id, object4TypeField);
        }

        public ObjectType getObject4Type() {
            return ObjectType.byCode(getObject4TypeRaw());
        }

        public int getObject4Id() {
            return storage.getAsInt(id, object4IdField);
        }
    }

    public enum EventType {
        FlightDispatchedManually(200),
        FlightDispatchedRandomly(201),
        FlightScheduledAndDispatched(202),
        FlightStarted(210),
        FlightFinished(211),
        FlightIsInUnexpectedStatus(212),
        FlightCancelled(219),

        AircraftDepartedFromGate(310),
        AircraftTakeoff(311),
        AircraftLanding(312),
        AircraftArrivedToGate(313),

        // 400 was old TransportFlightCreated and it has been removed

        TransportFlightCreated(500),
        TransportFlightCheckInStarted(501),
        TransportFlightCheckInEnded(502),
        TransportFlightBoardingStarted(503),
        TransportFlightBoardingEnded(504),
        TransportFlightArrived(505),
        TransportFlightDeparted(506),
        TransportFlightDeboardingStarted(507),
        TransportFlightFinished(508),
        ;

        private final int code;

        EventType(final int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static EventType byCode(final int code) {
            return Arrays.stream(EventType.values())
                    .filter(status -> status.code == code)
                    .findFirst()
                    .orElse(null);
        }
    }

    public enum ObjectType {
        Pilot(1),
        FlightMission(2),
        Aircraft(3),
        Airport(4),
        TransportFlight(5),
        ScheduledFlight(6);

        private final int code;

        ObjectType(final int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static ObjectType byCode(final int code) {
            return Arrays.stream(ObjectType.values())
                    .filter(status -> status.code == code)
                    .findFirst()
                    .orElse(null);
        }
    }

    @Data
    @AllArgsConstructor
    public static class EventLogId {
        private final ObjectType type;
        private final int id;
    }

    public static EventLogId id(final FlightMissions.Mission mission) {
        checkNotNull(mission);
        return new EventLogId(ObjectType.FlightMission, mission.getId());
    }

    public static EventLogId id(final Aircrafts.Aircraft aircraft) {
        checkNotNull(aircraft);
        return new EventLogId(ObjectType.Aircraft, aircraft.getId());
    }

    public static EventLogId id(final TransportFlights.Flight flight) {
        checkNotNull(flight);
        return new EventLogId(ObjectType.TransportFlight, flight.getId());
    }

    public static EventLogId id(final ScheduledFlights.Flight flight) {
        checkNotNull(flight);
        return new EventLogId(ObjectType.ScheduledFlight, flight.getId());
    }

    public static EventLogId pilotId(int pilotId) { // todo ak3 review all the usages when pilot will be introduced
        return new EventLogId(ObjectType.Pilot, pilotId);
    }

    public static EventLogId airportId(final int airportId) {
        checkArgument(airportId > 0);
        return new EventLogId(ObjectType.Airport, airportId);
    }
}
