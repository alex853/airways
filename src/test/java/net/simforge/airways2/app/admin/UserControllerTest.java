package net.simforge.airways2.app.admin;

import net.simforge.airways2.app.userstorage.User;
import net.simforge.airways2.app.userstorage.UserStorage;
import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.WorldStorageStrategy;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.BankTransactions;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A new user (the pilot, the bank account with the starting money) and a new token of a user.
 */
class UserControllerTest {
    private World world;
    private UserStorage users;
    private Cities.City city;

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
        world.aircraftOperators().create("SJ", "SJT", "shadowJet");
        world.aircraftOperators().create("BB", "BBD", "busyBirds");
        city = world.cities().create(world.countries().create("TT", "Testland").getId(), "Alpha City", 0, 0, 1_000_000);
        assertEquals(UserController.START_CITY_ID, city.getId());
        // the accounts of the production: the first one is the bank
        world.bankAccounts().create(BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK, true, world.getWorldTime());
        world.bankAccounts().create(BankAccounts.Kind.System, BankAccounts.SYSTEM_PASSENGERS, true, world.getWorldTime());
        users = new UserStorage(folder(dir.resolve("data")));
    }

    @Test
    void aNewUserHasAPilotAnAccountAndTheStartingMoney() {
        final UserController.CreatedUserDto created = UserController.createUser(world, users, new Random(1));

        final User user = users.findById(created.getUserId()).orElseThrow();
        assertNull(user.getToken()); // the user cannot log in
        assertEquals(created.getPersonId(), user.getPersonId());

        final Persons.Person person = world.persons().byId(created.getPersonId()).orElseThrow();
        assertEquals(Persons.Type.Pilot, person.getType());
        assertEquals(Persons.State.Idle, person.getState());
        assertEquals(user.getId(), person.getUserId());
        assertEquals(Persons.LocationType.City, person.getLocationType());
        assertEquals(1, person.getLocationId());
        assertEquals(1, person.getOriginCityId());
        assertEquals(0, person.getAvatarId());
        assertEquals(2, person.getInitials().length());
        assertTrue(person.getInitials().chars().allMatch(c -> c >= 'A' && c <= 'Z'));
        assertEquals(created.getInitials(), person.getInitials());

        final BankAccounts.BankAccount account = world.bankAccounts().byId(created.getBankAccountId()).orElseThrow();
        assertEquals(BankAccounts.Kind.Person, account.getKind());
        assertEquals(person.getId(), account.getOwnerId());
        assertEquals(account.getId(), person.getBankAccountId());
        assertEquals(1_000, account.getBalance());
        assertEquals(-1_000, world.bankAccounts().byId(BankAccounts.SystemBankAccountId).orElseThrow().getBalance());
        assertEquals(1, world.bankTransactions().all().filter(t -> t.getType() == BankTransactions.Type.StartMoney).count());
    }

    @Test
    void withoutTheStartCityNothingIsCreated() {
        final World noCity = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        noCity.bankAccounts().create(BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK, true, noCity.getWorldTime());

        assertThrows(IllegalArgumentException.class, () -> UserController.createUser(noCity, users, new Random(1)));

        assertTrue(users.all().isEmpty());
        assertEquals(0, noCity.persons().all().count());
    }

    @Test
    void withoutTheBankAccountNothingIsCreated() {
        final World empty = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);

        assertThrows(IllegalArgumentException.class, () -> UserController.createUser(empty, users, new Random(1)));

        assertTrue(users.all().isEmpty());
    }

    @Test
    void theGeneratedTokenOpensTheUser() {
        final UserController.CreatedUserDto created = UserController.createUser(world, users, new Random(1));

        final String token = UserController.generateToken(users, created.getUserId());

        assertEquals(created.getUserId(), users.findByToken(token).orElseThrow().getId());
        assertThrows(IllegalArgumentException.class, () -> UserController.generateToken(users, 99));
    }
}
