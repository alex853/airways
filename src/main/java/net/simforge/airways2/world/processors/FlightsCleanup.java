package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;

public class FlightsCleanup {
    private static final Logger log = LoggerFactory.getLogger(FlightsCleanup.class);

    public static void process(final World world) {
        final int worldTime = world.getWorldTime();

        final Collection<FlightMissions.Mission> cancelled = world.flightMissions()
                .filter(f -> f.getStatus() == FlightMissions.Status.Cancelled
                        && f.getPlannedDepartureWorldTime() <= worldTime - 1 * Time.ONE_DAY);
        cancelled.forEach(f -> {
            world.flightMissions().deleteById(f.getId());
            removeTransportFlights(world, f);
        });

        final Collection<FlightMissions.Mission> finished = world.flightMissions()
                .filter(f -> f.getStatus() == FlightMissions.Status.Finished
                        && f.getActualArrivalWorldTime() <= worldTime - 10 * Time.ONE_DAY);
        finished.forEach(f -> {
            world.flightMissions().deleteById(f.getId());
            removeTransportFlights(world, f);
        });

        if (cancelled.size() > 0 || finished.size() > 0) {
            log.info("flight missions cleaned up - {} cancelled, {} finished", cancelled.size(), finished.size());
        }
    }

    private static void removeTransportFlights(final World world, final FlightMissions.Mission f) {
        while (true) {
            final Optional<TransportFlights.Flight> tf = world.transportFlights().byFlightMissionId(f.getId());
            if (tf.isEmpty()) {
                break;
            }
            world.transportFlights().deleteById(tf.get().getId()); // todo ak2 t/f deletion means that tickets/passengers have to be processed somehow
        }
    }
}
