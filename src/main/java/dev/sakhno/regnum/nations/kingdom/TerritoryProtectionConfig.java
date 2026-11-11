package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.RegnumNations;
import java.util.Properties;

/** Server-only options in the existing legacy properties file. Missing/invalid values stay secure. */
public final class TerritoryProtectionConfig {
    public static boolean explosions = true;
    public static boolean pistons = true;
    public static boolean fluids = true;
    public static boolean dispensers = true;
    public static boolean fire = true;
    public static boolean entities = true;

    private TerritoryProtectionConfig() { }

    static void load(Properties properties) {
        explosions = enabled(properties, "explosions");
        pistons = enabled(properties, "pistons");
        fluids = enabled(properties, "fluids");
        dispensers = enabled(properties, "dispensers");
        fire = enabled(properties, "fire");
        entities = enabled(properties, "entities");
    }

    private static boolean enabled(Properties properties, String category) {
        String key = "protection." + category;
        String value = properties.getProperty(key, "true").trim();
        if (value.equalsIgnoreCase("false")) return false;
        if (!value.equalsIgnoreCase("true")) RegnumNations.LOGGER.warn("Invalid {}; keeping protection enabled", key);
        return true;
    }
}
