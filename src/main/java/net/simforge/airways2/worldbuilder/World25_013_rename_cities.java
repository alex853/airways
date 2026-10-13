package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.worldbuilder.tools.RenameAirports;
import net.simforge.airways2.worldbuilder.tools.RenameCities;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

// Renames cities which have been imported with transliterated names to the English ones, and airports with odd names.
// It should be run BEFORE World25_012_selected_cities, otherwise the import creates duplicates by the new names.
public class World25_013_rename_cities {
    public static void main(String[] args) throws IOException {
        RenameCities.renameCities(Arrays.asList(
                new String[]{"GR", "Kerkira", "Corfu"},
                new String[]{"GR", "Iraklion", "Heraklion"},
                new String[]{"GR", "Rodos", "Rhodes"}
        ));

        // singletonList is used because Arrays.asList(new String[]{...}) with the only array would be List<String>
        RenameAirports.renameAirports(Collections.singletonList(
                new String[]{"LIRN", "Capodichino"}
        ));
    }
}
