package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.World;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.BankTransactions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The only place where the money moves: the transfer takes it from one account, gives it to another and writes the transaction in one step, so the
 * balances and the transactions never differ. When all the accounts which have no money of their own can go negative (the bank, the passengers, the
 * operators), the sum of all the balances is always zero.
 */
public class BankControl {
    private static final Logger log = LoggerFactory.getLogger(BankControl.class);

    private final World world;

    public BankControl(final World world) {
        this.world = world;
    }

    /**
     * Moves the amount from an account to another.
     *
     * @param amount a whole number of money units, more than zero (the percents are rounded before the transfer)
     * @throws IllegalArgumentException if an account does not exist, the accounts are the same, the amount is not more than zero or the account which
     *                                  pays has not enough money and is not allowed to go negative; nothing is changed then
     */
    public BankTransactions.BankTransaction transfer(final int fromAccountId, final int toAccountId, final long amount, final BankTransactions.Type type) {
        checkArgument(amount > 0, "the amount must be more than zero: %s", amount);
        checkArgument(fromAccountId != toAccountId, "the accounts must be different: %s", fromAccountId);
        checkArgument(type != null, "the type is mandatory");
        final BankAccounts.BankAccount from = world.bankAccounts().byId(fromAccountId)
                .orElseThrow(() -> new IllegalArgumentException("there is no account #" + fromAccountId));
        final BankAccounts.BankAccount to = world.bankAccounts().byId(toAccountId)
                .orElseThrow(() -> new IllegalArgumentException("there is no account #" + toAccountId));
        checkArgument(from.canGoNegative() || from.getBalance() >= amount,
                "the account #%s has %s and cannot pay %s", fromAccountId, from.getBalance(), amount);

        final int time = world.getWorldTime();
        from.changeBalance(-amount, time);
        to.changeBalance(amount, time);
        final BankTransactions.BankTransaction transaction = world.bankTransactions().create(time, fromAccountId, toAccountId, amount, type);

        log.info("transfer #{} - {} from account #{} to account #{}, {}", transaction.getId(), amount, fromAccountId, toAccountId, type);
        return transaction;
    }

    /**
     * The id of the bank account of an operator, zero if the operator has none.
     */
    public int operatorAccountId(final int operatorId) {
        return world.bankAccounts().byOwner(BankAccounts.Kind.Operator, operatorId).map(BankAccounts.BankAccount::getId).orElse(0);
    }

    /**
     * A payment of the game: the transfer if both accounts exist. When an account is missing (the world before the first accounts, an operator without an
     * account, a person without one) nothing moves, it is written to the log as a warning, and the game goes on. A zero amount is not a transfer.
     *
     * @param reason what the payment is for, for the log
     */
    public Optional<BankTransactions.BankTransaction> transferIfAccountsExist(final int fromAccountId, final int toAccountId, final long amount,
                                                                              final BankTransactions.Type type, final String reason) {
        if (amount <= 0) {
            return Optional.empty();
        }
        if (fromAccountId <= 0 || toAccountId <= 0
                || world.bankAccounts().byId(fromAccountId).isEmpty() || world.bankAccounts().byId(toAccountId).isEmpty()) {
            log.warn("{} - no transfer of {}, {}: an account is missing, from #{}, to #{}", reason, amount, type, fromAccountId, toAccountId);
            return Optional.empty();
        }
        return Optional.of(transfer(fromAccountId, toAccountId, amount, type));
    }

    /**
     * Fixes the balance of an account: writes a balance checkpoint, the value of the balance it has now. Nothing moves. The checkpoint is the point which
     * validate starts from.
     */
    public BankTransactions.BankTransaction fixBalance(final int accountId) {
        final BankAccounts.BankAccount account = world.bankAccounts().byId(accountId)
                .orElseThrow(() -> new IllegalArgumentException("there is no account #" + accountId));

        final BankTransactions.BankTransaction transaction = world.bankTransactions().create(world.getWorldTime(), accountId, 0, account.getBalance(), BankTransactions.Type.BalanceCheckpoint);

        log.info("transfer #{} - balance of account #{} is fixed, {}", transaction.getId(), accountId, account.getBalance());
        return transaction;
    }

    // The balance of an account by its transactions: the value of the last checkpoint (zero, if there is none) and what the account has received and paid after it.
    private long calculateBalance(final int accountId) {
        final List<BankTransactions.BankTransaction> transactions = world.bankTransactions()
                .filter(world.bankTransactions().byAccountId(accountId))
                .sorted(Comparator.comparingInt(BankTransactions.BankTransaction::getId))
                .toList();

        long balance = 0;
        for (final BankTransactions.BankTransaction t : transactions) {
            if (t.getType() == BankTransactions.Type.BalanceCheckpoint) {
                // the value of the balance, not a change of it
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
     * Checks the balance of an account: it has to be the one its transactions give, that is the value of the last checkpoint (zero, if there is none) plus
     * what the account has received and paid after it. A mismatch is written to the log as an error.
     */
    public boolean validate(final int accountId) {
        final BankAccounts.BankAccount account = world.bankAccounts().byId(accountId)
                .orElseThrow(() -> new IllegalArgumentException("there is no account #" + accountId));

        final long calculated = calculateBalance(accountId);
        final boolean valid = calculated == account.getBalance();
        if (!valid) {
            log.error("account #{} - THE BALANCE DOES NOT MATCH THE TRANSACTIONS, balance {}, by transactions {}", accountId, account.getBalance(), calculated);
        }
        return valid;
    }
}
