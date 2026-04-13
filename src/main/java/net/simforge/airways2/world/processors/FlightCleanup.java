package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class FlightCleanup {
    private static final Logger log = LoggerFactory.getLogger(FlightCleanup.class);
    private static long lastExecution;

    public static void process(final World world) {
        if (System.currentTimeMillis() - lastExecution < 3600000) {
            return;
        }
        lastExecution = System.currentTimeMillis();

        final int worldTime = world.getWorldTime();

        FlightMissions flightMissions = world.flightMissions();
        final Collection<FlightMissions.Mission> cancelled = flightMissions
                .filter(flightMissions.anyStatus(FlightMissions.Status.Cancelled))
                .filter(f -> f.getPlannedDepartureWorldTime() <= worldTime - Time.ONE_DAY) // todo ak3 this can be improved by putting it into new filters
                .toList();
        cancelled.forEach(f -> {
            // todo ak2 'event log cleanup refinement' - remove event-logs
            flightMissions.deleteById(f.getId());
            deleteTransportFlights(world, f);
        });

        final Collection<FlightMissions.Mission> finished = flightMissions
                .filter(flightMissions.anyStatus(FlightMissions.Status.Finished))
                .filter(f -> f.getActualArrivalWorldTime() <= worldTime - 10 * Time.ONE_DAY) // todo ak3 this can be improved by putting it into new filters
                .toList();
        finished.forEach(f -> {
            // todo ak2 'event log cleanup refinement' - remove event-logs
            flightMissions.deleteById(f.getId());
            deleteTransportFlights(world, f);
        });

        if (cancelled.size() > 0 || finished.size() > 0) {
            log.info("flight missions cleaned up - {} cancelled, {} finished", cancelled.size(), finished.size());
        }

        List<TransportFlights.Flight> brokenTransportFlights = world.transportFlights().all()
                .filter(tf -> flightMissions.byId(tf.getFlightMissionId()).isEmpty())
                .toList();
        brokenTransportFlights.forEach(tf -> deleteTransportFlight(world, tf));

        if (brokenTransportFlights.size() > 0) {
            log.warn("FOUND AND REMOVED {} BROKEN TRANSPORT FLIGHTS", brokenTransportFlights.size());
        }
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
