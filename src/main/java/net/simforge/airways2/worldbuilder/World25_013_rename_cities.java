package net.simforge.airways2.worldbuilder;

import net.simforge.airways2.worldbuilder.tools.RenameCities;

import java.io.IOException;
import java.util.Arrays;

// Renames cities which have been imported with transliterated names to the English ones.
// It should be run BEFORE World25_012_selected_cities, otherwise the import creates duplicates by the new names.
public class World25_013_rename_cities {
    public static void main(String[] args) throws IOException {
        RenameCities.renameCities(Arrays.asList(
                new String[]{"GR", "Kerkira", "Corfu"},
                new String[]{"GR", "Iraklion", "Heraklion"},
                new String[]{"GR", "Rodos", "Rhodes"}
        ));
    }
}
