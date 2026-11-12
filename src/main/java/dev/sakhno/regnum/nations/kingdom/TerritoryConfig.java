package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.RegnumNations;
import java.nio.file.*;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Server-only bounds. Lowering a limit never deletes existing land or diplomatic demands. */
public final class TerritoryConfig {
    private static int settlementLimit=256, politicalLimit=256;
    private TerritoryConfig() { }
    public static int settlementLimit() { return settlementLimit; }
    public static int politicalLimit() { return politicalLimit; }
    public static void load() {
        Path path=FabricLoader.getInstance().getConfigDir().resolve("project-reigns-territory.properties");
        Properties properties=new Properties();
        try {
            if (Files.exists(path)) try (var input=Files.newInputStream(path)) { properties.load(input); }
            settlementLimit=read(properties,"settlementClaimLimit",256,1,100000);
            politicalLimit=read(properties,"politicalClaimsPerKingdom",256,1,4096);
            if (!Files.exists(path)) {
                properties.setProperty("settlementClaimLimit",Integer.toString(settlementLimit));
                properties.setProperty("politicalClaimsPerKingdom",Integer.toString(politicalLimit));
                try (var output=Files.newOutputStream(path)) { properties.store(output,"Regnum Nations territory limits (including the city anchor); restart to apply"); }
            }
        } catch (java.io.IOException | IllegalArgumentException error) {
            RegnumNations.LOGGER.error("Cannot read territory configuration; using safe defaults",error);
            settlementLimit=256; politicalLimit=256;
        }
    }
    private static int read(Properties values,String key,int fallback,int min,int max) {
        int value=Integer.parseInt(values.getProperty(key,Integer.toString(fallback)).trim());
        if (value<min||value>max) throw new IllegalArgumentException("Out-of-range territory setting: "+key);
        return value;
    }
}
