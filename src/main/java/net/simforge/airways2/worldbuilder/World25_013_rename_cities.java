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
        RenameCountries.renameCountries(Collections.singletonList(
                new String[]{"ZA", "South Africa"}
        ));

        RenameCities.renameCities(Arrays.asList(
                new String[]{"GR", "Kerkira", "Corfu"},
                new String[]{"GR", "Iraklion", "Heraklion"},
                new String[]{"GR", "Rodos", "Rhodes"},
                new String[]{"IT", "Roma", "Rome"},
                new String[]{"IT", "Milano", "Milan"}
        ));

        RenameAirports.renameAirports(Arrays.asList(
                new String[]{"LIRN", "Capodichino"},
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
                new String[]{"VTBD", "Bangkok Don Mueang"},
                new String[]{"VTBS", "Bangkok Suvarnabhumi"}
        ));
    }
}
