package org.wavemelon.awesomeareas.integration.village;

import org.wavemelon.awesomeareas.core.storage.AreaManager;

import java.util.Locale;
import java.util.Random;

/**
 * Generates rich, realistic, generic town names using prefix + suffix combinations
 * (e.g., Harriotville, Wayford, Williamsburg, Greenfield, Riverton, Oakdale).
 */
public class VillageNameGenerator {

    private static final String[] PREFIXES = {
            // People & Founder Surnames
            "Harriot", "Way", "Williams", "Jackson", "Smith", "Cooper", "Miller", "Hunter",
            "Mason", "Carter", "Clark", "Taylor", "Adams", "Parker", "Morgan", "Bailey",
            "Reed", "Bell", "Ward", "Watson", "Brooks", "Price", "Bennett", "Wood",
            "Barnes", "Ross", "Henderson", "Jenkins", "Perry", "Powell", "Long", "Hughes",
            "Butler", "Simmons", "Foster", "Bryant", "Alexander", "Russell", "Griffin", "Stewart",
            "Franklin", "Harrison", "Lincoln", "Jefferson", "Madison", "Monroe", "Hamilton",
            "Lawrence", "Marshall", "Spencer", "Stanley", "Harvey", "Clifford", "Leonard",
            "Fletcher", "Montgomery", "Sterling", "Carrington", "Preston", "Barrett", "Winslow",

            // Geographical, Natural & Descriptive
            "Green", "River", "Oak", "Pine", "Cedar", "Willow", "Elm", "Birch", "Maple",
            "Ash", "Lake", "Brook", "Creek", "Spring", "Mill", "Hill", "Glen", "Dale",
            "Cliff", "Stone", "Rock", "High", "Low", "Deep", "Broad", "Fair", "Clear",
            "Silver", "Gold", "Iron", "Clay", "Rose", "Fox", "Wolf", "Bear", "Hawk",
            "Falcon", "Red", "White", "Black", "West", "North", "East", "South", "Sun",
            "Cold", "Wind", "Moss", "Fern", "Thorn", "Cross", "Ridge", "Bay", "Port",
            "Sunny", "Amber", "Meadow", "Valley", "Timber", "Wild", "Copper", "Opal"
    };

    private static final String[] SUFFIXES = {
            "town", "ville", "ston", "ton", "field", "ford", "burg", "burgh",
            "borough", "dale", "haven", "port", "wick", "vale", "shire", "bridge",
            "cross", "grove", "wood", "crest", "fall", "falls", "water", "ridge",
            "view", "side", "point", "bay", "brook", "well", "mill", "hill",
            "moor", "heath", "stead", "gate", "end", "corner", "hollow", "bluff",
            "reach", "shore", "land", "over", "hurst", "mont", "den", "combe"
    };

    public static String generateUniqueVillageName(AreaManager manager, int chunkX, int chunkZ) {
        long seed = ((long) chunkX * 3129871L) ^ ((long) chunkZ * 116129781L) ^ 0x5DEECE66DL;
        Random rng = new Random(seed);

        // Try deterministic prefix + suffix combinations first
        for (int attempts = 0; attempts < 300; attempts++) {
            String p = PREFIXES[rng.nextInt(PREFIXES.length)];
            String s = SUFFIXES[rng.nextInt(SUFFIXES.length)];

            String pLow = p.toLowerCase(Locale.ROOT);
            String sLow = s.toLowerCase(Locale.ROOT);

            // Avoid redundant combinations like "Woodwood" or "Riverbrook"
            if (pLow.equals(sLow)) continue;
            if (pLow.endsWith(sLow)) continue;
            if (pLow.equals("river") && (sLow.equals("brook") || sLow.equals("water"))) continue;
            if (pLow.equals("lake") && (sLow.equals("water") || sLow.equals("bay"))) continue;
            if (pLow.equals("hill") && (sLow.equals("ridge") || sLow.equals("crest"))) continue;

            String candidate = p + s;
            if (manager.getAreaByName(candidate) == null) {
                return candidate;
            }
        }

        // Fallback: pick seeded combination with sequential number if saturated
        int pIndex = Math.abs(chunkX) % PREFIXES.length;
        int sIndex = Math.abs(chunkZ) % SUFFIXES.length;
        String base = PREFIXES[pIndex] + SUFFIXES[sIndex];

        int counter = 2;
        String name = base;
        while (manager.getAreaByName(name) != null) {
            name = base + " " + counter;
            counter++;
        }
        return name;
    }
}
