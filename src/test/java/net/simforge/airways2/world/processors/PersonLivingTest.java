package net.simforge.airways2.world.processors;

import net.simforge.airways2.world.InMemoryStorageStrategy;
import net.simforge.airways2.world.Time;
import net.simforge.airways2.world.World;
import net.simforge.airways2.world.computations.PilotLiving;
import net.simforge.airways2.world.datamodel.BankAccounts;
import net.simforge.airways2.world.datamodel.BankTransactions;
import net.simforge.airways2.world.datamodel.Cities;
import net.simforge.airways2.world.datamodel.Persons;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A free pilot pays for the meals when it stops being free and for the night when it falls asleep (PilotLiving); what the pilot has is taken, nothing fails.
 */
class PersonLivingTest {
    private static final int HOUR = 3600;

    private World world;
    private Cities.City city;
    private Persons.Person person;
    private BankAccounts.BankAccount pilotAccount;

    @BeforeEach
    void beforeEach() {
        world = World.create(new InMemoryStorageStrategy(), Time.START_TIME_EPOCH_SECONDS);
        world.setWorldTime(world.getWorldTime() + 100 * HOUR);
        final int countryId = world.countries().create("FR", "France").getId();
        // the ids of the cities are 1, 2, 3: the price of the third one has a 3 in the end
        world.cities().create(countryId, "Lyon", 45.7, 4.8, 500_000);
        world.cities().create(countryId, "Nice", 43.7, 7.2, 340_000);
        city = world.cities().create(countryId, "Paris", 48.9, 2.3, 2_000_000);
        person = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 1, Persons.LocationType.City, city.getId(), 0, "AK", 0);
        world.bankAccounts().create(BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK, true, world.getWorldTime());
        pilotAccount = world.bankAccounts().create(BankAccounts.Kind.Person, person.getId(), false, world.getWorldTime());
        person.setBankAccountId(pilotAccount.getId());
        fund(10_000);
    }

    private void fund(final long amount) {
        final BankAccounts.BankAccount operator = world.bankAccounts().create(BankAccounts.Kind.Operator, 3, true, world.getWorldTime());
        world.bankControl().transfer(operator.getId(), pilotAccount.getId(), amount, BankTransactions.Type.PilotFlightPay);
    }

    private void hoursPass(final int hours) {
        world.setWorldTime(world.getWorldTime() + hours * HOUR);
    }

    private List<BankTransactions.BankTransaction> paid(final BankTransactions.Type type) {
        return world.bankTransactions().filter(world.bankTransactions().byAccountId(pilotAccount.getId())).filter(t -> t.getType() == type).toList();
    }

    // the city has no flow in the test, so its attraction is 1
    private int cityDay(final int base) {
        return PilotLiving.cityPrice(base, 1.0f, city.getId());
    }

    @Test
    void theFormulasOfThePrices() {
        // the city: the base x (1 + attraction / 10), plus the last digit of the id
        assertEquals(11 + 4, PilotLiving.cityPrice(10, 1, 94));
        assertEquals(20 + 4, PilotLiving.cityPrice(10, 10, 94));
        assertEquals(30 + 4, PilotLiving.cityPrice(10, 20, 94));
        assertEquals(120 + 3, PilotLiving.cityPrice(40, 20, 3));
        // the airport: the base, a hub, more than 1000 flights in a week, the last digit of the id
        assertEquals(50 + 3, PilotLiving.airportPrice(50, false, 1000, 53));
        assertEquals(50 + 20 + 3, PilotLiving.airportPrice(50, true, 1000, 53));
        assertEquals(120 + 20 + 20 + 3, PilotLiving.airportPrice(120, true, 1001, 53));
        // the meals: a part of the day of 16 hours, not more than the whole price
        assertEquals(7, PilotLiving.mealsAmount(14, 8 * 60));
        assertEquals(14, PilotLiving.mealsAmount(14, 16 * 60));
        assertEquals(14, PilotLiving.mealsAmount(14, 40 * 60));
        assertEquals(0, PilotLiving.mealsAmount(14, 0));
    }

    @Test
    void theNightIsPaidAtTheFallAsleepAndIsNotReturnedAtTheWakeUp() {
        final long before = pilotAccount.getBalance();

        world.personControl().startSleep(person);
        // the pilot was not free for a known time (nothing in the log yet), so only the night is paid
        assertEquals(1, paid(BankTransactions.Type.PilotLodging).size());
        assertEquals(cityDay(PilotLiving.SLEEP_CITY_BASE), paid(BankTransactions.Type.PilotLodging).get(0).getAmount());
        assertEquals(0, paid(BankTransactions.Type.PilotMeals).size());
        assertEquals(before - cityDay(PilotLiving.SLEEP_CITY_BASE), pilotAccount.getBalance());

        hoursPass(2);
        world.personControl().wakeUp(person);

        assertEquals(before - cityDay(PilotLiving.SLEEP_CITY_BASE), pilotAccount.getBalance());
    }

    @Test
    void theMealsAreForTheTimeThePilotWasFree() {
        world.personControl().startSleep(person);
        hoursPass(8);
        world.personControl().wakeUp(person);
        final long before = pilotAccount.getBalance();

        hoursPass(8);
        world.personControl().startSleep(person);

        final int dayPrice = cityDay(PilotLiving.MEALS_CITY_BASE);
        assertEquals(1, paid(BankTransactions.Type.PilotMeals).size());
        assertEquals(PilotLiving.mealsAmount(dayPrice, 8 * 60), paid(BankTransactions.Type.PilotMeals).get(0).getAmount());
        assertEquals(before - PilotLiving.mealsAmount(dayPrice, 8 * 60) - cityDay(PilotLiving.SLEEP_CITY_BASE), pilotAccount.getBalance());
    }

    @Test
    void aPilotWhoHasNotEnoughMoneyPaysWhatItHasAndNothingFails() {
        // the balance is 30, the night is more
        world.bankControl().transfer(pilotAccount.getId(), world.bankAccounts().byOwner(BankAccounts.Kind.System, BankAccounts.SYSTEM_BANK).orElseThrow().getId(),
                pilotAccount.getBalance() - 30, BankTransactions.Type.Travel);

        world.personControl().startSleep(person);

        assertEquals(Persons.State.Resting, person.getState());
        assertEquals(0, pilotAccount.getBalance());
        assertEquals(30, paid(BankTransactions.Type.PilotLodging).get(0).getAmount());

        // nothing at all: no transaction, no failure
        hoursPass(8);
        world.personControl().wakeUp(person);
        hoursPass(8);
        world.personControl().startSleep(person);
        assertEquals(0, pilotAccount.getBalance());
        assertEquals(1, paid(BankTransactions.Type.PilotLodging).size());
        assertEquals(0, paid(BankTransactions.Type.PilotMeals).size());
    }

    @Test
    void goingOnAFlightPaysTheMealsOnly() {
        world.personControl().startSleep(person);
        hoursPass(8);
        world.personControl().wakeUp(person);
        hoursPass(8);
        final long before = pilotAccount.getBalance();

        world.personControl().setState(person, Persons.State.OnDuty, "test");

        assertEquals(1, paid(BankTransactions.Type.PilotMeals).size());
        assertEquals(1, paid(BankTransactions.Type.PilotLodging).size());
        assertTrue(pilotAccount.getBalance() < before);
    }

    @Test
    void aPilotWithoutAnAccountPaysNothingAndNothingFails() {
        final Persons.Person poor = world.persons().create(Persons.Type.Pilot, Persons.State.Idle, 2, Persons.LocationType.City, city.getId(), 0, "BB", 0);

        world.personControl().startSleep(poor);

        assertEquals(Persons.State.Resting, poor.getState());
    }
}
