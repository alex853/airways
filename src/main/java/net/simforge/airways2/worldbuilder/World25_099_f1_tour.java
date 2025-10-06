package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.worldbuilder.tools.ImportCities;
import net.simforge.airways2.worldbuilder.tools.ImportMajorAirportsInVicinityOfCities;

import java.io.IOException;

public class World25_099_f1_tour {
    public static void main(String[] args) throws IOException {
        ImportCities.main(new String[]{"country-code:SG", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:US", "city-name:San Francisco"});

        ImportMajorAirportsInVicinityOfCities.main(new String[0]);
    }
}
