package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class BusyBirdsMissionControl {
    private static final Logger log = LoggerFactory.getLogger(BusyBirdsMissionControl.class);

    private final World world;

    public BusyBirdsMissionControl(final World world) {
        this.world = world;
    }

    public BusyBirdsMissionControl.MissionPlan buildPlan(final Journeys.Journey journey, final Aircrafts.Aircraft aircraft) {
        final List<String> messages = new ArrayList<>();

        final AircraftOperators.AircraftOperator busyBirdsOperator = world.aircraftOperators().byIata(World25.BusyBirdsIata).orElseThrow();

        if (aircraft.getLocationStatus() != Aircrafts.LocationStatus.ParkedAtAirport) {
            messages.add("aircraft is not parked at airport");
        }
        if (aircraft.getOperationalStatus() != Aircrafts.OperationalStatus.Idle) {
            messages.add("aircraft is not idle");
        }

        final Optional<Airports.Airport> fromAirport = chooseAirport(
                busyBirdsOperator,
                world.airport2city().linksByCityId(journey.getFromCityId())
                        .map(l -> world.airports().byId(l.getAirportId()).orElseThrow())
                        .toList());
        final Optional<Airports.Airport> toAirport = chooseAirport(
                busyBirdsOperator,
                world.airport2city().linksByCityId(journey.getToCityId())
                        .map(l -> world.airports().byId(l.getAirportId()).orElseThrow())
                        .toList());

        if (fromAirport.isEmpty()) {
            messages.add("unable to find suitable 'from' airport");
        }

        if (toAirport.isEmpty()) {
            messages.add("unable to find suitable 'from' airport");
        }

        final Optional<Airports.Airport> baseAirport = findNearestBaseAirport(world, busyBirdsOperator, toAirport.get());
        if (baseAirport.isEmpty()) {
            messages.add("unable to find suitable 'base' airport");
        }

        if (!messages.isEmpty()) {
            return new MissionPlan(MissionPlan.Status.Failure, null, messages);
        }

        final List<Leg> legs = new ArrayList<>();

        final Airports.Airport locationAirport = world.airports().byId(aircraft.getLocationAirportId()).get();
        final boolean needFerryFlightToDepartureAirport = locationAirport.getId() != fromAirport.get().getId();

        if (needFerryFlightToDepartureAirport) {
            legs.add(new Leg(Leg.Type.Reposition, locationAirport, fromAirport.get()));
        }

        legs.add(new Leg(Leg.Type.Revenue, fromAirport.get(), toAirport.get(), journey.getGroupSize()));

        final boolean needFerryFlightToBaseAirport = toAirport.get().getId() != baseAirport.get().getId();
        if (needFerryFlightToBaseAirport) {
            legs.add(new Leg(Leg.Type.Reposition, toAirport.get(), baseAirport.get()));
        }

        return new MissionPlan(MissionPlan.Status.Success, legs, null);
    }

    private Optional<Airports.Airport> chooseAirport(final AircraftOperators.AircraftOperator aircraftOperator, final List<Airports.Airport> airports) {
        if (airports.isEmpty()) {
            return Optional.empty();
        }

        final Optional<Airports.Airport> baseAirport = airports.stream()
                .filter(a -> world.airportFacilities().hasFacility(a, aircraftOperator, AirportFacilities.Type.BaseAirport))
                .findFirst();
        if (baseAirport.isPresent()) {
            return baseAirport;
        }

        final List<Airports.Airport> preferredBusinessTerminals = airports.stream()
                .filter(a -> world.airportFacilities().hasFacility(a, aircraftOperator, AirportFacilities.Type.BusinessAviationTerminal))
                .toList();
        if (!preferredBusinessTerminals.isEmpty()) {
            return Optional.of(preferredBusinessTerminals.get((int) (Math.random()*preferredBusinessTerminals.size())));
        }

        final List<Airports.Airport> anyBusinessTerminals = airports.stream()
                .filter(a -> world.airportFacilities().hasFacility(a, AirportFacilities.Type.BusinessAviationTerminal))
                .toList();
        if (!anyBusinessTerminals.isEmpty()) {
            return Optional.of(anyBusinessTerminals.get((int) (Math.random()*anyBusinessTerminals.size())));
        }

        return Optional.of(airports.get((int) (Math.random()*airports.size())));
    }

    private Optional<Airports.Airport> findNearestBaseAirport(final World world, final AircraftOperators.AircraftOperator aircraftOperator, final Airports.Airport airport) {
        return world.airportFacilities().by(aircraftOperator, AirportFacilities.Type.BaseAirport)
                .map(f -> world.airports().byId(f.getAirportId()).orElseThrow())
                .min(Comparator.comparingDouble(a -> Geo.distance(a.getCoords(), airport.getCoords())));
    }

    public static class MissionPlan {
        private final Status status;
        private final List<Leg> legs;
        private final List<String> messages;

        public MissionPlan(final Status status, final List<Leg> legs, final List<String> messages) {
            this.status = status;
            this.legs = legs != null ? Collections.unmodifiableList(legs) : null;
            this.messages = messages != null ? Collections.unmodifiableList(messages) : null;
        }

        public Status getStatus() {
            return status;
        }

        public List<Leg> getLegs() {
            return legs;
        }

        public List<String> getMessages() {
            return messages;
        }

        public enum Status {
            Success,
            Failure,
        }
    }

    public static class Leg {
        private final Type type;
        private final Airports.Airport fromAirport;
        private final Airports.Airport toAirport;
        private final int pax;

        public Leg(final Type type, final Airports.Airport fromAirport, final Airports.Airport toAirport) {
            this.type = type;
            this.fromAirport = fromAirport;
            this.toAirport = toAirport;
            this.pax = 0;
        }

        public Leg(final Type type, final Airports.Airport fromAirport, final Airports.Airport toAirport, final int pax) {
            this.type = type;
            this.fromAirport = fromAirport;
            this.toAirport = toAirport;
            this.pax = pax;
        }

        public Type getType() {
            return type;
        }

        public Airports.Airport getFromAirport() {
            return fromAirport;
        }

        public Airports.Airport getToAirport() {
            return toAirport;
        }

        public int getPax() {
            return pax;
        }

        public enum Type {
            Reposition,
            Revenue,
        }
    }
}
