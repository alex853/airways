package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.AircraftOperators;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.worldbuilder.World25;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

public class ShadowJetLogic {
    private static final Logger log = LoggerFactory.getLogger(ShadowJetLogic.class);
    private static final Random random = new Random();
    private static final Map<String, Integer> missingAircraftTypes = new HashMap<>();

    public static Aircrafts.Aircraft findAvailableOrCreate(
            final World world,
            final String aircraftTypeIcao,
            final String locationAirportIcao) {
        final AircraftOperators.AircraftOperator shadowJet = world.aircraftOperators().byIata(World25.ShadowJetIata).orElseThrow();
        final Optional<AircraftTypes.AircraftType> requestedAircraftType = world.aircraftTypes().byIcao(aircraftTypeIcao);
        if (requestedAircraftType.isEmpty()) {
            missingAircraftTypes.compute(aircraftTypeIcao, (key, value) -> value == null ? 1 : value + 1);
            missingAircraftTypes.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> log.info("mission aircraft types | {} - {}", entry.getKey(), entry.getValue()));
        }
        final AircraftTypes.AircraftType aircraftType = requestedAircraftType.orElseGet(() -> world.aircraftTypes().byIcao("A320").orElseThrow());
        final Airports.Airport locationAirport = world.airports().byIcao(locationAirportIcao).orElseThrow();

        final Optional<Aircrafts.Aircraft> existingAircraft = world.aircrafts().allIdleAndParkedAtAirport().stream()
                .filter(a -> a.getAircraftOperatorId() == shadowJet.getId())
                .filter(a -> a.getAircraftTypeId() == aircraftType.getId())
                .filter(a -> a.getLocationAirportId() == locationAirport.getId())
                .findFirst();

        if (existingAircraft.isPresent()) {
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
            log.error("could not find available SJ-xxx reg no");
            throw new IllegalStateException("could not find available SJ-xxx reg no");
        }

        final Aircrafts.Aircraft newAircraft = world.aircrafts().create(aircraftType, newRegNo, locationAirport);
        newAircraft.setAircraftOperatorId(shadowJet.getId());
        log.info("new aircraft {} with reg no {} located at {} created", aircraftType.getIcao(), newRegNo, locationAirportIcao);
        return newAircraft;
    }

    private static String generateRandomSJxxxRegNo() {
        final String suffix = random.ints(3, 'A', 'Z' + 1)
                .mapToObj(c -> String.valueOf((char) c))
                .collect(Collectors.joining());

        return "SJ-" + suffix;
    }
}
