package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.worldbuilder.tools.RenameAirports;
import net.simforge.airways2.worldbuilder.tools.RenameCities;
import net.simforge.airways2.worldbuilder.tools.RenameCountries;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

// Renames cities which have been imported with transliterated names to the English ones, and airports with odd names.
// It should be run BEFORE World25_012_selected_cities, otherwise the import creates duplicates by the new names.
public class World25_013_rename_cities {
    public static void main(String[] args) throws IOException {
        RenameCountries.renameCountries(Arrays.asList(
                new String[]{"ZA", "South Africa"},
                new String[]{"SA", "Saudi Arabia"}
        ));

        RenameCities.renameCities(Arrays.asList(
                new String[]{"GR", "Kerkira", "Corfu"},
                new String[]{"GR", "Iraklion", "Heraklion"},
                new String[]{"GR", "Rodos", "Rhodes"},
                new String[]{"IT", "Roma", "Rome"},
                new String[]{"IT", "Milano", "Milan"},
                new String[]{"DE", "Munchen", "Munich"},
                new String[]{"DE", "Koln", "Cologne"},
                new String[]{"DE", "Nurnberg", "Nuremberg"},
                new String[]{"DE", "Hannover", "Hanover"},
                new String[]{"DE", "Frankfurt am Main", "Frankfurt"}
        ));

        RenameAirports.renameAirports(Arrays.asList(
                new String[]{"LIRN", "Capodichino"},
                new String[]{"LJLJ", "Ljubljana Joze Pucnik"},
                new String[]{"EETN", "Tallinn Lennart Meri"},
                new String[]{"LFKJ", "Ajaccio Napoleon Bonaparte"},
                new String[]{"LFKB", "Bastia Poretta"},
                new String[]{"LGKR", "Corfu Ioannis Kapodistrias"},
                new String[]{"LGIR", "Heraklion Nikos Kazantzakis"},
                new String[]{"ZSPD", "Shanghai Pudong"},
                new String[]{"ZSSS", "Shanghai Hongqiao"},
                new String[]{"ENZV", "Stavanger Sola"},
                new String[]{"SAEZ", "Buenos Aires Ezeiza"},
                new String[]{"RCTP", "Taipei Taoyuan"},
                new String[]{"HAAB", "Addis Ababa Bole"},
                new String[]{"VIDP", "Delhi Indira Gandhi"},
                new String[]{"YMEN", "Melbourne Essendon"},
                new String[]{"FAOR", "Johannesburg O.R. Tambo"},
                new String[]{"RJBB", "Osaka Kansai"},
                new String[]{"RJOO", "Osaka Itami"},
                new String[]{"RPLL", "Manila Ninoy Aquino"},
                new String[]{"RPLC", "Manila Clark"},
                new String[]{"LFLL", "Lyon Saint-Exupery"},
                new String[]{"LFLY", "Lyon Bron"},
                new String[]{"LMML", "Malta Luqa"},
                new String[]{"VTBD", "Bangkok Don Mueang"},
                new String[]{"VTBS", "Bangkok Suvarnabhumi"},
                new String[]{"OERK", "Riyadh King Khalid"},
                new String[]{"UMMS", "Minsk National"},
                new String[]{"LLBG", "Tel Aviv Ben Gurion"},
                new String[]{"LGTS", "Thessaloniki Makedonia"},
                new String[]{"EKVG", "Faroe Islands Vagar"},
                new String[]{"OJAI", "Amman Queen Alia"},
                new String[]{"TJSJ", "San Juan Luis Munoz Marin"},
                new String[]{"EGAA", "Belfast International"},
                new String[]{"EGAC", "Belfast City"},
                new String[]{"KOKC", "Oklahoma City Will Rogers"},
                new String[]{"HKJK", "Nairobi Jomo Kenyatta"},
                new String[]{"LYBE", "Belgrade Nikola Tesla"},
                new String[]{"SBGL", "Rio de Janeiro Galeao"},
                new String[]{"LFML", "Marseille Provence"},
                new String[]{"ENTC", "Tromso Langnes"},
                new String[]{"UMKK", "Kaliningrad Khrabrovo"},
                new String[]{"RPVM", "Cebu Mactan"},
                new String[]{"GMMX", "Marrakech Menara"},
                new String[]{"ZGGG", "Guangzhou Baiyun"},
                new String[]{"YPAD", "Adelaide"},
                new String[]{"LXGB", "Gibraltar"},
                new String[]{"LFBO", "Toulouse Blagnac"},
                new String[]{"WADD", "Denpasar Ngurah Rai"},
                new String[]{"SKBO", "Bogota El Dorado"},
                new String[]{"ZSAM", "Xiamen Gaoqi"},
                new String[]{"VAAH", "Ahmedabad Sardar Vallabhbhai Patel"},
                new String[]{"EGNT", "Newcastle Intl"},
                new String[]{"DTTA", "Tunis Carthage"},
                new String[]{"GMMN", "Casablanca Mohammed V"},
                new String[]{"OOMS", "Muscat Intl"}
        ));
    }
}
