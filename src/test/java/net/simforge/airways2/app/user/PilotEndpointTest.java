package net.simforge.airways2.app.user;

import net.simforge.airways2.app.user.PilotController.PilotDto;
import net.simforge.airways2.tools.Avatars;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.BankTransactions;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The pilot of the user as the page of the pilot shows it: who, the state, where and how much money.
 */
class PilotEndpointTest {
    private World world;
    private Airports.Airport oakland;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        oakland = world.airports().create(37.72, -122.22, "OAK", "KOAK", "Oakland");
    }

    @Test
    void pilotHasTheBalanceOfItsBankAccount() {
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1, Persons.LocationType.Airport, oakland.getId(), 0, "AK", 0);
        final BankAccounts.BankAccount pilotAccount = world.bankAccounts().create(BankAccounts.Kind.Person, person.getId(), false, world.getWorldTime());
        final BankAccounts.BankAccount operatorAccount = world.bankAccounts().create(BankAccounts.Kind.Operator, 3, true, world.getWorldTime());
        person.setBankAccountId(pilotAccount.getId());
        world.bankControl().transfer(operatorAccount.getId(), pilotAccount.getId(), 12_345, BankTransactions.Type.PilotFlightPay);

        final PilotDto pilot = PilotController.buildPilot(world, person);

        assertEquals(12_345L, pilot.getBankBalance());
        assertEquals("AK", pilot.getInitials());
        assertEquals("Idle", pilot.getState());
        assertEquals("KOAK", pilot.getAirportIcao());
    }

    @Test
    void pilotWithoutAnAccountHasNoBalance() {
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.OnDuty, 1, Persons.LocationType.None, 0, 0, "AK", 0);

        final PilotDto pilot = PilotController.buildPilot(world, person);

        assertNull(pilot.getBankBalance());
        assertEquals("None", pilot.getLocationType());
        assertNull(pilot.getAirportIcao());
    }

    private Persons.Person freshPilot() {
        return world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1, Persons.LocationType.None, 0, 0, "AK", 0);
    }

    @Test
    void awakePilotShowsTheCreditNowAndItFallsWithTheTime() {
        final Persons.Person person = freshPilot();
        world.personControl().startAwakeCredit(person);
        world.setWorldTime(world.getWorldTime() + 3 * 3600);

        final PilotController.AwakeDto awake = PilotController.buildPilot(world, person).getAwake();

        assertEquals(13 * 60, awake.getCreditMinutes());
        assertFalse(awake.isSleeping());
        assertNull(awake.getCreditAfterWakeMinutes());
    }

    @Test
    void sleepingPilotShowsTheCreditAtFallingAsleepAndTheOneAfterWaking() {
        final Persons.Person person = freshPilot();
        world.personControl().startAwakeCredit(person);
        world.setWorldTime(world.getWorldTime() + 14 * 3600);
        world.personControl().startSleep(person);
        world.setWorldTime(world.getWorldTime() + 3 * 3600);

        final PilotController.AwakeDto awake = PilotController.buildPilot(world, person).getAwake();

        assertEquals(2 * 60, awake.getCreditMinutes());
        assertTrue(awake.isSleeping());
        assertEquals(3 * 60, awake.getSleepMinutes());
        assertEquals(8 * 60, awake.getCreditAfterWakeMinutes());
    }

    // ---- the duty of the last 24 hours

    private Airports.Airport sanFrancisco;
    private net.simforge.airways2.world.datamodel.Aircrafts.Aircraft jet;

    private net.simforge.airways2.world.datamodel.PilotLogbook.Entry logbookEntry(final Persons.Person person, final int blocksOffHoursAgo, final int blocksOnHoursAgo) {
        if (jet == null) {
            sanFrancisco = world.airports().create(37.62, -122.38, "SFO", "KSFO", "San Francisco");
            jet = world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", oakland);
        }
        final Airports.Airport other = sanFrancisco;
        final net.simforge.airways2.world.datamodel.Aircrafts.Aircraft aircraft = jet;
        final int now = world.getWorldTime();
        return world.pilotLogbook().create(person.getId(), aircraft.getId(), oakland.getId(), other.getId(),
                now - blocksOffHoursAgo * 3600, now - blocksOnHoursAgo * 3600);
    }

    @Test
    void dutyOfAnEntryWithoutTheKeptDutyIsTheBlocksWithThePreflightAndThePostflight() {
        world.setWorldTime(world.getWorldTime() + 100 * 3600);
        final Persons.Person person = freshPilot();
        // two hours from blocks off to blocks on: 30 min of the preflight before and 20 min of the postflight after make 2 h 50 min
        logbookEntry(person, 6, 4);

        final PilotController.DutyDto duty = PilotController.buildDuty(world, person);

        assertEquals(170, duty.getLast24HoursMinutes());
        assertEquals(14 * 60, duty.getLimitMinutes());
        assertFalse(duty.isOnDuty());
    }

    @Test
    void dutyOfAnEntryWithTheKeptDutyIsFromTheStartToTheFinish() {
        world.setWorldTime(world.getWorldTime() + 100 * 3600);
        final Persons.Person person = freshPilot();
        final int now = world.getWorldTime();
        logbookEntry(person, 6, 4).setDuty(now - 7 * 3600, now - 3 * 3600);

        assertEquals(4 * 60, PilotController.buildDuty(world, person).getLast24HoursMinutes());
    }

    @Test
    void flightsOlderThanADayAreNotInTheDuty() {
        world.setWorldTime(world.getWorldTime() + 100 * 3600);
        final Persons.Person person = freshPilot();
        logbookEntry(person, 50, 48);

        assertEquals(0, PilotController.buildDuty(world, person).getLast24HoursMinutes());
    }

    @Test
    void flightInProgressIsCountedFromItsStartUpToNow() {
        world.setWorldTime(world.getWorldTime() + 100 * 3600);
        final Persons.Person person = freshPilot();
        person.setState(Persons.State.OnDuty);
        person.setDutyStartTime(world.getWorldTime() - 90 * 60);
        // and one finished flight before it: 2 h 50 min
        logbookEntry(person, 12, 10);

        final PilotController.DutyDto duty = PilotController.buildDuty(world, person);

        assertEquals(170 + 90, duty.getLast24HoursMinutes());
        assertTrue(duty.isOnDuty());
    }

    // ---- the flight time of the last 30 and 365 days

    @Test
    void flightTimeIsTheBlocksOfTheLogbookInTheWindows() {
        world.setWorldTime(world.getWorldTime() + 400 * 24 * 3600);
        final Persons.Person person = freshPilot();
        logbookEntry(person, 10 * 24 + 5, 10 * 24);      // 5 h, ten days ago
        logbookEntry(person, 100 * 24 + 6, 100 * 24);    // 6 h, 100 days ago
        logbookEntry(person, 380 * 24 + 7, 380 * 24);    // 7 h, more than a year ago

        final PilotController.FlightTimeDto flightTime = PilotController.buildFlightTime(world, person);

        assertEquals(5 * 60, flightTime.getLast30DaysMinutes());
        assertEquals(11 * 60, flightTime.getLast365DaysMinutes());
        assertEquals(80 * 60, flightTime.getLimit30DaysMinutes());
        assertEquals(800 * 60, flightTime.getLimit365DaysMinutes());
    }

    @Test
    void flightInProgressIsInTheFlightTimeFromItsBlocksOffUpToNow() {
        world.setWorldTime(world.getWorldTime() + 100 * 3600);
        final Persons.Person person = freshPilot();
        person.setState(Persons.State.OnDuty);
        final Airports.Airport other = world.airports().create(37.62, -122.38, "SFO", "KSFO", "San Francisco");
        final net.simforge.airways2.world.datamodel.Aircrafts.Aircraft aircraft =
                world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", oakland);
        final int now = world.getWorldTime();
        final net.simforge.airways2.world.datamodel.FlightMissions.Mission mission =
                world.flightMissions().createDispatchedMission(aircraft, oakland, other, now - 3 * 3600, now + 3600);
        mission.setUserId(person.getUserId());
        mission.setStatus(net.simforge.airways2.world.datamodel.FlightMissions.Status.Flying);
        mission.setActualDepartureWorldTime(now - 90 * 60);

        assertEquals(90, PilotController.buildFlightTime(world, person).getLast30DaysMinutes());
    }

    @Test
    void flightNotYetOffTheBlocksIsNotInTheFlightTime() {
        world.setWorldTime(world.getWorldTime() + 100 * 3600);
        final Persons.Person person = freshPilot();
        person.setState(Persons.State.OnDuty);
        final Airports.Airport other = world.airports().create(37.62, -122.38, "SFO", "KSFO", "San Francisco");
        final net.simforge.airways2.world.datamodel.Aircrafts.Aircraft aircraft =
                world.aircrafts().create(world.aircraftTypes().create("C25C", "CNJ"), "BB-CJA", oakland);
        final int now = world.getWorldTime();
        final net.simforge.airways2.world.datamodel.FlightMissions.Mission mission =
                world.flightMissions().createDispatchedMission(aircraft, oakland, other, now + 1800, now + 3 * 3600);
        mission.setUserId(person.getUserId());
        mission.setStatus(net.simforge.airways2.world.datamodel.FlightMissions.Status.Preflight);

        assertEquals(0, PilotController.buildFlightTime(world, person).getLast30DaysMinutes());
    }

    @Test
    void theAvatarOfThePersonIsInThePilotAndInTheHeaderOfTheOtherPages() {
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1, Persons.LocationType.None, 0, 0, "AK", 106);

        // row 106 of avatars.csv
        assertEquals("user_pilot.png", PilotController.buildPilot(world, person).getAvatarImage());
        assertEquals("user_pilot.png", PilotController.buildLogbook(world, person.getId()).getAvatarImage());
        assertEquals("user_pilot.png", PilotController.buildQualifications(world, person.getId(), world.getWorldTime()).getAvatarImage());
    }

    @Test
    void personWithoutAnAvatarShowsOneOfTwoPilotsByTheParityOfItsId() {
        final Persons.Person first = freshPilot();
        final Persons.Person second = freshPilot();

        assertEquals(0, first.getAvatarId());
        assertEquals(Avatars.DEFAULT_FOR_ODD_PERSON_ID, PilotController.buildPilot(world, first).getAvatarImage());
        assertEquals(Avatars.DEFAULT_FOR_EVEN_PERSON_ID, PilotController.buildPilot(world, second).getAvatarImage());
        // the stored avatar is not changed by showing it
        assertEquals(0, first.getAvatarId());
        assertEquals(0, second.getAvatarId());
        // the other pages have the same picture
        assertEquals(Avatars.DEFAULT_FOR_ODD_PERSON_ID, PilotController.buildLogbook(world, first.getId()).getAvatarImage());
        assertEquals(Avatars.DEFAULT_FOR_EVEN_PERSON_ID, PilotController.buildQualifications(world, second.getId(), world.getWorldTime()).getAvatarImage());
    }

    @Test
    void chosenAvatarIsStoredAndShown() {
        final Persons.Person person = freshPilot();

        PilotController.chooseAvatar(world, person, "user_zorro.png");

        assertEquals(Avatars.size(), person.getAvatarId());
        assertEquals("user_zorro.png", PilotController.buildPilot(world, person).getAvatarImage());
    }

    @Test
    void aForbiddenOrUnknownPictureCannotBeChosen() {
        final Persons.Person person = freshPilot();

        for (final String image : new String[]{"user_add.png", "apple.png", "../user.png", "", "nothing"}) {
            final org.springframework.web.server.ResponseStatusException e = org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.web.server.ResponseStatusException.class, () -> PilotController.chooseAvatar(world, person, image));
            assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, e.getStatus());
        }
        assertEquals(0, person.getAvatarId());
    }

    @Test
    void pilotWithoutACreditStartedShowsTheInitialOne() {
        assertEquals(16 * 60, PilotController.buildPilot(world, freshPilot()).getAwake().getCreditMinutes());
    }
}
