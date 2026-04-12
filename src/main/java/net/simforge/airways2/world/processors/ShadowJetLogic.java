package net.simforge.airways2.world.processors;

import com.google.common.collect.Sets;
import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

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

    private static final Set<String> allowedAirports = Set.of(
            "EDDF", "EDDM", "EDDH", "EDDB",
            "EGLL", "EGKK", "EGCC", "EGPH", "EGBB",
            "LFPG", "LFPO", "LFPB",
            "LKPR",
            "LOWW");
    private static volatile long lastTFWithJourneysTS;

    public static void provideTransportFlightIfRequired(World world, FlightMissions.Mission mission) {
        String from = world.airports().getIcao(mission.getDepartureAirportId()).orElseThrow();
        String to = world.airports().getIcao(mission.getDestinationAirportId()).orElseThrow();
        String route = from + "-" + to;

        if (from.equals(to)) {
            log.warn("Transport flight provisioning - f/m #{} - {} - from equals to, route not allowed, SKIPPING", mission.getId(), route);
            return;
        }

        if (!allowedAirports.contains(from) || !allowedAirports.contains(to)) {
            log.warn("Transport flight provisioning - f/m #{} - {} - route not allowed, SKIPPING", mission.getId(), route);
            return;
        }

        Set<Integer> fromCitiesId = world.airport2city().allByAirportId(mission.getDepartureAirportId()).map(Airport2City.Link::getCityId).collect(Collectors.toSet());
        Set<Integer> toCitiesId = world.airport2city().allByAirportId(mission.getDestinationAirportId()).map(Airport2City.Link::getCityId).collect(Collectors.toSet());

        if (fromCitiesId.isEmpty() || toCitiesId.isEmpty()) {
            log.warn("Transport flight provisioning - f/m #{} - {} - no cities found - {} / {}, SKIPPING", mission.getId(), route, fromCitiesId, toCitiesId);
            return;
        }

        Sets.SetView<Integer> intersection = Sets.intersection(fromCitiesId, toCitiesId);
        if (!intersection.isEmpty()) {
            log.warn("Transport flight provisioning - f/m #{} - {} - intersection {} detected, SKIPPING", mission.getId(), route, intersection);
            return;
        }

        // todo ak1 cabin layout depending on aircraft type - lets collect few most frequently used aircraft types
        // todo ak1 cabin layout depending on aircraft type - manually put that information into some dictionary
        TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(mission);
        log.warn("Transport flight provisioning - f/m #{}, t/f #{} - transport flight CREATED", mission.getId(), transportFlight.getId());

        world.c2cFlowControl().updateSuccessRate(transportFlight, 0.001f);

        int journeyBooked = 0;
        int paxBooked = 0;

        world.transportFlightControl().startCheckIn(transportFlight);

        if ((System.currentTimeMillis() - lastTFWithJourneysTS < 10*60*1000) || lastTFWithJourneysTS == 0) {
            List<Journeys.Journey> journeys = world.journeys().filter(world.journeys().byStatus(Journeys.Status.LookingForTickets))
                    .filter(j -> fromCitiesId.contains(j.getFromCityId())
                            && toCitiesId.contains(j.getToCityId())
                            && j.getCabinService() == CabinLayout.Service.Y) // todo ak1 also needs changes
                    .toList();
            log.warn("Transport flight provisioning - f/m #{}, t/f #{} - Found journeys: {}", mission.getId(), transportFlight.getId(), journeys.stream().map(Journeys.Journey::getId).toList());

            for (int i = 0; i < Math.min(3, journeys.size()); i++) {
                Journeys.Journey journey = journeys.get(i);

                world.journeyControl().bookDirectFlightJourneyNoChecks(journey, transportFlight);
                world.journeyControl().waitForCheckin(journey);
                world.journeyControl().checkin(journey);

                journeyBooked++;
                paxBooked += journey.getGroupSize();

                log.warn("Transport flight provisioning - f/m #{}, t/f #{} - Journey {} booked to the flight and checked-in", mission.getId(), transportFlight.getId(), journey);
            }

            if (journeyBooked > 0) {
                lastTFWithJourneysTS = System.currentTimeMillis();
                log.error("Transport flight provisioning - f/m #{}, t/f #{} - lastTFWithJourneysTS updated to {}", mission.getId(), transportFlight.getId(), lastTFWithJourneysTS);
            }
        } else {
            log.error("Transport flight provisioning - f/m #{}, t/f #{} - journey step intentionally skipped due to lastTFWithJourneysTS", mission.getId(), transportFlight.getId());
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
