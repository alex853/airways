package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class FlightCleanup {
    private static final Logger log = LoggerFactory.getLogger(FlightCleanup.class);
    private static long lastExecution;

    // the removed objects of the current run, they are logged by lines of up to 10 ids: "t/f #10208, f/m #6432, s/f #77 removed"
    private static final int IDS_PER_LINE = 10;
    static final List<String> removed = new ArrayList<>();

    public static void process(final World world) {
        if (System.currentTimeMillis() - lastExecution < 60000) {
            return;
        }
        lastExecution = System.currentTimeMillis();

        try {
            cleanup(world);
        } finally {
            logRemoved();
        }
    }

    private static void cleanup(final World world) {
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

        long outdated = findFlightMissions(world, 36 * Time.ONE_HOUR,
                FlightMissions.Status.Dispatched, FlightMissions.Status.Preflight, FlightMissions.Status.Departure,
                FlightMissions.Status.Flying, FlightMissions.Status.Arrival, FlightMissions.Status.Postflight)
                .peek(f -> deleteFlightMission(world, f))
                .count();

        if (cancelledOnPreflight + cancelled + finished + outdated > 0) {
            log.info("{} cancelled on preflight, {} cancelled, {} finished, {} outdated", cancelledOnPreflight, cancelled, finished, outdated);
        }

        long brokenTransportFlights = world.transportFlights().all()
                .filter(tf -> world.flightMissions().byId(tf.getFlightMissionId()).isEmpty())
                .peek(tf -> deleteTransportFlight(world, tf))
                .count();

        if (brokenTransportFlights > 0) {
            log.warn("FOUND AND REMOVED {} ORPHANED TRANSPORT FLIGHTS", brokenTransportFlights);
        }
    }

    private static Stream<FlightMissions.Mission> findFlightMissions(World world, int time, FlightMissions.Status... statuses) {
        return world.flightMissions()
                .filter(world.flightMissions().anyStatus(statuses))
                .filter(f -> f.getPlannedDepartureWorldTime() <= world.getWorldTime() - time);
    }

    private static void deleteFlightMission(World world, FlightMissions.Mission f) {
        deleteTransportFlights(world, f);
        world.pilotContracts().deleteByFlightMissionId(f.getId());
        world.flightMissions().deleteById(f.getId());
        removed.add("f/m #" + f.getId());
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
                .ifPresent(sf -> {
                    world.scheduledFlights().deleteById(sf.getId());
                    removed.add("s/f #" + sf.getId());
                });

        final List<Journeys.Journey> journeys = world.journeys()
                .filter(world.journeys().byAnyTransportFlightId(tf1.getId()))
                .toList();
        final boolean missionExists = world.flightMissions().byId(tf1.getFlightMissionId()).isPresent();
        for (final Journeys.Journey journey : journeys) {
            final Journeys.Status status = journey.getStatus();
            if (status == Journeys.Status.Orphaned
                    || status == Journeys.Status.Finished
                    || status == Journeys.Status.CouldNotFindTickets) {
                continue;
            }

            if (journey.getTransportFlight1Id() == tf1.getId()) {
                // the journey is reset while the transport flight and its mission still exist, they are required for that
                if (missionExists) {
                    log.warn("t/f #{} - j/y #{} in {} status is reset to looking for tickets", tf1.getId(), journey.getId(), status);
                    world.journeyControl().resetJourneyForcefully(journey);
                } else {
                    log.warn("t/f #{} - j/y #{} in {} status, mission not found, orphaned", tf1.getId(), journey.getId(), status);
                    world.journeyControl().orphaned(journey);
                }
            } else {
                log.warn("t/f #{} - j/y #{} in {} status, t/f2 reference is cleared", tf1.getId(), journey.getId(), status);
                journey.setTransportFlight2Id(0);
                journey.setPrice2(0);
            }
        }

        world.transportFlightExtras().deleteByTransportFlightId(tf1.getId());
        world.transportFlights().deleteById(tf1.getId());
        removed.add("t/f #" + tf1.getId());
    }

    static void logRemoved() {
        for (int i = 0; i < removed.size(); i += IDS_PER_LINE) {
            log.info("{} removed", String.join(", ", removed.subList(i, Math.min(i + IDS_PER_LINE, removed.size()))));
        }
        removed.clear();
    }
}
