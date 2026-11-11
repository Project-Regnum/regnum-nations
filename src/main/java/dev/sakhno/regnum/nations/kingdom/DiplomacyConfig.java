package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.RegnumNations;
import java.nio.file.*;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Server configuration in the same properties format as the political map settings. */
final class DiplomacyConfig {
    static long invitationMillis=7*24*60*60*1000L;
    static void load() {
        Path path=FabricLoader.getInstance().getConfigDir().resolve("regnum-nations-diplomacy.properties");
        try {
            Properties p=new Properties();
            if (Files.exists(path)) { try (var input=Files.newInputStream(path)) { p.load(input); } }
            else Files.writeString(path,"# Alliance invitation lifetime in seconds (60-2592000). Existing deadlines are retained.\ninvitationLifetimeSeconds=604800\n");
            invitationMillis=1000L*Math.clamp(Long.parseLong(p.getProperty("invitationLifetimeSeconds","604800")),60,2592000);
        } catch (Exception e) { RegnumNations.LOGGER.warn("Cannot read diplomacy configuration; using seven days",e); }
    }
}
