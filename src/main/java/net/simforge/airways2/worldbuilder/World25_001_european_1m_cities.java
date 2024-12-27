package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.worldbuilder.tools.ImportCities;
import net.simforge.airways2.worldbuilder.tools.ImportMajorAirportsInVicinityOfCities;

import java.io.IOException;

public class World25_001_european_1m_cities {
    public static void main(String[] args) throws IOException {
        ImportCities.main(new String[]{"country-code:GB", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:FR", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:DE", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:CZ", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:AT", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:IT", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:ES", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:PT", "min-population:1000000"});
        ImportCities.main(new String[]{"country-code:IE", "min-population:1000000"});

        ImportMajorAirportsInVicinityOfCities.main(new String[0]);
    }
}
