package net.simforge.airways2.app.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.app.userstorage.User;
import net.simforge.airways2.app.userstorage.UserStorage;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.BankTransactions;
import net.simforge.airways2.world.datamodel.Persons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Random;

/**
 * The management of the users under the admin token (docs/users.md): a new user with its pilot and its bank account, a new token of a user.
 */
@RestController
@RequestMapping("/admin/user")
@CrossOrigin
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    // the bank gives it to the account of a new user
    static final long START_MONEY = 1000;
    // the pilot of a new user is in this city (fixed for now)
    static final int START_CITY_ID = 1;

    @SuppressWarnings("SpringJavaAutowiredFieldsWarningInspection")
    @Autowired
    private WorldRunnerBean worldBean;

    /**
     * A new user: random initials of the pilot, no avatar, a pilot (an idle person), the bank account of the pilot with the starting money from the system bank
     * account. The pilot is in the city #1. The user has no token and cannot log in until a token is generated.
     */
    @PostMapping("/create")
    public CreatedUserDto create() {
        try {
            return worldBean.modifySync(world -> createUser(world, UserStorage.getInstance(), new Random()));
        } catch (final IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * A new random token of the user. The old token stops to work. The token is saved before the answer, it is the only answer which has it.
     */
    @PostMapping(value = "/{id}/generate-token", produces = "text/plain")
    public String generateToken(@PathVariable final int id) {
        try {
            return generateToken(UserStorage.getInstance(), id);
        } catch (final IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    static String generateToken(final UserStorage users, final int userId) {
        return users.generateToken(userId);
    }

    static CreatedUserDto createUser(final World world, final UserStorage users, final Random random) {
        // everything which can be refused is checked before the user is created
        if (world.cities().byId(START_CITY_ID).isEmpty()) {
            throw new IllegalArgumentException("the city #" + START_CITY_ID + " not found");
        }
        final BankAccounts.BankAccount bank = world.bankAccounts().byId(BankAccounts.SystemBankAccountId)
                .orElseThrow(() -> new IllegalArgumentException("the system bank account #" + BankAccounts.SystemBankAccountId + " not found"));
        if (!bank.canGoNegative() && bank.getBalance() < START_MONEY) {
            throw new IllegalArgumentException("the system bank account has not enough money");
        }

        final User user = users.create(null, 0, 0);

        final String initials = "" + (char) ('A' + random.nextInt(26)) + (char) ('A' + random.nextInt(26));
        final Persons.Person person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, user.getId(),
                Persons.LocationType.City, START_CITY_ID, START_CITY_ID, initials, 0);
        users.setPersonId(user.getId(), person.getId());
        // the flight of the pilot is finished by itself after the deboarding (docs/persons-pilots.md)
        person.setAutoFinish(true);

        final BankAccounts.BankAccount account = world.bankAccounts().create(BankAccounts.Kind.Person, person.getId(), false, world.getWorldTime());
        person.setBankAccountId(account.getId());
        world.bankControl().transfer(BankAccounts.SystemBankAccountId, account.getId(), START_MONEY, BankTransactions.Type.StartMoney);

        log.info("u/s #{} - new user, p/s #{}, initials {}, account #{}, {} money from the bank", user.getId(), person.getId(), initials, account.getId(), START_MONEY);
        return new CreatedUserDto(user.getId(), person.getId(), initials, account.getId(), START_MONEY);
    }

    @Data
    @AllArgsConstructor
    public static class CreatedUserDto {
        private int userId;
        private int personId;
        private String initials;
        private int bankAccountId;
        private long startMoney;
    }
}
