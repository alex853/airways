package net.simforge.airways2.app.user;

import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.tools.Id;
import net.simforge.airways2.app.userstorage.User;
import net.simforge.airways2.app.userstorage.UserStorage;
import net.simforge.airways2.tools.CabinLayout;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.WorldAccess;
import net.simforge.airways2.world.WorldStorageStrategy;
import net.simforge.airways2.world.datamodel.AircraftOperators;
import net.simforge.airways2.world.datamodel.AircraftTypes;
import net.simforge.airways2.world.datamodel.Aircrafts;
import net.simforge.airways2.world.datamodel.AirportFacilities;
import net.simforge.airways2.world.datamodel.Airports;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.FlightMissions;
import net.simforge.airways2.world.datamodel.Journeys;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The booking of a BusyBirds mission with a start time (the page Book busyBirds) from the plan to the flights of the roster: the legs are at the chosen time with 30 minutes
 * between them, and a mission which does not fit into the roster is refused without leaving anything behind.
 */
class BusyBirdsBookingStartTimeTest {
    private World world;
    private BusyBirdsController controller;
    private Object oldUserStorage;
    private int userId;
    private Journeys.Journey journey;
    private Aircrafts.Aircraft aircraft;
    private Airports.Airport base;
    private Airports.Airport destination;

    // the storage of the users in a folder of the test, the one of the application is not touched
    private static WorldStorageStrategy folder(final Path dir) {
        return new WorldStorageStrategy() {
            @Override
            public void load(final WorldIOOperation loadingOps) throws IOException {
                loadingOps.perform(dir);
            }

            @Override
            public void save(final WorldIOOperation savingOps) throws IOException {
                savingOps.perform(dir);
            }
        };
    }

    @BeforeEach
    void beforeEach(@TempDir final Path dir) {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);

        world.aircraftOperators().create("AA", "AAA", "first");
        world.aircraftOperators().create("SJ", "SJT", "second");
        final AircraftOperators.AircraftOperator busyBirds = world.aircraftOperators().create("BB", "BBD", "busyBirds");

        final int countryId = world.countries().create("GB", "United Kingdom").getId();
        final Cities.City fromCity = world.cities().create(countryId, "From", 51.28, -0.76, 100000);
        final Cities.City toCity = world.cities().create(countryId, "To", 52.5, 13.4, 100000);
        base = world.airports().create(51.28, -0.76, "FRM", "EGLF", "Farnborough");
        destination = world.airports().create(52.36, 13.5, "TOO", "EDDB", "Berlin");
        world.airport2city().create(base.getId(), fromCity.getId());
        world.airport2city().create(destination.getId(), toCity.getId());
        world.airportFacilities().createIfAbsent(base, busyBirds, AirportFacilities.Type.BaseAirport);

        final AircraftTypes.AircraftType type = world.aircraftTypes().create("C25C", "CNJ");
        aircraft = world.aircrafts().create(type, "BB-CJA", base);
        aircraft.setAircraftOperatorId(busyBirds.getId());

        journey = world.journeys().create(Journeys.Status.LookingForTickets, fromCity.getId(), toCity.getId(), 4, CabinLayout.Service.J);
        journey.setLocationCityId(fromCity.getId());

        oldUserStorage = ReflectionTestUtils.getField(UserStorage.class, "instance");
        ReflectionTestUtils.setField(UserStorage.class, "instance", new UserStorage(folder(dir.resolve("data"))));
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1, Persons.LocationType.Airport, base.getId(), fromCity.getId(), "AK", 0);
        final User user = UserStorage.getInstance().create("TestToken123456", person.getId(), 0);
        userId = user.getId();

        final WorldRunnerBean worldBean = mock(WorldRunnerBean.class);
        when(worldBean.read(any())).thenAnswer(invocation -> ((WorldAccess.Action<?>) invocation.getArgument(0)).invoke(world));
        when(worldBean.modifySync(any())).thenAnswer(invocation -> ((WorldAccess.Action<?>) invocation.getArgument(0)).invoke(world));
        controller = new BusyBirdsController();
        ReflectionTestUtils.setField(controller, "worldBean", worldBean);
    }

    @AfterEach
    void afterEach() {
        ReflectionTestUtils.setField(UserStorage.class, "instance", oldUserStorage);
    }

    private long startAfterHours(final int hours) {
        return (world.getWorldTime() + hours * 3600L) * 1000L;
    }

    private BusyBirdsController.BookMissionResponse book(final long startMillis) {
        final BusyBirdsController.BuildPlansResponse plans = controller.buildPlans(userId, Id.encode(journey.getId()), Id.encode(aircraft.getId()), startMillis, null, true);
        return controller.bookMission(userId, Id.encode(journey.getId()), Id.encode(aircraft.getId()), startMillis, null, true, plans.getPlans().get(0).getId());
    }

    private List<FlightMissions.Mission> flightsOfTheUser() {
        return world.flightMissions().allByUserId(userId).sorted(FlightMissions.sortByDepartureTimeFromPastToFuture).toList();
    }

    @Test
    void theMissionIsBookedIntoTheRosterAtTheChosenTimeWithThirtyMinutesBetweenTheLegs() {
        final long start = startAfterHours(30);

        final BusyBirdsController.BookMissionResponse response = book(start);

        assertEquals("success", response.getStatus(), String.valueOf(response.getMessages()));
        final List<FlightMissions.Mission> flights = flightsOfTheUser();
        assertEquals(2, flights.size(), "the revenue leg and the ferry back");
        assertEquals(Time.alignTo5mins((int) (start / 1000)), flights.get(0).getPlannedDepartureWorldTime());
        // the gap of the roster between the flights is 30 minutes (the departure is aligned up to 5 minutes), so the legs of one mission are not in conflict with each other
        final int gap = flights.get(1).getPlannedDepartureWorldTime() - flights.get(0).getPlannedArrivalWorldTime();
        assertTrue(gap >= 30 * 60 && gap < 35 * 60, "the gap between the legs in seconds: " + gap);
        assertEquals(Journeys.Status.WaitingForCheckIn, journey.getStatus());
        assertTrue(journey.getTransportFlight1Id() > 0);
    }

    @Test
    void aMissionWhichConflictsWithAFlightOfTheRosterIsRefusedAndNothingIsLeftBehind() {
        final int departure = world.getWorldTime() + 30 * 3600;
        final FlightMissions.Mission other = world.flightMissions().createDispatchedMission(aircraft, base, destination, departure, departure + 3 * 3600);
        other.setUserId(userId);

        // the mission starts in the middle of that flight
        final BusyBirdsController.BookMissionResponse response = book((departure + 3600) * 1000L);

        assertEquals("failure", response.getStatus());
        assertTrue(String.join(" ", response.getMessages()).contains("conflicts with your flight"), String.valueOf(response.getMessages()));
        assertEquals(1, flightsOfTheUser().size(), "only the flight which was there");
        assertEquals(Journeys.Status.LookingForTickets, journey.getStatus());
        assertEquals(0, journey.getTransportFlight1Id());
    }

    @Test
    void aMissionWhichStartsTooLateIsRefused() {
        final BusyBirdsController.BookMissionResponse response = book(startAfterHours(8 * 24));

        assertEquals("failure", response.getStatus());
        assertTrue(String.join(" ", response.getMessages()).contains("too late"), String.valueOf(response.getMessages()));
        assertEquals(0, flightsOfTheUser().size());
    }

    @Test
    void theStateAndThePlaceOfThePilotDoNotMatter() {
        // the pilot is on duty, nowhere: the mission is booked all the same, it is the pilot who has to come to the aircraft
        final Persons.Person person = world.persons().byId(UserStorage.getInstance().findById(userId).orElseThrow().getPersonId()).orElseThrow();
        person.setState(Persons.State.OnDuty);
        person.setLocation(Persons.LocationType.None, 0);

        final BusyBirdsController.BookMissionResponse response = book(startAfterHours(30));

        assertEquals("success", response.getStatus(), String.valueOf(response.getMessages()));
    }

    @Test
    void aUserWithoutAPilotBooksNothing() {
        final BusyBirdsController.BuildPlansResponse plans = controller.buildPlans(userId, Id.encode(journey.getId()), Id.encode(aircraft.getId()), startAfterHours(30), null, true);
        UserStorage.getInstance().setPersonId(userId, 0);

        final BusyBirdsController.BookMissionResponse response = controller.bookMission(userId, Id.encode(journey.getId()), Id.encode(aircraft.getId()), startAfterHours(30), null, true,
                plans.getPlans().get(0).getId());

        assertEquals("failure", response.getStatus());
        assertTrue(String.join(" ", response.getMessages()).contains("no pilot"), String.valueOf(response.getMessages()));
        assertEquals(0, flightsOfTheUser().size());
    }
}
