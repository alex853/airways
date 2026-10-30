package net.simforge.airways2.world.processors;

import net.simforge.airways2.app.tools.FlightStats;
import net.simforge.airways2.app.tools.Timing;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static net.simforge.airways2.world.datamodel.EventsToProcess.Type.StartAutomaticDeboarding;

public class TransportFlightControl {
    private static final int CHECKIN_TICK = Time.ONE_MINUTE;
    private static final int BOARDING_TICK = Time.TICK;
    private static final int DEBOARDING_TICK = Time.TICK;
    // the deboarding must not last longer, the flight is finished when the window is over
    private static final int DEBOARDING_WINDOW = Time.ONE_HOUR;
    private static final int DEBOARDING_WINDOW_CACHE_TTL = 2 * Time.ONE_HOUR;

    private static final Logger log = LoggerFactory.getLogger(TransportFlightControl.class);

    private final World world;

    private final Map<String, CabinLayout> cabinLayouts = new TreeMap<>();

    // t/f id -> deboarding start time, filled lazily from the event log
    private final Map<Integer, Integer> deboardingStartTimes = new HashMap<>();

    public TransportFlightControl(final World world) {
        this.world = world;

        loadCabinLayouts();
    }

    private PaxManager paxManager() {
        return world.paxManager();
    }

    @SuppressWarnings("UnusedReturnValue")
    public TransportFlights.Flight createTransportFlight(FlightMissions.Mission flightMission) {
        return createTransportFlight(flightMission, chooseDefaultCabinLayout(flightMission));
    }

    public TransportFlights.Flight createTransportFlight(FlightMissions.Mission flightMission, CabinLayout cabinLayout) {
        return createTransportFlight(flightMission, null, cabinLayout);
    }

    public TransportFlights.Flight createTransportFlight(FlightMissions.Mission flightMission,
                                                         ScheduledFlights.Flight scheduledFlight) {
        return createTransportFlight(flightMission, scheduledFlight, chooseDefaultCabinLayout(flightMission));
    }

    public TransportFlights.Flight createTransportFlight(FlightMissions.Mission flightMission,
                                                         ScheduledFlights.Flight scheduledFlight,
                                                         CabinLayout cabinLayout) {
        checkNotNull(flightMission);
        checkNotNull(cabinLayout);

        final TransportFlights.Flight transportFlight = world.transportFlights().create(
                flightMission,
                scheduledFlight,
                cabinLayout);

        final int checkinStartsAt = TransportFlightHelper.calcCheckinStartTime(flightMission);
        transportFlight.setHeartbeatTime(checkinStartsAt);

        log.info("t/f #{} - created t/f for f/m #{}, s/f #{}", transportFlight.getId(), flightMission.getId(), scheduledFlight != null ? scheduledFlight.getId() : "///");

        return transportFlight;
    }

    /**
     * The cabin layout which a transport flight of an aircraft type gets by default, the seats of the type; for a type without a layout it is the same default as the one of
     * the transport flights (99 economy seats). It is for the estimates before there is a flight (the approximate income of ShadowJet).
     */
    public CabinLayout cabinLayoutOfType(final String aircraftTypeIcao) {
        final CabinLayout cabinLayout = cabinLayouts.get(aircraftTypeIcao);
        return cabinLayout != null ? cabinLayout : CabinLayout.Y(99);
    }

    private CabinLayout chooseDefaultCabinLayout(FlightMissions.Mission flightMission) {
        Aircrafts.Aircraft aircraft = world.aircrafts().byId(flightMission.getAircraftId()).orElseThrow();
        String aircraftType = world.aircraftTypes().byId(aircraft.getAircraftTypeId()).orElseThrow().getIcao();

        CabinLayout cabinLayout = cabinLayouts.get(aircraftType);

        if (cabinLayout == null) {
            FlightStats.event("aircraft cabin layout missing " + aircraftType);
            return CabinLayout.Y(99);
        }

        return cabinLayout;
    }

    public void obtainFlightTickets(final TransportFlights.Flight flight, final int tickets, final CabinLayout.Service service) {
        checkNotNull(flight);
        checkArgument(tickets >= 0);
        checkNotNull(service);

        final CabinLayout remainedTickets = flight.getRemainedTickets();
        flight.setRemainedTickets(remainedTickets.occupySeats(tickets, service));
    }

    // ---- the income of a transport flight: the sum of the price of its journeys, in TransportFlightExtras. Only the flights of the pilots have it (the PC flights);
    // the flights of the NPCs skip all of it. A flight without income has no record.

    boolean isPcFlight(final TransportFlights.Flight flight) {
        return world.flightMissions().byId(flight.getFlightMissionId())
                .map(mission -> mission.getCharacterMode() == FlightMissions.CharacterMode.PC)
                .orElse(false);
    }

    /**
     * The income of the transport flight, zero for a flight which has none (and for a flight of an NPC).
     */
    public int getIncome(final TransportFlights.Flight flight) {
        checkNotNull(flight);
        return world.transportFlightExtras().byTransportFlightId(flight.getId())
                .map(TransportFlightExtras.Extra::getIncome)
                .orElse(0);
    }

    /**
     * The income of the flight of a mission: the income of its transport flight, zero if the mission has no transport flight (a ferry flight). It is what the pay of the
     * pilot is taken from (PilotPay.incomeShare).
     */
    public int getIncomeOfMission(final FlightMissions.Mission mission) {
        checkNotNull(mission);
        return world.transportFlights().byFlightMissionId(mission.getId())
                .map(this::getIncome)
                .orElse(0);
    }

    /**
     * A journey is booked to the flight: its price is added to the income of the flight. Nothing for a flight of an NPC and for a journey without a price.
     */
    public void addIncome(final TransportFlights.Flight flight, final int price) {
        checkNotNull(flight);
        checkArgument(price >= 0);
        if (price == 0 || !isPcFlight(flight)) {
            return;
        }
        setIncome(flight, (long) getIncome(flight) + price);
    }

    /**
     * A journey leaves the flight: its price is taken off the income of the flight. The income does not go below zero.
     */
    public void subtractIncome(final TransportFlights.Flight flight, final int price) {
        checkNotNull(flight);
        checkArgument(price >= 0);
        if (price == 0 || !isPcFlight(flight)) {
            return;
        }
        setIncome(flight, Math.max(0L, (long) getIncome(flight) - price));
    }

    /**
     * The boarding is over: the income is the sum of the price of the journeys which are on board, what really boarded. It overwrites what the bookings have added.
     * Nothing for a flight of an NPC.
     */
    public void recalculateIncome(final TransportFlights.Flight flight) {
        checkNotNull(flight);
        if (!isPcFlight(flight)) {
            return;
        }
        final long income = paxManager().journeysOf(flight, Journeys.Status.OnBoard).stream()
                .mapToLong(Journeys.Journey::getPrice)
                .sum();
        setIncome(flight, income);
        log.info("t/f #{} - the income is {} (the journeys which are on board)", flight.getId(), getIncome(flight));
    }

    // a record is made when the income is more than zero; the income which does not fit into three bytes is the biggest one which fits
    private void setIncome(final TransportFlights.Flight flight, final long income) {
        final int value = (int) Math.min(income, TransportFlightExtras.MAX_INCOME);
        if (value < income) {
            log.warn("t/f #{} - the income {} does not fit into 3 bytes, {} is kept", flight.getId(), income, value);
        }
        final Optional<TransportFlightExtras.Extra> extra = world.transportFlightExtras().byTransportFlightId(flight.getId());
        if (extra.isPresent()) {
            extra.get().setIncome(value);
        } else if (value > 0) {
            world.transportFlightExtras().create(flight.getId()).setIncome(value);
        }
    }

    public void releaseFlightTickets(final TransportFlights.Flight flight, final int tickets, final CabinLayout.Service service) {
        checkNotNull(flight);
        checkArgument(tickets >= 0);
        checkNotNull(service);

        final CabinLayout remainedTickets = flight.getRemainedTickets();
        flight.setRemainedTickets(remainedTickets.releaseSeats(tickets, service));
    }

    public boolean ifCheckInTimeComes(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Scheduled);

        final FlightMissions.Mission flightMission = world.flightMissions().byId(transportFlight.getFlightMissionId()).orElseThrow();
        final int checkinStartTime = TransportFlightHelper.calcCheckinStartTime(flightMission);
        return checkinStartTime < world.getWorldTime();
    }

    public void startCheckIn(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Scheduled);

        transportFlight.setStatus(TransportFlights.Status.CheckIn);
        transportFlight.setHeartbeatTime(world.getWorldTime() + CHECKIN_TICK);

        log.info("t/f #{} - check-in started", transportFlight.getId());
    }

    public void continueCheckIn(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.CheckIn);

        transportFlight.setHeartbeatTime(world.getWorldTime() + CHECKIN_TICK);
    }

    public void increasePaxCheckedIn(TransportFlights.Flight transportFlight, int groupSize) {
        checkNotNull(transportFlight);
        checkArgument(TransportFlightHelper.flightStatusAllowsToCheckIn(transportFlight.getStatus()));
        checkArgument(groupSize > 0);

        transportFlight.setPaxCheckedIn(transportFlight.getPaxCheckedIn() + groupSize);
    }

    public boolean areAllPaxCheckedIn(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.CheckIn);

        if (transportFlight.getRemainedTickets().getTotal() > 0) {
            // if some tickets still available then we can't tell that all PAX checked-in even if all PAX with tickets already checked-in
            // this will lead to a case that check-in will continue to be open till the end of check-in window if there are some tickets are still available
            return false;
        }

        return transportFlight.getPaxCheckedIn() >= transportFlight.getSoldTickets();
    }

    public boolean isCheckInFinishTimePassed(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.CheckIn);

        final FlightMissions.Mission flightMission = world.flightMissions().byId(transportFlight.getFlightMissionId()).orElseThrow();
        final int checkinEndTime = TransportFlightHelper.calcCheckinEndTime(flightMission);
        return checkinEndTime < world.getWorldTime();
    }

    public void waitForBoarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.CheckIn);

        transportFlight.setStatus(TransportFlights.Status.WaitingForBoarding);
        transportFlight.setHeartbeatTime(0);

        log.info("t/f #{} - check-in ended, waiting for boarding", transportFlight.getId());
    }

    public void startBoarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(TransportFlightHelper.flightStatusAllowsToStartBoarding(transportFlight.getStatus()));

        transportFlight.setStatus(TransportFlights.Status.Boarding);
        transportFlight.setHeartbeatTime(world.getWorldTime() + BOARDING_TICK);

        paxManager().boarding(transportFlight).start();

        log.info("t/f #{} - boarding started", transportFlight.getId());
    }

    public void continueBoarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);

        paxManager().boarding(transportFlight).proceed();

        transportFlight.setHeartbeatTime(world.getWorldTime() + BOARDING_TICK);
    }

    public boolean areAllPaxBoarded(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        // the flight which is tracked via VATSIM starts moving whenever it likes, the boarding does not end by itself (see staysOpenUntilDeparture)
        if (staysOpenUntilDeparture(transportFlight)) {
            return false;
        }
        // by the journeys, not by PAX on board: the counter of the boarding is not a reliable sign of the end of the boarding. Everybody is on board, but the boarding stays
        // open for a few minutes more for the journeys which knock late (PaxManager.Boarding.wasFinishTimePassed)
        return paxManager().areAllBoarded(transportFlight) && paxManager().boarding(transportFlight).wasFinishTimePassed();
    }

    /**
     * The flights which are tracked via VATSIM (the flights of the pilots who are not users of the game: PC mode and no user) start moving independently of the state of the
     * flight in the game, the movement interrupts the boarding (whenFlightDepartsFromGate). The boarding of such a flight starts when it is provided, and it does not end by the
     * time or by the boarding of everybody who has a ticket: if the aircraft stands at the gate for 40 or 50 minutes, the journeys check in and board during all that time
     * (decided 2026-10-30; the journeys come one after another, see ShadowJetLogic and JourneyWakeCurve). The flights of the users and the AI ones end the boarding by the usual rules.
     */
    public boolean staysOpenUntilDeparture(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        return world.flightMissions().byId(transportFlight.getFlightMissionId())
                .map(mission -> mission.getCharacterMode() == FlightMissions.CharacterMode.PC && mission.getUserId() == 0)
                .orElse(false);
    }

    public boolean isBoardingFinishTimePassed(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);

        if (staysOpenUntilDeparture(transportFlight)) {
            return false;
        }
        return paxManager().boarding(transportFlight).wasFinishTimePassed();
    }

    public void waitForDeparture(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Boarding);

        paxManager().boarding(transportFlight).finish();

        transportFlight.setStatus(TransportFlights.Status.WaitingForDeparture);
        transportFlight.setHeartbeatTime(0);

        log.info("t/f #{} - boarding ended, waiting for departure", transportFlight.getId());
    }

    public void whenFlightDepartsFromGate(final TransportFlights.Flight transportFlight) {
        // the pilot is not obliged to wait for the end of the boarding, the journeys which are not on board are too late
        if (transportFlight.getStatus() == TransportFlights.Status.Boarding) {
            paxManager().boarding(transportFlight).finish();
        }

        transportFlight.setStatus(TransportFlights.Status.Departure);

        log.info("t/f #{} - departed", transportFlight.getId());
    }

    public void whenFlightTakeoffs(final TransportFlights.Flight transportFlight) {
        transportFlight.setStatus(TransportFlights.Status.Flying);
        // the passengers who are on board at the takeoff are the ones who are carried
        transportFlight.setPaxCarried(transportFlight.getPaxOnBoard());

        log.info("t/f #{} - flying", transportFlight.getId());
    }

    public void whenFlightLands(final TransportFlights.Flight transportFlight) {
        transportFlight.setStatus(TransportFlights.Status.Arrival);

        log.info("t/f #{} - arrival", transportFlight.getId());
    }

    public void whenFlightArrivesToGate(final TransportFlights.Flight transportFlight) {
        transportFlight.setStatus(TransportFlights.Status.WaitingForDeboarding);

        log.info("t/f #{} - arrived, waiting for deboarding", transportFlight.getId());
    }

    public void scheduleAutomaticDeboarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.WaitingForDeboarding);

        world.eventsToProcess().sendEvent(StartAutomaticDeboarding, transportFlight.getId(), world.getWorldTime() + TransportFlightHelper.AUTOMATIC_DEBOARDING_DELAY);

        log.info("t/f #{} - automatic deboarding scheduled", transportFlight.getId());
    }

    public void startDeboarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.WaitingForDeboarding);

        transportFlight.setStatus(TransportFlights.Status.Deboarding);
        transportFlight.setHeartbeatTime(world.getWorldTime() + DEBOARDING_TICK);

        world.log(EventLog.EventType.TransportFlightDeboardingStarted, EventLog.id(transportFlight));
        deboardingStartTimes.put(transportFlight.getId(), world.getWorldTime());

        // PAX on board is the sum of the journeys on board, it is set before the journeys leave the 'on board' status
        transportFlight.setPaxOnBoard(paxManager().countPaxOnBoard(transportFlight));

        world.journeyControl().scheduleDeboardingForAllOnBoardJourneys(transportFlight);

        paxManager().deboarding(transportFlight).start();

        log.info("t/f #{} - deboarding started, PAX on board {}", transportFlight.getId(), transportFlight.getPaxOnBoard());
    }

    public void decreasePaxOnBoard(TransportFlights.Flight transportFlight, int groupSize) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Deboarding);
        checkArgument(groupSize > 0);

        transportFlight.setPaxOnBoard(Math.max(0, transportFlight.getPaxOnBoard() - groupSize));
    }

    public void continueDeboarding(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Deboarding);

        paxManager().deboarding(transportFlight).proceed();

        transportFlight.setHeartbeatTime(world.getWorldTime() + DEBOARDING_TICK);
    }

    public boolean areAllPaxDeboarded(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        return transportFlight.getPaxOnBoard() == 0;
    }

    /**
     * The deboarding must not last longer than DEBOARDING_WINDOW. The start time is taken from the event log (cached),
     * the flight without the start event is considered as expired.
     */
    public boolean deboardingWindowExpired(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);

        final int now = world.getWorldTime();
        deboardingStartTimes.values().removeIf(startTime -> now - startTime > DEBOARDING_WINDOW_CACHE_TTL);

        // null is not stored in the map, the flight without the start event is expired
        final Integer startTime = deboardingStartTimes.computeIfAbsent(transportFlight.getId(), this::findDeboardingStartTime);

        return startTime == null || now - startTime >= DEBOARDING_WINDOW;
    }

    // null if there is no start event
    private Integer findDeboardingStartTime(final int transportFlightId) {
        try (final Timing.Timer ignored = Timing.label("TFControl - findDeboardingStartTime # event log scan")) {
            return world.eventLog()
                    .byObject(EventLog.ObjectType.TransportFlight.code(), transportFlightId)
                    .filter(event -> event.getType() == EventLog.EventType.TransportFlightDeboardingStarted)
                    .map(EventLog.Event::getTime)
                    .max(Integer::compare)
                    .orElse(null);
        }
    }

    public void finish(final TransportFlights.Flight transportFlight) {
        checkNotNull(transportFlight);
        checkArgument(transportFlight.getStatus() == TransportFlights.Status.Deboarding);

        if (!areAllPaxDeboarded(transportFlight)) {
            log.warn("t/f #{} - finished with PAX {} still on board", transportFlight.getId(), transportFlight.getPaxOnBoard());
            FlightStats.event("t/f - finished with PAX still on board");
        }

        transportFlight.setStatus(TransportFlights.Status.Finished);
        transportFlight.setHeartbeatTime(0);
        transportFlight.setPaxOnBoard(0);

        paxManager().deboarding(transportFlight).finish();
        deboardingStartTimes.remove(transportFlight.getId());

        log.info("t/f #{} - finished", transportFlight.getId());
    }

    /**
     * Cancels the transport flight of the mission, if there is one: all its journeys are unloaded forcefully (they go back to the cities
     * where they were before) and the flight becomes Cancelled.
     */
    public void cancelTransportFlightIfExists(final FlightMissions.Mission mission) {
        checkNotNull(mission);

        final Optional<TransportFlights.Flight> transportFlightO = world.transportFlights().byFlightMissionId(mission.getId());
        if (transportFlightO.isEmpty()) {
            return;
        }

        final TransportFlights.Flight transportFlight = transportFlightO.get();
        unloadJourneysForcefullyFromActiveFlight(transportFlight);
        transportFlight.setStatus(TransportFlights.Status.Cancelled);

        log.warn("f/m #{}, t/f #{}, PAX {} - flight cancelled, all PAX unloaded", mission.getId(), transportFlight.getId(), transportFlight.getPaxOnBoard());
    }

    // Active flight is expectedly transportFlight1Id for all the journeys
    public void unloadJourneysForcefullyFromActiveFlight(TransportFlights.Flight transportFlight) {
        try (final Timing.Timer ignored = Timing.label("TFControl - unloadJourneysForcefully # journeys scan")) {
            world.journeys()
                    .filter(world.journeys().byTransportFlight1Id(transportFlight.getId()))
                    .forEach(journey -> world.journeyControl().resetJourneyForcefully(journey));
        }

        transportFlight.setPaxOnBoard(0);
        paxManager().dropState(transportFlight);

        log.warn("t/f #{} - unloaded forcefully, set PAX on board to 0", transportFlight.getId());
    }

    private void loadCabinLayouts() {
        Properties properties = new Properties();

        try {
            InputStream stream = TransportFlightControl.class.getResourceAsStream("/cabin-layouts.properties");
            properties.load(stream);
        } catch (IOException e) {
            log.error("Cabin layouts - unable to load the file", e);
            return;
        }

        log.info("Cabin layouts - the file loaded, keeping {} entries", properties.size());

        properties.forEach((key, value) -> {
            String type = (String) key;
            String layoutStr = (String) value;

            try {
                CabinLayout cabinLayout = CabinLayout.parseString(layoutStr);
                cabinLayouts.put(type, cabinLayout);
//                log.info("Cabin layouts - {} = {} - parsed and added {}", type, layoutStr, cabinLayout.toString());
            } catch (RuntimeException ignored) {
                log.error("Cabin layouts - {} = {} - UNABLE TO PARSE", type, layoutStr);
            }
        });
    }
}
