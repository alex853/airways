package net.simforge.airways2.app.admin;

import net.simforge.airways2.app.beans.WorldRunnerBean;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.Persons;
import net.simforge.airways2.worldbuilder.World25;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The bank under the admin token: the accounts as a plain text table.
 */
@RestController
@RequestMapping("/admin/bank")
@CrossOrigin
public class BankController {
    private static final String NEW_LINE = "\n";

    @SuppressWarnings("SpringJavaAutowiredFieldsWarningInspection")
    @Autowired
    private WorldRunnerBean worldBean;

    @GetMapping(value = "/accounts", produces = "text/plain")
    public String getAccounts() {
        return worldBean.read(BankController::formatAccounts);
    }

    /**
     * The one-time update: creates the first accounts. The order gives the ids when the world has no accounts yet: 1 the bank, 2 the passengers, 3 ShadowJet,
     * 4 BusyBirds, 5 the first person. Everything has a zero balance and has no name. What exists already (by the owner) is not created again, so the call is
     * safe to repeat. The person gets the reference to its account (bankAccountId).
     */
    @GetMapping(value = "/create-initial-accounts", produces = "text/plain")
    public String createInitialAccounts(@RequestParam(name = "dryRun", defaultValue = "true") final boolean dryRun) {
        return worldBean.modifySync(world -> createInitialAccounts(world, dryRun));
    }

    static String createInitialAccounts(final World world, final boolean dryRun) {
        final List<String> lines = new ArrayList<>();
        if (dryRun) {
            lines.add("DRY RUN - nothing is changed");
            lines.add("");
        }

        createInitialAccount(world, dryRun, lines, "System Bank", BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK, true, 0);
        createInitialAccount(world, dryRun, lines, "System Passengers", BankAccounts.Kind.System, BankAccounts.SYSTEM_PASSENGERS, true, 0);
        createInitialAccount(world, dryRun, lines, "Operator ShadowJet", BankAccounts.Kind.Operator, World25.ShadowJetOperatorId, true, BankAccounts.ShadowJetBankAccountId);
        createInitialAccount(world, dryRun, lines, "Operator BusyBirds", BankAccounts.Kind.Operator, World25.BusyBirdsOperatorId, true, BankAccounts.BusyBirdsBankAccountId);
        createInitialAccount(world, dryRun, lines, "Person #1", BankAccounts.Kind.Person, 1, false, 0);

        return String.join(NEW_LINE, lines) + NEW_LINE;
    }

    private static void createInitialAccount(final World world, final boolean dryRun, final List<String> lines,
                                             final String label, final BankAccounts.Kind kind, final int ownerId, final boolean canGoNegative,
                                             final int expectedAccountId) {
        final String name = String.format("%-20s", label);

        // the owner has to exist
        final boolean ownerExists = switch (kind) {
            case System -> true;
            case Operator -> world.aircraftOperators().byId(ownerId).isPresent();
            case Person -> world.persons().byId(ownerId).isPresent();
        };
        if (!ownerExists) {
            lines.add(name + " owner not found, skipped");
            return;
        }

        final java.util.Optional<BankAccounts.BankAccount> existing = world.bankAccounts().byOwner(kind, ownerId);
        final BankAccounts.BankAccount account;
        if (existing.isPresent()) {
            account = existing.get();
            lines.add(name + " exists already, account #" + account.getId());
        } else if (dryRun) {
            lines.add(name + " should be created");
            return;
        } else {
            account = world.bankAccounts().create(kind, ownerId, canGoNegative, world.getWorldTime());
            lines.add(name + " created, account #" + account.getId());
        }

        // the id which the rest of the code knows (World25 constants), zero if there is none
        if (expectedAccountId > 0 && account.getId() != expectedAccountId) {
            lines.add(name + " WARNING: the account is #" + account.getId() + ", but the constant of the code says #" + expectedAccountId);
        }

        // a person refers to its account
        if (kind == BankAccounts.Kind.Person) {
            final Persons.Person person = world.persons().byId(ownerId).orElseThrow();
            if (person.getBankAccountId() != account.getId()) {
                if (dryRun) {
                    lines.add(name + " the person should get the reference to the account #" + account.getId());
                } else {
                    person.setBankAccountId(account.getId());
                    lines.add(name + " the person refers to the account #" + account.getId());
                }
            }
        }
    }

    /**
     * The accounts, one per line: the id, the balance, the kind and the owner (by the kind: the code of a system account, the id of an operator or of a person).
     */
    static String formatAccounts(final World world) {
        final List<BankAccounts.BankAccount> accounts = world.bankAccounts().all()
                .sorted(Comparator.comparingInt(BankAccounts.BankAccount::getId))
                .toList();

        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-8s %18s  %-10s %s%n", "ID", "BALANCE", "KIND", "OWNER"));
        for (final BankAccounts.BankAccount account : accounts) {
            sb.append(String.format("%-8d %18d  %-10s %d%n", account.getId(), account.getBalance(), account.getKind(), account.getOwnerId()));
        }
        return sb.toString();
    }
}
