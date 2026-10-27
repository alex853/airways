package net.simforge.airways2.world.processors;

import net.simforge.airways2.tools.Tools;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.AirportHubs;
import net.simforge.airways2.world.computations.PilotLiving;
import net.simforge.airways2.world.computations.PilotRest;
import net.simforge.airways2.world.computations.PilotTravel;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.BankTransactions;
import net.simforge.airways2.world.datamodel.EventLog;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.world.datamodel.PilotLogbook;
import net.simforge.airways2.world.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Changes of the state and of the location of persons (pilots). Every change is logged as 'p/s #id'.
 * <p>
 * The person of a flight is the person of the user of the mission, missions without a user (NPC) have no person yet. A user without
 * a person is not an error for the flight, it is only logged.
 */
public class PersonControl {
    private static final Logger log = LoggerFactory.getLogger(PersonControl.class);

    private final World world;

    public PersonControl(final World world) {
        this.world = world;
    }

    // ---- the flight of the person: Idle at the airport -> OnDuty at start -> no location in the flight -> airport at landing -> Idle at finish

    /**
     * The flight is started: the pilot goes on duty.
     */
    public void onMissionStarted(final FlightMissions.Mission mission) {
        findPerson(mission).ifPresent(person -> {
            if (person.getState() != Persons.State.Idle) {
                log.warn("p/s #{} - f/m #{} is started while the person is {}, not Idle", person.getId(), mission.getId(), person.getState());
            }
            setState(person, Persons.State.OnDuty, "f/m #" + mission.getId() + " started");
            // the duty is counted from the start of the flight, the preflight is in it
            person.setDutyStartTime(world.getWorldTime());
            // the setting of the pilot is the setting of this flight from now on
            mission.setAutoFinish(person.isAutoFinish());
        });
    }

    /**
     * The aircraft took off: the pilot is nowhere until the landing.
     */
    public void onMissionTakeoff(final FlightMissions.Mission mission) {
        findPerson(mission).ifPresent(person ->
                setLocation(person, Persons.LocationType.None, 0, "f/m #" + mission.getId() + " took off"));
    }

    /**
     * The aircraft landed: the pilot is at the landing airport.
     */
    public void onMissionLanding(final FlightMissions.Mission mission, final Airports.Airport landingAirport) {
        findPerson(mission).ifPresent(person ->
                setLocation(person, Persons.LocationType.Airport, landingAirport.getId(), "f/m #" + mission.getId() + " landed"));
    }

    /**
     * The flight is finished: the pilot is free and the flight goes to the logbook of the pilot.
     */
    public void onMissionFinished(final FlightMissions.Mission mission) {
        findPerson(mission).ifPresent(person -> {
            setState(person, Persons.State.Idle, "f/m #" + mission.getId() + " finished");
            writeLogbook(person, mission);
            person.setDutyStartTime(0);
        });
    }

    /**
     * The flight is cancelled after the start: the pilot which is on duty is free and is at the airport where the aircraft is parked now.
     * A person who is not on duty (the flight was not started, so the person was not touched by it) is left as it is.
     */
    public void onMissionCancelled(final FlightMissions.Mission mission, final int airportId) {
        findPerson(mission)
                .filter(person -> person.getState() == Persons.State.OnDuty)
                .ifPresent(person -> {
                    final String reason = "f/m #" + mission.getId() + " cancelled";
                    setState(person, Persons.State.Idle, reason);
                    setLocation(person, Persons.LocationType.Airport, airportId, reason);
                    person.setDutyStartTime(0);
                });
    }

    private void writeLogbook(final Persons.Person person, final FlightMissions.Mission mission) {
        final int blocksOff = mission.getActualDepartureWorldTime();
        final int blocksOn = mission.getActualArrivalWorldTime();
        if (blocksOff < Time.START_TIME_EPOCH_SECONDS || blocksOn < blocksOff || blocksOn - blocksOff > 1530 * Time.ONE_MINUTE) {
            log.warn("p/s #{} - f/m #{} is not written to the logbook, its blocks off {} and blocks on {} are not valid", person.getId(), mission.getId(), blocksOff, blocksOn);
            return;
        }

        final int toAirportId = mission.getActualLandingAirportId() > 0 ? mission.getActualLandingAirportId() : mission.getDestinationAirportId();

        final PilotLogbook.Entry entry = world.pilotLogbook().create(
                person.getId(),
                mission.getAircraftId(),
                mission.getDepartureAirportId(),
                toAirportId,
                blocksOff,
                blocksOn);

        // For now every flight is IFR (all the flight plans are filed in SimBrief), so IFR time is the whole flight time. VFR flights are to come later,
        // the source of the flight rules is not decided yet.
        entry.setIfr(true);
        // the duty: from the start of the flight to its finish (now), if the start is known (a flight started before the duty was kept has none)
        if (person.getDutyStartTime() > 0 && world.getWorldTime() >= person.getDutyStartTime()) {
            entry.setDuty(person.getDutyStartTime(), world.getWorldTime());
        }
        entry.setNightTakeoff(mission.isNightTakeoff());
        entry.setNightLanding(mission.isNightLanding());
        // seconds to hours, the storage rounds it to a tenth; the night time cannot be longer than the flight time
        entry.setNightTime(Math.min(entry.getFlightTime(), mission.getNightMinutes() / 60.0f));

        log.info("p/s #{} - logbook entry #{}: {} -> {}, {} min, f/m #{}", person.getId(), entry.getId(),
                world.airports().getIcao(entry.getFromAirportId()).orElse("#" + entry.getFromAirportId()),
                world.airports().getIcao(entry.getToAirportId()).orElse("#" + entry.getToAirportId()),
                entry.getDurationMinutes(), mission.getId());
    }

    // ---- sleep and the awake credit (PilotRest): Idle -> Resting at sleep, Resting -> Idle at wake up

    /**
     * The credit has not been started for the person yet (the person is older than the credit): it gets the initial credit from this moment.
     */
    public boolean awakeCreditIsNotStarted(final Persons.Person person) {
        return person.getCreditTime() == 0;
    }

    public void startAwakeCredit(final Persons.Person person) {
        if (!awakeCreditIsNotStarted(person)) {
            return;
        }
        person.setAwakeCredit(PilotRest.INITIAL_CREDIT_MINUTES, world.getWorldTime());
        log.info("p/s #{} - awake credit started, {} min", person.getId(), PilotRest.INITIAL_CREDIT_MINUTES);
    }

    /**
     * The pilot goes to sleep: the credit is fixed at this moment and stops falling. Only a free (Idle) pilot can go to sleep.
     */
    public void startSleep(final Persons.Person person) {
        startSleep(person, "sleep");
    }

    private void startSleep(final Persons.Person person, final String reason) {
        if (person.getState() != Persons.State.Idle) {
            throw new IllegalStateException("only an idle pilot can go to sleep, the pilot is " + person.getState());
        }
        startAwakeCredit(person);
        final int now = world.getWorldTime();
        final int credit = PilotRest.creditNow(person.getAwakeCreditMinutes(), person.getCreditTime(), now);
        person.setAwakeCredit(credit, now);
        setState(person, Persons.State.Resting, reason);
        // the pilot wakes up by itself at a random moment, the heartbeat is that moment (PersonProcessor)
        // a pilot in debt sleeps longer, see PilotRest.extraSleepMinutes
        final int sleepMinutes = Tools.random(PilotRest.AUTO_WAKE_UP_MIN_MINUTES, PilotRest.AUTO_WAKE_UP_MAX_MINUTES) + PilotRest.extraSleepMinutes(credit);
        person.setHeartbeatTime(now + sleepMinutes * Time.ONE_MINUTE);
        log.info("p/s #{} - sleep started, awake credit {} min, wakes up by itself in {} min", person.getId(), credit, sleepMinutes);
    }

    /**
     * The pilot goes to sleep by itself: the time which was scheduled when the pilot became free has come (see scheduleAutoSleep).
     */
    public void fallAsleepByItself(final Persons.Person person) {
        startSleep(person, "fell asleep by itself");
    }

    /**
     * A free pilot goes to sleep by itself at the moment which is set now (the heartbeat of the person): the earlier of a day of 15 to 17 hours and the end of
     * the credit plus a debt of 3 to 5 hours (PilotRest.autoSleepDelayMinutes). It is set when the pilot becomes free and cleared when the pilot is not free.
     */
    private void scheduleAutoSleep(final Persons.Person person) {
        final int now = world.getWorldTime();
        final int credit = awakeCreditIsNotStarted(person)
                ? PilotRest.INITIAL_CREDIT_MINUTES
                : PilotRest.creditNow(person.getAwakeCreditMinutes(), person.getCreditTime(), now);
        final int delay = PilotRest.autoSleepDelayMinutes(credit,
                Tools.random(PilotRest.AUTO_SLEEP_DAY_MIN_MINUTES, PilotRest.AUTO_SLEEP_DAY_MAX_MINUTES),
                Tools.random(PilotRest.AUTO_SLEEP_DEBT_MIN_MINUTES, PilotRest.AUTO_SLEEP_DEBT_MAX_MINUTES));
        person.setHeartbeatTime(now + delay * Time.ONE_MINUTE);
        log.info("p/s #{} - awake credit {} min, falls asleep by itself in {} min if it stays free", person.getId(), credit, delay);
    }

    /**
     * The free pilots who have no falling asleep scheduled (they became free before it existed, or a person was created free) get one. For the start of the
     * application.
     */
    public void scheduleAutoSleepOfIdlePersons() {
        world.persons().idleWithoutHeartbeat().forEach(this::scheduleAutoSleep);
    }

    /**
     * The pilot wakes up at the will of the user: the sleep (from the moment the credit was fixed) is added to the credit. Only a sleeping pilot can wake up.
     */
    public void wakeUp(final Persons.Person person) {
        wakeUp(person, "wake up");
    }

    /**
     * The pilot wakes up by itself: the time which was scheduled when the pilot fell asleep has come.
     */
    public void wakeUpByItself(final Persons.Person person) {
        wakeUp(person, "woke up by itself");
    }

    /**
     * The sleeping pilots who have no wake up scheduled (they fell asleep before it was scheduled, the application knows nothing about them) get one: the
     * random moment from 7.5 to 10 hours after the fall asleep, or the next step of the world if that moment has passed already. For the start of the application.
     */
    public void scheduleWakeUpOfSleepingPersons() {
        final int now = world.getWorldTime();
        world.persons().sleepingWithoutHeartbeat().forEach(person -> {
            // the credit of a sleeping person is the one it fell asleep with
            final int sleepMinutes = Tools.random(PilotRest.AUTO_WAKE_UP_MIN_MINUTES, PilotRest.AUTO_WAKE_UP_MAX_MINUTES)
                    + PilotRest.extraSleepMinutes(person.getAwakeCreditMinutes());
            final int wakeUpAt = Math.max(now, person.getCreditTime() + sleepMinutes * Time.ONE_MINUTE);
            person.setHeartbeatTime(wakeUpAt);
            log.info("p/s #{} - asleep without a wake up, it is scheduled in {} min", person.getId(), (wakeUpAt - now) / Time.ONE_MINUTE);
        });
    }

    private void wakeUp(final Persons.Person person, final String reason) {
        if (person.getState() != Persons.State.Resting) {
            throw new IllegalStateException("only a sleeping pilot can wake up, the pilot is " + person.getState());
        }
        person.setHeartbeatTime(0); // woken by the user: the scheduled wake up is not needed any more
        final int now = world.getWorldTime();
        final int sleepMinutes = PilotRest.minutesBetween(person.getCreditTime(), now);
        final int before = person.getAwakeCreditMinutes();
        final int after = PilotRest.capAfterWakeUp(PilotRest.creditAfterSleep(before, sleepMinutes), Math.random());
        person.setAwakeCredit(after, now);
        setState(person, Persons.State.Idle, reason);
        log.info("p/s #{} - woke up after {} min of sleep, awake credit {} -> {} min", person.getId(), sleepMinutes, before, after);
    }

    // ---- travel: a free pilot at an airport or in a city moves to an airport or to a city by itself (PilotTravel): Idle -> Travelling (the arrival is the
    // heartbeat) -> Idle. The ways: the warp drive from anywhere to anywhere, Mach 3 from an airport to an airport, by ground from anywhere to anywhere, by airline
    // from a city to a city (not made yet).

    /**
     * A place where a pilot is or goes: an airport (the type Airport, the id of the airport, the name is its ICAO) or a city.
     */
    public record Place(Persons.LocationType type, int id, String name) {
    }

    /**
     * The place where the pilot is now, empty if it is not at an airport or in a city.
     */
    public Optional<Place> currentPlace(final Persons.Person person) {
        return switch (person.getLocationType()) {
            case Airport -> world.airports().byId(person.getLocationId()).map(a -> new Place(Persons.LocationType.Airport, a.getId(), a.getIcao()));
            case City -> world.cities().byId(person.getLocationId()).map(c -> new Place(Persons.LocationType.City, c.getId(), c.getName()));
            default -> Optional.empty();
        };
    }

    /**
     * The airports where the pilot may book an aircraft: the airport where the pilot is, or, for a pilot in a city, the airports linked to the city (the pilot
     * will travel to the airport of the aircraft before the start). Nothing for a pilot who is on the way or nowhere.
     */
    public List<Airports.Airport> bookingAirports(final Persons.Person person) {
        if (person.getState() == Persons.State.Travelling) {
            return List.of();
        }
        return switch (person.getLocationType()) {
            case Airport -> world.airports().byId(person.getLocationId()).map(List::of).orElse(List.of());
            case City -> world.airport2city().allByCityId(person.getLocationId())
                    .map(link -> world.airports().byId(link.getAirportId()))
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(Airports.Airport::getIcao))
                    .toList();
            default -> List.of();
        };
    }

    /**
     * The pilot goes to the place. The way is paid at once (to the bank), the pilot is on the way for the time of the way and then it is at the place and
     * free; the warp drive is at once and free. While the pilot is on the way its location is the place of the arrival (see arrive). The awake credit goes on
     * while the pilot is on the way.
     *
     * @throws IllegalStateException    the pilot cannot travel this way now, the message says why (the state, the place, the flag, the distance, the money)
     * @throws IllegalArgumentException the pilot is at the place already
     */
    public void travel(final Persons.Person person, final PilotTravel.Method method, final Place destination, final double distanceNm) {
        checkTravelAllowed(person, method, destination, distanceNm);
        final PilotTravel.Quote quote = PilotTravel.quote(method, distanceNm);
        final long price = quote.price();
        if (price > 0) {
            final int bankAccountId = bankAccountIdForTravel(person, price);
            world.bankControl().transfer(person.getBankAccountId(), bankAccountId, price, BankTransactions.Type.Travel);
        }

        final String reason = method.title() + " to " + destination.name();
        if (quote.durationMinutes() == 0) {
            // the pilot leaves the place at once, so the meals there are paid now and the count starts again at the new place
            settleLiving(person, false);
            setLocation(person, destination.type(), destination.id(), reason);
            markIdle(person);
            log.info("p/s #{} - travel {}, {} paid, at once", person.getId(), reason, price);
            return;
        }
        // the awake credit is fixed at the departure: at the arrival it is known whether the trip has used it up or the pilot was overtired already (see arrive)
        startAwakeCredit(person);
        person.setAwakeCredit(PilotRest.creditNow(person.getAwakeCreditMinutes(), person.getCreditTime(), world.getWorldTime()), world.getWorldTime());
        // the state first: leaving Idle clears the heartbeat, then the arrival is set
        setState(person, Persons.State.Travelling, reason);
        // while the person is Travelling the location is the place of the arrival (see arrive)
        setLocation(person, destination.type(), destination.id(), reason);
        person.setHeartbeatTime(world.getWorldTime() + quote.durationMinutes() * Time.ONE_MINUTE);
        log.info("p/s #{} - travel {}, {} paid, arrives in {} min", person.getId(), reason, price, quote.durationMinutes());
    }

    /**
     * Whether the pilot can go to the place this way now: the message is the reason when it cannot, null when it can. For the page which shows the ways.
     */
    public String travelProblem(final Persons.Person person, final PilotTravel.Method method, final Place destination, final double distanceNm) {
        try {
            checkTravelAllowed(person, method, destination, distanceNm);
            final long price = PilotTravel.quote(method, distanceNm).price();
            if (price > 0) {
                bankAccountIdForTravel(person, price);
            }
            return null;
        } catch (final IllegalStateException | IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private void checkTravelAllowed(final Persons.Person person, final PilotTravel.Method method, final Place destination, final double distanceNm) {
        if (person.getState() != Persons.State.Idle) {
            throw new IllegalStateException("Only a free pilot can travel, the pilot is " + person.getState());
        }
        final Place from = currentPlace(person).orElseThrow(() -> new IllegalStateException("The pilot is not at an airport or in a city"));
        if (from.type() == destination.type() && from.id() == destination.id()) {
            throw new IllegalArgumentException("The pilot is " + (from.type() == Persons.LocationType.Airport ? "at " : "in ") + destination.name() + " already");
        }
        switch (method) {
            case WarpDrive -> {
                if (!person.isWarpDrive()) {
                    throw new IllegalStateException("This way is not available");
                }
            }
            case Mach3 -> {
                if (from.type() != Persons.LocationType.Airport || destination.type() != Persons.LocationType.Airport) {
                    throw new IllegalStateException("Mach 3 flights are between airports only");
                }
            }
            case Airline, Ground -> { }
        }
        final String problem = PilotTravel.problem(method, distanceNm);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
    }

    // the account of the bank which gets the money of a travel, the pilot must have an account and enough money on it
    private int bankAccountIdForTravel(final Persons.Person person, final long price) {
        final BankAccounts.BankAccount own = world.bankAccounts().byId(person.getBankAccountId()).orElse(null);
        if (own == null) {
            throw new IllegalStateException("The pilot has no bank account");
        }
        if (!own.canGoNegative() && own.getBalance() < price) {
            throw new IllegalStateException("Not enough money");
        }
        return world.bankAccounts().byOwner(BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK)
                .orElseThrow(() -> new IllegalStateException("The bank has no account yet"))
                .getId();
    }

    /**
     * The pilot has arrived (the heartbeat of a travelling pilot): it is free, so the falling asleep by itself is scheduled. The place of the arrival is its
     * location already (while the state is Travelling the location says where the pilot is going), so only the state changes.
     */
    public void arrive(final Persons.Person person) {
        if (person.getState() != Persons.State.Travelling) {
            throw new IllegalStateException("only a travelling pilot can arrive, the pilot is " + person.getState());
        }
        // The awake time goes down on the way. If the pilot had some at the departure and the trip has used it up, the pilot rested on the way: it arrives with a
        // random awake time of two to ten hours. A pilot who was overtired at the departure is not rested by a trip.
        final int now = world.getWorldTime();
        if (!awakeCreditIsNotStarted(person) && person.getAwakeCreditMinutes() >= 0
                && PilotRest.creditNow(person.getAwakeCreditMinutes(), person.getCreditTime(), now) < 0) {
            final int before = PilotRest.creditNow(person.getAwakeCreditMinutes(), person.getCreditTime(), now);
            final int rested = Tools.random(PilotRest.TRIP_REST_MIN_MINUTES, PilotRest.TRIP_REST_MAX_MINUTES);
            person.setAwakeCredit(rested, now);
            log.info("p/s #{} - the trip has used up the awake time, the pilot rested on the way, awake credit {} -> {} min", person.getId(), before, rested);
        }
        // the falling asleep by itself is scheduled again from the awake time the pilot has now (setState does it)
        setState(person, Persons.State.Idle, "arrived");
    }

    // ---- the initials

    /**
     * The person has typed its initials (the caller has checked them, see PilotController.chooseInitials). The change is logged.
     */
    public void setInitials(final Persons.Person person, final String initials) {
        final String oldInitials = person.getInitials();
        if (oldInitials.equals(initials)) {
            return;
        }
        person.setInitials(initials);
        log.info("p/s #{} - initials '{}' -> '{}'", person.getId(), oldInitials, initials);
    }

    // ---- the avatar

    /**
     * The person has chosen the avatar: the number of the row of the list of the pictures (see Avatars). The change is logged.
     */
    public void setAvatar(final Persons.Person person, final int avatarId, final String file) {
        final int oldAvatarId = person.getAvatarId();
        if (oldAvatarId == avatarId) {
            return;
        }
        person.setAvatarId(avatarId);
        log.info("p/s #{} - avatar {} -> {} ({})", person.getId(), oldAvatarId, avatarId, file);
    }

    // ---- low level, it logs every change

    public void setState(final Persons.Person person, final Persons.State state, final String reason) {
        final Persons.State oldState = person.getState();
        if (oldState == state) {
            return;
        }
        // the pilot stops being free: the meals of the place are paid, and the night if the pilot falls asleep (the location is the old one still)
        if (oldState == Persons.State.Idle) {
            settleLiving(person, state == Persons.State.Resting);
        }
        person.setState(state);
        log.info("p/s #{} - state {} -> {} ({})", person.getId(), oldState, state, reason);
        if (state == Persons.State.Idle) {
            markIdle(person);
        }
        // a free pilot is going to fall asleep by itself; a pilot who is not free (on duty, asleep) has nothing scheduled here (a sleep sets its own wake up)
        if (state == Persons.State.Idle) {
            scheduleAutoSleep(person);
        } else if (oldState == Persons.State.Idle) {
            person.setHeartbeatTime(0);
        }
    }

    // ---- the meals and the night of a free pilot (PilotLiving). The moment when the pilot became free is taken from the event log (PersonBecameIdle) and kept in
    // the map, the log is read once. todo ak1 a field of the Person record (4 bytes, a migration of the storage) would replace the log and the map

    private final Map<Integer, Integer> idleSince = new HashMap<>();
    private boolean idleSinceLoaded;

    private void ensureIdleSinceLoaded() {
        if (idleSinceLoaded) {
            return;
        }
        idleSinceLoaded = true;
        final int type = EventLog.EventType.PersonBecameIdle.code();
        world.eventLog().all()
                .filter(event -> event.getTypeRaw() == type)
                .forEach(event -> idleSince.merge(event.getObject1Id(), event.getTime(), Math::max));
    }

    private void markIdle(final Persons.Person person) {
        ensureIdleSinceLoaded();
        final int now = world.getWorldTime();
        world.eventLog().log(now, EventLog.EventType.PersonBecameIdle, EventLog.id(person), null, null, null);
        idleSince.put(person.getId(), now);
    }

    // the pilot stops being free: pays for the meals of the free time in this place and, if it falls asleep, for the night; nothing fails, the pilot pays what it has
    private void settleLiving(final Persons.Person person, final boolean sleeping) {
        final Place place = currentPlace(person).orElse(null);
        if (place == null || person.getBankAccountId() <= 0) {
            return;
        }
        ensureIdleSinceLoaded();
        final Integer since = idleSince.get(person.getId());
        if (since != null) {
            final int minutes = (world.getWorldTime() - since) / Time.ONE_MINUTE;
            final int dayPrice = livingPrice(place, PilotLiving.MEALS_CITY_BASE, PilotLiving.MEALS_AIRPORT_BASE);
            payLiving(person, PilotLiving.mealsAmount(dayPrice, minutes), BankTransactions.Type.PilotMeals, "meals", place);
        }
        if (sleeping) {
            payLiving(person, livingPrice(place, PilotLiving.SLEEP_CITY_BASE, PilotLiving.SLEEP_AIRPORT_BASE), BankTransactions.Type.PilotLodging, "night", place);
        }
    }

    // the price of the day or of the night in the place
    private int livingPrice(final Place place, final int cityBase, final int airportBase) {
        if (place.type() == Persons.LocationType.City) {
            final float attraction = world.cityFlows().byCityId(place.id()).map(f -> f.getAttractionFactor()).orElse(1.0f);
            return PilotLiving.cityPrice(cityBase, attraction, place.id());
        }
        final boolean hub = AirportHubs.isHub(place.name());
        return PilotLiving.airportPrice(airportBase, hub, flightsPerWeek(place.id()), place.id());
    }

    // the flights which took off from the airport or landed there in the days of the flight stats (today and seven days before)
    private int flightsPerWeek(final int airportId) {
        return world.airport2airportDailyFlightStats().allByFromAirportId(airportId).mapToInt(s -> s.getTotalCount()).sum()
                + world.airport2airportDailyFlightStats().allByToAirportId(airportId).mapToInt(s -> s.getTotalCount()).sum();
    }

    private void payLiving(final Persons.Person person, final int amount, final BankTransactions.Type type, final String what, final Place place) {
        if (amount <= 0) {
            return;
        }
        final Optional<BankAccounts.BankAccount> bank = world.bankAccounts().byOwner(BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK);
        if (bank.isEmpty()) {
            log.warn("p/s #{} - {} {} is not paid, the bank has no account yet", person.getId(), what, amount);
            return;
        }
        final long paid = world.bankControl().transferUpTo(person.getBankAccountId(), bank.get().getId(), amount, type);
        log.info("p/s #{} - {} {}{} in {}", person.getId(), what, paid, paid < amount ? " of " + amount : "", place.name());
    }

    public void setLocation(final Persons.Person person, final Persons.LocationType type, final int id, final String reason) {
        final Persons.LocationType oldType = person.getLocationType();
        final int oldId = person.getLocationId();
        if (oldType == type && oldId == id) {
            return;
        }
        person.setLocation(type, id);
        log.info("p/s #{} - location {} -> {} ({})", person.getId(), describeLocation(oldType, oldId), describeLocation(type, id), reason);
    }

    private Optional<Persons.Person> findPerson(final FlightMissions.Mission mission) {
        if (mission.getUserId() <= 0) {
            return Optional.empty();
        }
        final Optional<Persons.Person> person = world.persons().byUserId(mission.getUserId());
        if (person.isEmpty()) {
            log.warn("f/m #{} - user #{} has no person, the state and the location of the person are not changed", mission.getId(), mission.getUserId());
        }
        return person;
    }

    private String describeLocation(final Persons.LocationType type, final int id) {
        if (type == null) {
            return "?";
        }
        return switch (type) {
            case None -> "None";
            case City -> "City " + world.cities().byId(id).map(c -> c.getName()).orElse("#" + id);
            case Airport -> "Airport " + world.airports().getIcao(id).orElse("#" + id);
            case Hotel -> "Hotel #" + id;
        };
    }
}
