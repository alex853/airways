package net.simforge.airways2.world.processors;

import com.google.common.collect.Sets;
import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.simforge.airways2.storage.Storage.Condition.and;

public class ShadowJetLogic {
    private static final Logger log = LoggerFactory.getLogger(ShadowJetLogic.class);
    private static final Random random = new Random();

    public static Aircrafts.Aircraft findAvailableAircraftOrCreateNew(
            final World world,
            final AircraftTypes.AircraftType aircraftType,
            final Airports.Airport locationAirport) {
        final AircraftOperators.AircraftOperator shadowJet = getShadowJet(world);

        final Optional<Aircrafts.Aircraft> existingAircraft = world.aircrafts().allIdleAndParkedAtAirport().stream()
                .filter(a -> a.getAircraftOperatorId() == shadowJet.getId())
                .filter(a -> a.getAircraftTypeId() == aircraftType.getId())
                .min(Comparator.comparing(a -> Geo.distance(locationAirport.getCoords(), a.getLocationCoords())));

        if (existingAircraft.isPresent()) {
            if (existingAircraft.get().getLocationAirportId() != locationAirport.getId()) {
                log.info("ShadowJet Fleet - moving a/c #{}, {}, reg no {} located at {} to {}",
                        existingAircraft.get().getId(), aircraftType.getIcao(), existingAircraft.get().getRegNo(),
                        world.airports().byId(existingAircraft.get().getLocationAirportId()).orElseThrow().getIcao(),
                        locationAirport.getIcao());
                FlightStats.event("shadowJet moving");
                AircraftHelper.moveParkedAircraftToAnotherAirport(world, existingAircraft.get(), locationAirport);
            } else {
                log.info("ShadowJet Fleet - selecting a/c #{}, {}, reg no {} located at {}",
                        existingAircraft.get().getId(), aircraftType.getIcao(), existingAircraft.get().getRegNo(),
                        locationAirport.getIcao());
                FlightStats.event("shadowJet selecting");
            }

            return existingAircraft.get();
        }

        String newRegNo = null;
        for (int i = 0; i < 100; i++) {
            final String regNo = generateRandomSJxxxRegNo();
            final Optional<Aircrafts.Aircraft> aircraftByRegNo = world.aircrafts().byRegNo(regNo);
            if (aircraftByRegNo.isEmpty()) {
                newRegNo = regNo;
                break;
            }
        }

        if (newRegNo == null) {
            log.error("ShadowJet Fleet - could not find non-occupied SJ-xxx reg no");
            throw new IllegalStateException("ShadowJet Fleet - could not find non-occupied SJ-xxx reg no");
        }

        final Aircrafts.Aircraft newAircraft = world.aircrafts().create(aircraftType, newRegNo, locationAirport);
        newAircraft.setAircraftOperatorId(shadowJet.getId());
        log.info("ShadowJet Fleet - creating a/c #{}, {}, reg no {} at {}", newAircraft.getId(), aircraftType.getIcao(), newRegNo, locationAirport.getIcao());
        FlightStats.event("shadowJet creating");
        return newAircraft;
    }

    public static AircraftOperators.AircraftOperator getShadowJet(World world) {
        return world.aircraftOperators().byIata(World25.ShadowJetIata).orElseThrow();
    }

    private static String generateRandomSJxxxRegNo() {
        final String suffix = random.ints(3, 'A', 'Z' + 1)
                .mapToObj(c -> String.valueOf((char) c))
                .collect(Collectors.joining());

        return "SJ-" + suffix;
    }

    public static void provideTransportFlightIfRequired(World world, FlightMissions.Mission mission) {
        String from = world.airports().getIcao(mission.getDepartureAirportId()).orElseThrow();
        String to = world.airports().getIcao(mission.getDestinationAirportId()).orElseThrow();
        String route = from + "-" + to;

        if (from.equals(to)) {
            log.warn("Transport flight provisioning - f/m #{} - {} - from equals to, route not allowed, SKIPPING", mission.getId(), route);
            return;
        }

        Set<Integer> fromCitiesId = world.airport2city().allByAirportId(mission.getDepartureAirportId()).map(Airport2City.Link::getCityId).collect(Collectors.toSet());
        Set<Integer> toCitiesId = world.airport2city().allByAirportId(mission.getDestinationAirportId()).map(Airport2City.Link::getCityId).collect(Collectors.toSet());

        Sets.SetView<Integer> intersection = Sets.intersection(fromCitiesId, toCitiesId);
        if (!intersection.isEmpty()) {
            log.warn("Transport flight provisioning - f/m #{} - {} - intersection {} detected, SKIPPING", mission.getId(), route, intersection);
            return;
        }

        TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(mission);
        log.warn("Transport flight provisioning - f/m #{}, t/f #{} - transport flight CREATED", mission.getId(), transportFlight.getId());

        world.c2cFlowControl().updateSuccessRate(transportFlight, 0.01f);

        int journeyBooked = 0;
        int paxBooked = 0;

        world.transportFlightControl().startCheckIn(transportFlight);

        double loadFactor = Tools.random(40, 60) / 100.0;
        int consideredAvgPaxPerJourney = 5;
        int maxCount = (int) ((transportFlight.getTotalTickets().getTotal() * loadFactor) / consideredAvgPaxPerJourney);

        List<Journeys.Journey> collected = new ArrayList<>();

        CabinLayout remained = transportFlight.getRemainedTickets();

        Stream<Journeys.Journey> journeyStream = world.journeys().filter(and(
                        world.journeys().byNoBusyBirdsProcessing(),
                        world.journeys().byStatus(Journeys.Status.LookingForTickets)))
                .filter(j -> fromCitiesId.contains(j.getFromCityId())
                        && toCitiesId.contains(j.getToCityId()));
        Iterator<Journeys.Journey> it = journeyStream.iterator();
        while (it.hasNext()
                && (remained.getTotal() > 0)
                && (collected.size() < maxCount)) {
            Journeys.Journey journey = it.next();
            CabinLayout newRemained;
            try {
                newRemained = remained.occupySeats(journey.getGroupSize(), journey.getCabinService());
            } catch (IllegalArgumentException e) {
                continue;
            }

            collected.add(journey);
            remained = newRemained;
        }
        log.warn("Transport flight provisioning - f/m #{}, t/f #{} - Selected load factor {}, Found journeys: {}", mission.getId(), transportFlight.getId(), loadFactor, collected.stream().map(Journeys.Journey::getId).toList());

        // todo ak2 some number of journeys to ping 'looking for tickets' processing randomly distributed in next 5 minutes?

        for (Journeys.Journey journey : collected) {
            world.journeyControl().bookDirectFlightJourneyNoChecks(journey, transportFlight);
            world.journeyControl().waitForCheckin(journey);
            world.journeyControl().checkin(journey);

            journeyBooked++;
            paxBooked += journey.getGroupSize();

            log.warn("Transport flight provisioning - f/m #{}, t/f #{} - Journey {} booked to the flight and checked-in", mission.getId(), transportFlight.getId(), journey);
        }

        world.transportFlightControl().startBoarding(transportFlight);
        log.warn("Transport flight provisioning - f/m #{}, t/f #{} - {} journeys / {} PAX booked and checked-in, boarding started, DONE", mission.getId(), transportFlight.getId(), journeyBooked, paxBooked);
    }

    public static void cancelTransportFlightIfExists(World world, FlightMissions.Mission mission) {
        Optional<TransportFlights.Flight> transportFlightO = world.transportFlights().byFlightMissionId(mission.getId());
        if (transportFlightO.isEmpty()) {
            return;
        }

        TransportFlights.Flight transportFlight = transportFlightO.get();
        world.transportFlightControl().unloadJourneysForcefullyFromActiveFlight(transportFlight);
        transportFlight.setStatus(TransportFlights.Status.Cancelled);

        log.warn("Transport flight cancellation - f/m #{}, t/f #{}, PAX {} - flight cancelled, all PAX unloaded", mission.getId(), transportFlight.getId(), transportFlight.getPaxOnBoard());
    }

    public static void deboardTransportFlightIfExists(World world, FlightMissions.Mission mission) {
        Optional<TransportFlights.Flight> transportFlightO = world.transportFlights().byFlightMissionId(mission.getId());
        if (transportFlightO.isEmpty()) {
            return;
        }

        TransportFlights.Flight transportFlight = transportFlightO.get();
        world.transportFlightControl().startDeboarding(transportFlight);
        log.warn("Transport flight deboarding - f/m #{}, t/f #{} - deboarding started", mission.getId(), transportFlight.getId());
    }
}
