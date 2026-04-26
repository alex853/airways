package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.worldbuilder.tools.ImportCities;
import net.simforge.airways2.worldbuilder.tools.ImportMajorAirportsInVicinityOfCities;

import java.io.IOException;

public class World25_012_selected_cities {
    public static void main(String[] args) throws IOException {
        ImportCities.main(new String[]{"country-code:AE", "city-name:Dubai"});
        ImportCities.main(new String[]{"country-code:AE", "city-name:Abu Dhabi"});

        ImportCities.main(new String[]{"country-code:CA", "city-name:Toronto"});
        ImportCities.main(new String[]{"country-code:CA", "city-name:Montreal"});

        ImportCities.main(new String[]{"country-code:CH", "city-name:Zurich"});
        ImportCities.main(new String[]{"country-code:CH", "city-name:Geneve"});

        ImportCities.main(new String[]{"country-code:CL", "city-name:Santiago de Chile"});
        ImportCities.main(new String[]{"country-code:CL", "city-name:Puente Alto"});

        ImportCities.main(new String[]{"country-code:CN", "city-name:Kowloon"});
        ImportCities.main(new String[]{"country-code:CN", "city-name:Hong Kong Island"});

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
        ImportCities.main(new String[]{"country-code:DE", "city-name:Nurnberg"});

        ImportCities.main(new String[]{"country-code:DK", "city-name:Copenhagen"});

        ImportCities.main(new String[]{"country-code:FR", "city-name:Nice"});

        ImportCities.main(new String[]{"country-code:GB", "city-name:Liverpool"});
        ImportCities.main(new String[]{"country-code:GB", "city-name:Edinburgh"});
        ImportCities.main(new String[]{"country-code:GB", "city-name:Manchester"});

        ImportCities.main(new String[]{"country-code:GR", "city-name:Athens"});

        ImportCities.main(new String[]{"country-code:JP", "city-name:Tokyo"});
        ImportCities.main(new String[]{"country-code:JP", "city-name:Yokohama"});
        ImportCities.main(new String[]{"country-code:JP", "city-name:Kawasaki"});

        ImportCities.main(new String[]{"country-code:IT", "city-name:Venice"});

        ImportCities.main(new String[]{"country-code:MX", "city-name:Tijuana"});

        ImportCities.main(new String[]{"country-code:PT", "city-name:Lisbon"});

        ImportCities.main(new String[]{"country-code:RU", "city-name:Moscow"});

        ImportCities.main(new String[]{"country-code:SE", "city-name:Stockholm"});

        ImportCities.main(new String[]{"country-code:US", "city-name:Boston"});
        ImportCities.main(new String[]{"country-code:US", "city-name:New York"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Austin"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Los Angeles"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Chicago"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Las Vegas"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Detroit"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Atlanta"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Dallas"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Fort Worth"});
        ImportCities.main(new String[]{"country-code:US", "city-name:San Diego"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Orlando"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Washington"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Baltimore"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Seattle"});
        ImportCities.main(new String[]{"country-code:US", "city-name:Tacoma"});

        ImportMajorAirportsInVicinityOfCities.main(new String[0]);
    }
}
