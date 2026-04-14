package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.worldbuilder.tools.ImportCities;
import net.simforge.airways2.worldbuilder.tools.ImportMajorAirportsInVicinityOfCities;

import java.io.IOException;

public class World25_012_selected_cities {
    public static void main(String[] args) throws IOException {
        ImportCities.main(new String[]{"country-code:DE", "city-name:Frankfurt am Main"});
        ImportCities.main(new String[]{"country-code:DE", "city-name:Bremen"});
        ImportCities.main(new String[]{"country-code:DE", "city-name:Hannover"});
        ImportCities.main(new String[]{"country-code:DE", "city-name:Koln"});
        ImportCities.main(new String[]{"country-code:DE", "city-name:Dortmund"});
        ImportCities.main(new String[]{"country-code:DE", "city-name:Essen"});
        ImportCities.main(new String[]{"country-code:DE", "city-name:Dusseldorf"});
        ImportCities.main(new String[]{"country-code:DE", "city-name:Duisburg"});
        ImportCities.main(new String[]{"country-code:DE", "city-name:Bonn"});
        ImportCities.main(new String[]{"country-code:DE", "city-name:Leipzig"});
        ImportCities.main(new String[]{"country-code:DE", "city-name:Dresden"});
        ImportCities.main(new String[]{"country-code:DE", "city-name:Stuttgart"});

        ImportCities.main(new String[]{"country-code:GB", "city-name:Liverpool"});
        ImportCities.main(new String[]{"country-code:GB", "city-name:Edinburgh"});
        ImportCities.main(new String[]{"country-code:GB", "city-name:Manchester"});

        ImportMajorAirportsInVicinityOfCities.main(new String[0]);
    }
}
