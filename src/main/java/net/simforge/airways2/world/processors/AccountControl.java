package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.Accounts;
import net.simforge.airways2.world.datamodel.Transactions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The only place where the money moves: the transfer takes it from one account, gives it to another and writes the transaction in one step, so the
 * balances and the transactions never differ. When all the accounts which have no money of their own can go negative (the bank, the passengers, the
 * operators), the sum of all the balances is always zero.
 */
public class AccountControl {
    private static final Logger log = LoggerFactory.getLogger(AccountControl.class);

    private final World world;

    public AccountControl(final World world) {
        this.world = world;
    }

    /**
     * Moves the amount from an account to another.
     *
     * @param amount a whole number of money units, more than zero (the percents are rounded before the transfer)
     * @throws IllegalArgumentException if an account does not exist, the accounts are the same, the amount is not more than zero or the account which
     *                                  pays has not enough money and is not allowed to go negative; nothing is changed then
     */
    public Transactions.Transaction transfer(final int fromAccountId, final int toAccountId, final long amount, final Transactions.Type type) {
        checkArgument(amount > 0, "the amount must be more than zero: %s", amount);
        checkArgument(fromAccountId != toAccountId, "the accounts must be different: %s", fromAccountId);
        checkArgument(type != null, "the type is mandatory");
        final Accounts.Account from = world.accounts().byId(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("there is no account #" + fromAccountId));
        final Accounts.Account to = world.accounts().byId(toAccountId)
                .orElseThrow(() -> new IllegalArgumentException("there is no account #" + toAccountId));
        checkArgument(from.canGoNegative() || from.getBalance() >= amount,
                "the account #%s has %s and cannot pay %s", fromAccountId, from.getBalance(), amount);

        final int time = world.getWorldTime();
        from.changeBalance(-amount, time);
        to.changeBalance(amount, time);
        final Transactions.Transaction transaction = world.transactions().create(time, fromAccountId, toAccountId, amount, type);

        log.info("transfer #{} - {} from account #{} to account #{}, {}", transaction.getId(), amount, fromAccountId, toAccountId, type);
        return transaction;
    }

    /**
     * Fixes the balance of an account: writes a balance checkpoint with the balance it has now. Nothing moves.
     */
    public Transactions.Transaction fixBalance(final int accountId) {
        final Accounts.Account account = world.accounts().byId(accountId)
                .orElseThrow(() -> new IllegalArgumentException("there is no account #" + accountId));

        final Transactions.Transaction transaction = world.transactions().create(world.getWorldTime(), accountId, accountId, account.getBalance(), Transactions.Type.BalanceCheckpoint);

        log.info("transfer #{} - balance of account #{} is fixed, {}", transaction.getId(), accountId, account.getBalance());
        return transaction;
    }

    /**
     * The balance of an account by its transactions: the balance of the last checkpoint (zero, if there is none) and what the account has received and paid
     * after it.
     */
    public long calculateBalance(final int accountId) {
        final List<Transactions.Transaction> transactions = world.transactions()
                .filter(world.transactions().byAccountId(accountId))
                .sorted(Comparator.comparingInt(Transactions.Transaction::getId))
                .toList();

        long balance = 0;
        for (final Transactions.Transaction t : transactions) {
            if (t.getType() == Transactions.Type.BalanceCheckpoint) {
                balance = t.getAmount();
            } else if (t.getToAccountId() == accountId) {
                balance += t.getAmount();
            } else {
                balance -= t.getAmount();
            }
        }
        return balance;
    }

    /**
     * Checks that the balance of an account is the one its transactions give.
     */
    public boolean validate(final int accountId) {
        final Accounts.Account account = world.accounts().byId(accountId)
                .orElseThrow(() -> new IllegalArgumentException("there is no account #" + accountId));

        final long calculated = calculateBalance(accountId);
        final boolean valid = calculated == account.getBalance();
        if (!valid) {
            log.error("account #{} - THE BALANCE DOES NOT MATCH THE TRANSACTIONS, balance {}, by transactions {}", accountId, account.getBalance(), calculated);
        }
        return valid;
    }
}
