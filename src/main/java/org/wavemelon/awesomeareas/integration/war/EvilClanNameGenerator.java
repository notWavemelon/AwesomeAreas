package org.wavemelon.awesomeareas.integration.war;

import java.util.Random;

/**
 * Generates thematic, randomized EVIL clan names for pillager raid warbands.
 * Examples: "The Bloodfang Horde", "The Shadowthorn Warband", "The Dreadskull Raiders".
 */
public class EvilClanNameGenerator {
    private static final String[] EVIL_PREFIXES = {
            "Blood", "Shadow", "Dread", "Iron", "Vile", "Grim", "Black", "Skull",
            "Doom", "Dark", "Gore", "Blight", "Ravage", "Cursed", "Venom", "Hate",
            "Ashen", "Rot", "Storm", "Night", "Chaos", "Bone", "Death", "Grave",
            "Frost", "Ash", "Bane", "Wrath", "Savage", "Fell", "Sinister", "Spite"
    };

    private static final String[] EVIL_MIDDLES = {
            "fang", "blade", "skull", "thorn", "claw", "bane", "spire", "mourn",
            "crush", "render", "scar", "wraith", "howl", "grip", "strike", "maw",
            "stalker", "hound", "reaper", "tusk", "gorge", "fury", "flame", "cleaver"
    };

    private static final String[] CLAN_TYPES = {
            "Clan", "Warband", "Horde", "Legion", "Raiders", "Syndicate", "Host",
            "Cabal", "Marauders", "Brotherhood", "Cult", "Ravagers", "Corsairs",
            "Plunderers", "Battalion", "Cohort"
    };

    private static final Random RNG = new Random();

    public static String generateEvilClanName() {
        String pre = EVIL_PREFIXES[RNG.nextInt(EVIL_PREFIXES.length)];
        String mid = EVIL_MIDDLES[RNG.nextInt(EVIL_MIDDLES.length)];
        String type = CLAN_TYPES[RNG.nextInt(CLAN_TYPES.length)];
        return "The " + pre + mid + " " + type;
    }
}
