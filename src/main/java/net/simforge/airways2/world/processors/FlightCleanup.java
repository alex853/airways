package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.stream.Stream;

public class FlightCleanup {
    private static final Logger log = LoggerFactory.getLogger(FlightCleanup.class);
    private static long lastExecution;

    public static void process(final World world) {
        if (System.currentTimeMillis() - lastExecution < 60000) {
            return;
        }
        lastExecution = System.currentTimeMillis();

        long cancelledOnPreflight = findFlightMissions(world, FlightMissions.Status.Cancelled, 15 * Time.ONE_MINUTE)
                .filter(f -> world.flightMissionControl().checkAndRemoveIfScheduledForQuickRemoval(f))
                .peek(f -> deleteFlightMission(world, f))
                .count();

        long cancelled = findFlightMissions(world, FlightMissions.Status.Cancelled, Time.ONE_DAY)
                .peek(f -> deleteFlightMission(world, f))
                .count();

        long finished = findFlightMissions(world, FlightMissions.Status.Finished, 10 * Time.ONE_DAY)
                .peek(f -> deleteFlightMission(world, f))
                .count();

        if (cancelledOnPreflight + cancelled + finished > 0) {
            log.info("flight cleanup - {} cancelled on preflight, {} cancelled, {} finished", cancelledOnPreflight, cancelled, finished);
        }

        long brokenTransportFlights = world.transportFlights().all()
                .filter(tf -> world.flightMissions().byId(tf.getFlightMissionId()).isEmpty())
                .peek(tf -> deleteTransportFlight(world, tf))
                .count();

        if (brokenTransportFlights > 0) {
            log.warn("FOUND AND REMOVED {} BROKEN TRANSPORT FLIGHTS", brokenTransportFlights);
        }
    }

    private static Stream<FlightMissions.Mission> findFlightMissions(World world, FlightMissions.Status status, int time) {
        return world.flightMissions()
                .filter(world.flightMissions().anyStatus(status))
                .filter(f -> f.getPlannedDepartureWorldTime() <= world.getWorldTime() - time); // todo ak1 this can be improved by putting it into new filters
    }

    private static void deleteFlightMission(World world, FlightMissions.Mission f) {
        // todo ak2 'event log cleanup refinement' - remove event-logs
        deleteTransportFlights(world, f);
        world.flightMissions().deleteById(f.getId());
    }

    private static void deleteTransportFlights(final World world, final FlightMissions.Mission f) {
        while (true) {
            final Optional<TransportFlights.Flight> tf = world.transportFlights().byFlightMissionId(f.getId());
            if (tf.isEmpty()) {
                break;
            }
            deleteTransportFlight(world, tf.get());
        }
    }

    private static void deleteTransportFlight(World world, TransportFlights.Flight tf1) {
        world.scheduledFlights().byId(tf1.getScheduledFlightId())
                .ifPresent(sf -> world.scheduledFlights().deleteById(sf.getId()));
        // todo ak1 t/f deletion means that tickets/passengers have to be processed somehow
        world.transportFlights().deleteById(tf1.getId());
    }
}
