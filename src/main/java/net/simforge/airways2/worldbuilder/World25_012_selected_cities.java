package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.worldbuilder.tools.ImportCities;
import net.simforge.airways2.worldbuilder.tools.ImportMajorAirportsInVicinityOfCities;

import java.io.IOException;

public class World25_012_selected_cities {
    public static void main(String[] args) throws IOException {
        ImportCities.main(new String[]{"country-code:GB", "city-name:Liverpool"});
        ImportCities.main(new String[]{"country-code:GB", "city-name:Edinburgh"});
        ImportCities.main(new String[]{"country-code:GB", "city-name:Manchester"});

        ImportMajorAirportsInVicinityOfCities.main(new String[0]);
    }
}
