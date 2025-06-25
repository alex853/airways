package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.worldbuilder.tools.ImportSelectedAirports;

import java.io.IOException;

public class World25_009_create_some_airports {
    public static void main(String[] args) throws IOException {
        ImportSelectedAirports.main(new String[] {
                "EDDF",
                "EDDK",
                "EDDL",
                "EFHK",
                "EHAM",
                "LCLK",
                "LCPH",
                "LPPT",
                "LSGG",
                "LSZH"
        });
    }
}
