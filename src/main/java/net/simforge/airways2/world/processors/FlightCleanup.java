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

        long cancelledOnPreflight = findFlightMissions(world, 15 * Time.ONE_MINUTE, FlightMissions.Status.Cancelled)
                .filter(f -> world.flightMissionControl().checkAndRemoveIfScheduledForQuickRemoval(f))
                .peek(f -> deleteFlightMission(world, f))
                .count();

        long cancelled = findFlightMissions(world, Time.ONE_DAY, FlightMissions.Status.Cancelled)
                .peek(f -> deleteFlightMission(world, f))
                .count();

        long finished = findFlightMissions(world, 10 * Time.ONE_DAY, FlightMissions.Status.Finished)
                .peek(f -> deleteFlightMission(world, f))
                .count();

        long outdated = findFlightMissions(world, 3 * Time.ONE_DAY,
                FlightMissions.Status.Dispatched, FlightMissions.Status.Preflight, FlightMissions.Status.Departure,
                FlightMissions.Status.Flying, FlightMissions.Status.Arrival, FlightMissions.Status.Postflight)
                .peek(f -> deleteFlightMission(world, f))
                .count();

        if (cancelledOnPreflight + cancelled + finished + outdated > 0) {
            log.info("flight cleanup - {} cancelled on preflight, {} cancelled, {} finished, {} outdated", cancelledOnPreflight, cancelled, finished, outdated);
        }

        long brokenTransportFlights = world.transportFlights().all()
                .filter(tf -> world.flightMissions().byId(tf.getFlightMissionId()).isEmpty())
                .peek(tf -> deleteTransportFlight(world, tf))
                .count();

        if (brokenTransportFlights > 0) {
            log.warn("FOUND AND REMOVED {} BROKEN TRANSPORT FLIGHTS", brokenTransportFlights);
        }
    }

    private static Stream<FlightMissions.Mission> findFlightMissions(World world, int time, FlightMissions.Status... statuses) {
        return world.flightMissions()
                .filter(world.flightMissions().anyStatus(statuses))
                .filter(f -> f.getPlannedDepartureWorldTime() <= world.getWorldTime() - time);
    }

    private static void deleteFlightMission(World world, FlightMissions.Mission f) {
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
        world.transportFlights().deleteById(tf1.getId());
    }
}
