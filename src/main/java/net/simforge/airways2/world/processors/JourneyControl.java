package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.tools.Formatting;
import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.City2CityFlows;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.TransportFlights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class JourneyControl {
    private static final Logger log = LoggerFactory.getLogger(JourneyControl.class);

    private static final int MAX_STAY_AT_DESTINATION = 7 * Time.ONE_DAY;
    private static final int MIN_STAY_AT_DESTINATION = Time.ONE_DAY;

    private static final int TERMINAL_STATUS_DURATION = 3 * Time.ONE_DAY;

    private final World world;

    public JourneyControl(World world) {
        this.world = world;
    }

    public Journeys.Journey create(final City2CityFlows.Flow c2cFlow, final CabinLayout.Service service) {
        checkNotNull(c2cFlow);
        checkNotNull(service);

        final Journeys.Journey journey = world.journeys().create(
                Journeys.Status.LookingForTickets,
                c2cFlow.getFromCityId(),
                c2cFlow.getToCityId(),
                c2cFlow.getNextGroupSize(),
                service);
        journey.setHeartbeatTime(world.getWorldTime());
        return journey;
    }

    public void waitForCheckin(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(EnumSet.of(
                        Journeys.Status.LookingForTickets,
                        Journeys.Status.JustArrived)
                .contains(journey.getStatus()));

        journey.setStatus(Journeys.Status.WaitingForCheckIn);
        journey.setHeartbeatTime(world.getWorldTime());
    }

    public void board(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.WaitingForBoarding);

        journey.setStatus(Journeys.Status.OnBoard);
    }

    public void switchToReturnTrip(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.ItinerariesDone);

        journey.setReturningBack(true);

        final int fromCityId = journey.getFromCityId();
        final int toCityId = journey.getToCityId();
        journey.setFromCityId(toCityId);
        journey.setToCityId(fromCityId);

        journey.setStatus(Journeys.Status.LookingForTickets);
        journey.setAttemptCounter(0);
        journey.setHeartbeatTime(world.getWorldTime() + Tools.random(MIN_STAY_AT_DESTINATION, MAX_STAY_AT_DESTINATION));

        updateCity2CityFlowSuccessRate(journey, 0.004f);

        log.info("j/y #{} - switched for return trip, cities swapped, looking for tickets scheduled", journey.getId());
    }

    public void finish(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(journey.getStatus() == Journeys.Status.ItinerariesDone);

        journey.setStatus(Journeys.Status.Finished);
        journey.setHeartbeatTime(world.getWorldTime() + TERMINAL_STATUS_DURATION);

        updateCity2CityFlowSuccessRate(journey, 0.006f);

        log.info("j/y #{} - finished, cleanup scheduled", journey.getId());
    }

    public void tooLateToBoard(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(EnumSet.of(
                        Journeys.Status.WaitingForCheckIn,
                        Journeys.Status.WaitingForBoarding)
                .contains(journey.getStatus()));

        // todo ak1 'cancel journey safely' with removal all following tickets etc
        // todo ak0 'update stats'
        journey.setStatus(Journeys.Status.TooLateToBoard);
        journey.setHeartbeatTime(world.getWorldTime() + TERMINAL_STATUS_DURATION);

        log.info("j/y #{} - too late to board, cleanup scheduled", journey.getId());
    }

    public void couldNotFindTickets(final Journeys.Journey journey) {
        checkNotNull(journey);
        checkArgument(Journeys.Status.LookingForTickets == journey.getStatus());

        journey.setStatus(Journeys.Status.CouldNotFindTickets);
        journey.setHeartbeatTime(world.getWorldTime() + TERMINAL_STATUS_DURATION);

        updateCity2CityFlowSuccessRate(journey, -1f);

        log.info("j/y #{} - could not find tickets, cleanup scheduled", journey.getId());
    }

    public void scheduleDeboardingForAllOnBoardJourneys(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Deboarding);

        world.journeys()
                .filter(world.journeys().byTransportFlight1IdAndStatus(transportFlight.getId(), Journeys.Status.OnBoard))
                .forEach(this::scheduleDeboardingAtRandomTime);
    }

    private void scheduleDeboardingAtRandomTime(final Journeys.Journey journey) {
        journey.setStatus(Journeys.Status.WaitingForDeboarding);
        journey.setHeartbeatTime(world.getWorldTime() + (int) (Math.random() * TransportFlightHelper.DEBOARDING_DURATION));
    }

    // todo ak0 refactor - move to another class
    private void updateCity2CityFlowSuccessRate(final Journeys.Journey journey, final float deltaPercents) {
        // todo ak0 another direction!
        final Optional<City2CityFlows.Flow> flow = world.city2cityFlows().getFromCityIdToCityId(journey.getFromCityId(), journey.getToCityId());
        if (flow.isEmpty()) {
            log.warn("update c2c flows - {}->{} - no flow found", journey.getFromCityId(), journey.getToCityId());
            return;
        }

        final float originalSuccessRate = flow.get().getSuccessRate();
        final float successRateToItsLimit = deltaPercents > 0 ? 1.0f - originalSuccessRate : originalSuccessRate;
        final float successRateDelta = successRateToItsLimit * (deltaPercents/100);
        final float newSuccessRate = originalSuccessRate + successRateDelta;
        flow.get().setSuccessRate(newSuccessRate);

        log.info("update c2c flows - {}->{} - success rate update {}% - src {}, delta {}, new {}, new reread {}", journey.getFromCityId(), journey.getToCityId(), 
                 deltaPercents, 
                 Formatting.df7z.format(originalSuccessRate), 
                 Formatting.df7z.format(successRateDelta), 
                 Formatting.df7z.format(newSuccessRate),
                 Formatting.df7z.format(flow.get().getSuccessRate()));
    }
}
