package net.simforge.airways2.tools;

import org.apache.logging.log4j.util.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class CabinLayout {
    private static final int ECONOMY_LENGTH = 10;
    private static final int PREMIUM_ECONOMY_LENGTH = 8;
    private static final int BUSINESS_LENGTH = 7;
    private static final int FIRST_LENGTH = 5;

    private static final int ECONOMY_OFFSET = 0;
    private static final int PREMIUM_ECONOMY_OFFSET = ECONOMY_OFFSET + ECONOMY_LENGTH;
    private static final int BUSINESS_OFFSET = PREMIUM_ECONOMY_OFFSET + PREMIUM_ECONOMY_LENGTH;
    private static final int FIRST_OFFSET = BUSINESS_OFFSET + BUSINESS_LENGTH;

    private static final int ECONOMY_MAX = ((1 << ECONOMY_LENGTH) - 1);
    private static final int PREMIUM_ECONOMY_MAX = ((1 << PREMIUM_ECONOMY_LENGTH) - 1);
    private static final int BUSINESS_MAX = ((1 << BUSINESS_LENGTH) - 1);
    private static final int FIRST_MAX = ((1 << FIRST_LENGTH) - 1);

    private static final int ECONOMY_MASK = ECONOMY_MAX << ECONOMY_OFFSET;
    private static final int PREMIUM_ECONOMY_MASK = PREMIUM_ECONOMY_MAX << PREMIUM_ECONOMY_OFFSET;
    private static final int BUSINESS_MASK = BUSINESS_MAX << BUSINESS_OFFSET;
    private static final int FIRST_MASK = FIRST_MAX << FIRST_OFFSET;

    private final int economy;
    private final int premiumEconomy;
    private final int business;
    private final int first;

    private CabinLayout(final int economy, final int premiumEconomy, final int business, final int first) {
        this.economy = economy;
        this.premiumEconomy = premiumEconomy;
        this.business = business;
        this.first = first;
    }

    public static final CabinLayout NOBODY = new CabinLayout(0, 0, 0, 0);

    public static CabinLayout Y(final int economy) {
        checkArgument(0 <= economy && economy <= ECONOMY_MAX);
        return new CabinLayout(economy, 0, 0, 0);
    }

    public static CabinLayout JY(final int business, final int economy) {
        checkArgument(0 <= business && business <= BUSINESS_MAX);
        checkArgument(0 <= economy && economy <= ECONOMY_MAX);
        return new CabinLayout(economy, 0, business, 0);
    }

    public static CabinLayout FJWY(final int first, final int business, final int premiumEconomy, final int economy) {
        checkArgument(0 <= economy && economy <= ECONOMY_MAX);
        checkArgument(0 <= premiumEconomy && premiumEconomy <= PREMIUM_ECONOMY_MAX);
        checkArgument(0 <= business && business <= BUSINESS_MAX);
        checkArgument(0 <= first && first <= FIRST_MAX);
        return new CabinLayout(economy, premiumEconomy, business, first);
    }

    public int getEconomy() {
        return economy;
    }

    public int getPremiumEconomy() {
        return premiumEconomy;
    }

    public int getBusiness() {
        return business;
    }

    public int getFirst() {
        return first;
    }

    public int getTotal() {
        return economy + premiumEconomy + business + first;
    }

    public int get(final Service cabinService) {
        return switch (cabinService) {
            case Y -> economy;
            case W -> premiumEconomy;
            case J -> business;
            case F -> first;
        };
    }

    public CabinLayout occupySeats(final int seats, final Service service) {
        checkArgument(seats >= 0);
        checkNotNull(service);
        return new CabinLayout(
                economy - (service == Service.Y ? seats : 0),
                premiumEconomy - (service == Service.W ? seats : 0),
                business - (service == Service.J ? seats : 0),
                first - (service == Service.F ? seats : 0));
    }

    public int toSigned32bit() {
        return (economy << ECONOMY_OFFSET)
                + (premiumEconomy << PREMIUM_ECONOMY_OFFSET)
                + (business << BUSINESS_OFFSET)
                + (first << FIRST_OFFSET);
    }

    public static CabinLayout fromSigned32bit(final int value) {
        final int economy = (value & ECONOMY_MASK) >> ECONOMY_OFFSET;
        final int premiumEconomy = (value & PREMIUM_ECONOMY_MASK) >> PREMIUM_ECONOMY_OFFSET;
        final int business = (value & BUSINESS_MASK) >> BUSINESS_OFFSET;
        final int first = (value & FIRST_MASK) >> FIRST_OFFSET;
        return new CabinLayout(economy, premiumEconomy, business, first);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CabinLayout layout = (CabinLayout) o;
        return economy == layout.economy && premiumEconomy == layout.premiumEconomy && business == layout.business && first == layout.first;
    }

    @Override
    public int hashCode() {
        return Objects.hash(economy, premiumEconomy, business, first);
    }

    @Override
    public String toString() {
        final List<String> strs = new ArrayList<>(4);
        if (first != 0)
            strs.add("F" + first);
        if (business != 0)
            strs.add("J" + business);
        if (premiumEconomy != 0)
            strs.add("W" + premiumEconomy);
        if (economy != 0)
            strs.add("Y" + economy);
        return Strings.join(strs, '/');
    }

    public enum Service {
        Y, // economy
        W, // premium economy
        J, // business
        F  // first
    }
}
