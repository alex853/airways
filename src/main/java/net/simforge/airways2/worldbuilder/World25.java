package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.world.DiskStorageStrategy;
import net.simforge.airways2.world.World;

import java.io.IOException;

public class World25 {
    public static final String name = "World25";
    public static final int ShadowJetOperatorId = 2;
    public static final String ShadowJetIata = "SJ";
    public static final String ShadowJetIcao = "SJT";
    // the bank account of the operator, created by the one-time update /admin/bank/create-initial-accounts (the 1st is the bank, the 2nd is the passengers)
    public static final int ShadowJetBankAccountId = 3;
    public static final int BusyBirdsOperatorId = 3;
    public static final String BusyBirdsIata = "BB";
    public static final String BusyBirdsIcao = "BBD";
    public static final int BusyBirdsBankAccountId = 4;

    public static final DiskStorageStrategy diskStorageStrategy = new DiskStorageStrategy(World25.name);

    public static World load() throws IOException {
        return World.load(diskStorageStrategy);
    }
}
