package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.worldbuilder.tools.ImportCities;
import net.simforge.airways2.worldbuilder.tools.ImportMajorAirportsInVicinityOfCities;

import java.io.IOException;

public class World25_099_f1_tour {
    public static void main(String[] args) throws IOException {
        ImportCities.main(new String[]{"country-code:SG", "min-population:1000000"});

        ImportCities.main(new String[]{"country-code:US", "city-name:San Francisco"});

        // Miami area
        ImportCities.main(new String[]{"country-code:US", "city-name:Miami"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Hialeah"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Fort Lauderdale"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Pembroke Pines"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Hollywood"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Coral Springs"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Miramar"});

        ImportMajorAirportsInVicinityOfCities.main(new String[0]);
    }
}
