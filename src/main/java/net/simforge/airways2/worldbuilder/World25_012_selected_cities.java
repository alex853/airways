package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.worldbuilder.tools.ImportCities;
import net.simforge.airways2.worldbuilder.tools.ImportMajorAirportsInVicinityOfCities;

import java.io.IOException;
import java.util.Arrays;

public class World25_012_selected_cities {
    public static void main(String[] args) throws IOException {
        ImportCities.importCities(Arrays.asList(
                new String[]{"country-code:AE", "city-name:Dubai"},
                new String[]{"country-code:AE", "city-name:Abu Dhabi"},

                new String[]{"country-code:AT", "city-name:Innsbruck"},

                new String[]{"country-code:BE", "city-name:Brussels"},

                new String[]{"country-code:CA", "city-name:Toronto"},
                new String[]{"country-code:CA", "city-name:Montreal"},

                new String[]{"country-code:CH", "city-name:Zurich"},
                new String[]{"country-code:CH", "city-name:Geneve"},

                new String[]{"country-code:CL", "city-name:Santiago de Chile"},
                new String[]{"country-code:CL", "city-name:Puente Alto"},

                new String[]{"country-code:CN", "city-name:Kowloon"},
                new String[]{"country-code:CN", "city-name:Hong Kong Island"},

                new String[]{"country-code:CY", "city-name:Nicosia"},
                new String[]{"country-code:CY", "city-name:Limassol"},
                new String[]{"country-code:CY", "city-name:Larnaca"},
                new String[]{"country-code:CY", "city-name:Paphos"},
                new String[]{"country-code:CY", "city-name:Kyrenia"},
                new String[]{"country-code:CY", "city-name:Ayia Napa"},

                new String[]{"country-code:DE", "city-name:Frankfurt am Main"},
                new String[]{"country-code:DE", "city-name:Bremen"},
                new String[]{"country-code:DE", "city-name:Hannover"},
                new String[]{"country-code:DE", "city-name:Koln"},
                new String[]{"country-code:DE", "city-name:Dortmund"},
                new String[]{"country-code:DE", "city-name:Essen"},
                new String[]{"country-code:DE", "city-name:Dusseldorf"},
                new String[]{"country-code:DE", "city-name:Duisburg"},
                new String[]{"country-code:DE", "city-name:Bonn"},
                new String[]{"country-code:DE", "city-name:Leipzig"},
                new String[]{"country-code:DE", "city-name:Dresden"},
                new String[]{"country-code:DE", "city-name:Stuttgart"},
                new String[]{"country-code:DE", "city-name:Nurnberg"},

                new String[]{"country-code:DK", "city-name:Copenhagen"},

                new String[]{"country-code:EG", "city-name:Cairo"},
                new String[]{"country-code:EG", "city-name:Giza"},
                new String[]{"country-code:EG", "city-name:Shubra El Kheima"},

                new String[]{"country-code:ES", "city-name:Mallorca"},
                new String[]{"country-code:ES", "city-name:Menorca"},
                new String[]{"country-code:ES", "city-name:Ibiza"},

                new String[]{"country-code:FI", "city-name:Helsinki"},

                new String[]{"country-code:FR", "city-name:Nice"},

                new String[]{"country-code:GB", "city-name:Liverpool"},
                new String[]{"country-code:GB", "city-name:Edinburgh"},
                new String[]{"country-code:GB", "city-name:Manchester"},

                new String[]{"country-code:GR", "city-name:Athens"},
                new String[]{"country-code:GR", "city-name:Zakynthos"},

                new String[]{"country-code:HU", "city-name:Budapest"},

                new String[]{"country-code:JP", "city-name:Tokyo"},
                new String[]{"country-code:JP", "city-name:Yokohama"},
                new String[]{"country-code:JP", "city-name:Kawasaki"},

                new String[]{"country-code:IN", "city-name:Mumbai"},

                new String[]{"country-code:IT", "city-name:Venice"},

                new String[]{"country-code:IS", "city-name:Reykjavik"},

                new String[]{"country-code:MC", "city-name:Monte Carlo"},

                new String[]{"country-code:MX", "city-name:Tijuana"},

                new String[]{"country-code:NL", "city-name:Amsterdam"},
                new String[]{"country-code:NL", "city-name:Rotterdam"},
                new String[]{"country-code:NL", "city-name:The Hague"},

                new String[]{"country-code:PL", "city-name:Warsaw"},

                new String[]{"country-code:PT", "city-name:Lisbon"},

                new String[]{"country-code:RO", "city-name:Bucharest"},

                new String[]{"country-code:RU", "city-name:Moscow"},
                new String[]{"country-code:RU", "city-name:Saint Petersburg"},

                new String[]{"country-code:SA", "city-name:Jeddah"},
                new String[]{"country-code:SA", "city-name:Mecca"},

                new String[]{"country-code:SE", "city-name:Stockholm"},
                new String[]{"country-code:SE", "city-name:Gothenburg"},

                new String[]{"country-code:TR", "city-name:Istanbul"},
                new String[]{"country-code:TR", "city-name:Izmir"},
                new String[]{"country-code:TR", "city-name:Bursa"},

                new String[]{"country-code:US", "city-name:Boston"},
                new String[]{"country-code:US", "city-name:New York"},
                new String[]{"country-code:US", "city-name:Austin"},
                new String[]{"country-code:US", "city-name:Los Angeles"},
                new String[]{"country-code:US", "city-name:Chicago"},
                new String[]{"country-code:US", "city-name:Las Vegas"},
                new String[]{"country-code:US", "city-name:Detroit"},
                new String[]{"country-code:US", "city-name:Atlanta"},
                new String[]{"country-code:US", "city-name:Dallas"},
                new String[]{"country-code:US", "city-name:Fort Worth"},
                new String[]{"country-code:US", "city-name:San Diego"},
                new String[]{"country-code:US", "city-name:Orlando"},
                new String[]{"country-code:US", "city-name:Washington"},
                new String[]{"country-code:US", "city-name:Baltimore"},
                new String[]{"country-code:US", "city-name:Seattle"},
                new String[]{"country-code:US", "city-name:Tacoma"},
                new String[]{"country-code:US", "city-name:Denver"},
                new String[]{"country-code:US", "city-name:Aurora"},
                new String[]{"country-code:US", "city-name:Memphis"},
                new String[]{"country-code:US", "city-name:Houston"},
                new String[]{"country-code:US", "city-name:Phoenix"},
                new String[]{"country-code:US", "city-name:Tampa"},
                new String[]{"country-code:US", "city-name:St. Petersburg"},
                new String[]{"country-code:US", "city-name:San Antonio"},
                new String[]{"country-code:US", "city-name:Charlotte"},
                new String[]{"country-code:US", "city-name:Salt Lake City"}
        ));

        ImportMajorAirportsInVicinityOfCities.main(new String[0]);
    }
}
