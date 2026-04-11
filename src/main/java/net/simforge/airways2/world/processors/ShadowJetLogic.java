package net.simforge.airways2.world.processors;

import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import net.simforge.airways2.worldbuilder.World25;
import net.simforge.commons.misc.Geo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
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

    public static void provideTransportFlightIfRequired(World world, FlightMissions.Mission mission) {
        String from = world.airports().getIcao(mission.getDepartureAirportId()).orElseThrow();
        String to = world.airports().getIcao(mission.getDestinationAirportId()).orElseThrow();
        String route = from + "-" + to;

        if (!("EDDF-EDDM".equals(route) || "EDDM-EDDF".equals(route))) { // todo ak0 add support for EGLL-LFPG pair
            log.warn("Transport flight provisioning - f/m #{} - {} - route not allowed", mission.getId(), route);
            return;
        }

        log.warn("Transport flight provisioning - f/m #{} - {} - lets create the transport flight", mission.getId(), route);

        // todo ak1 cabin layout depending on aircraft type - lets collect few most frequently used aircraft types
        // todo ak1 cabin layout depending on aircraft type - manually put that information into some dictionary
        TransportFlights.Flight transportFlight = world.transportFlightControl().createTransportFlight(mission);
        log.warn("Transport flight provisioning - f/m #{}, t/f #{} - created - {}", mission.getId(), transportFlight.getId(), transportFlight);

        world.transportFlightControl().startBoarding(transportFlight);
        log.warn("Transport flight provisioning - f/m #{}, t/f #{} - boarding started - {}", mission.getId(), transportFlight.getId(), transportFlight);

        world.c2cFlowControl().updateSuccessRate(transportFlight, 0.001f);
        log.warn("Transport flight provisioning - f/m #{}, t/f #{} - minor c2c flow increase applied", mission.getId(), transportFlight.getId());

        int fromCityId = "EDDF-EDDM".equals(route) ? 27 : 7; // todo ak1 support for several cities attached to the airport
        int toCityId = "EDDM-EDDF".equals(route) ? 7 : 27;

        List<Journeys.Journey> journeys = world.journeys().filter(world.journeys().byStatus(Journeys.Status.LookingForTickets))
                .filter(j -> j.getFromCityId() == fromCityId
                        && j.getToCityId() == toCityId
                        && j.getCabinService() == CabinLayout.Service.Y) // todo ak1 also needs changes
                .toList();
        log.warn("Transport flight provisioning - f/m #{}, t/f #{} - Found journeys: {}", mission.getId(), transportFlight.getId(), journeys.stream().map(Journeys.Journey::getId).toList());

        int journeyBooked = 0;
        int paxBooked = 0;
        for (int i = 0; i < Math.min(3, journeys.size()); i++) {
            Journeys.Journey journey = journeys.get(i);

            // todo ak1 copy&paste from JourneyProcessor
            bookDirectFlightJourney(world, journey, transportFlight);
            world.journeyControl().waitForCheckin(journey);

            journeyBooked++;
            paxBooked += journey.getGroupSize();

            log.warn("Transport flight provisioning - f/m #{}, t/f #{} - Journey {} booked to the flight and checked-in", mission.getId(), transportFlight.getId(), journey);
        }

        log.warn("Transport flight provisioning - f/m #{}, t/f #{} - DONE, {} journeys booked with {} pax", mission.getId(), transportFlight.getId(), journeyBooked, paxBooked);
    }

    // todo ak1 copy&paste from JourneyProcessor
    private static void bookDirectFlightJourney(final World world, final Journeys.Journey journey, final TransportFlights.Flight flight) {
        journey.setTransportFlight1Id(flight.getId());
        world.transportFlightControl().obtainFlightTickets(flight, journey.getGroupSize(), journey.getCabinService());
    }

    public static void cancelTransportFlightIfExists(World world, FlightMissions.Mission mission) {
        Optional<TransportFlights.Flight> transportFlightO = world.transportFlights().byFlightMissionId(mission.getId());
        if (transportFlightO.isEmpty()) {
            return;
        }

        TransportFlights.Flight transportFlight = transportFlightO.get();
        log.warn("Transport flight cancellation - f/m #{}, t/f #{} - starting a cancellation", mission.getId(), transportFlight.getId());

        // todo ak0 deboard all the journeys onboard
        log.warn("Transport flight cancellation - f/m #{}, t/f #{} - {} PAX SHOULD BE DEBOARDED!!!!", mission.getId(), transportFlight.getId(), transportFlight.getPaxOnBoard());

        transportFlight.setStatus(TransportFlights.Status.Cancelled);
        log.warn("Transport flight cancellation - f/m #{}, t/f #{} - flight cancelled", mission.getId(), transportFlight.getId());
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
