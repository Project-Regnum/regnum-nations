package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.RegnumNations;
import java.nio.file.*;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Server configuration in the same properties format as the political map settings. */
final class DiplomacyConfig {
    static long invitationMillis=7*24*60*60*1000L;
    static long proposalCooldownMillis=60_000L;
    static void load() {
        invitationMillis=604800_000L; proposalCooldownMillis=60_000L;
        Path path=FabricLoader.getInstance().getConfigDir().resolve("regnum-nations-diplomacy.properties");
        try {
            Properties p=new Properties();
            if (Files.exists(path)) { try (var input=Files.newInputStream(path)) { p.load(input); } }
            else Files.writeString(path,"# Diplomatic proposal lifetime in seconds (60-2592000). Existing deadlines are retained.\ninvitationLifetimeSeconds=604800\n# Pair-wide proposal cooldown, including rejected/cancelled proposals (0-86400).\nproposalCooldownSeconds=60\n");
            long expiration=1000L*Math.clamp(Long.parseLong(p.getProperty("invitationLifetimeSeconds","604800")),60,2592000);
            long cooldown=1000L*Math.clamp(Long.parseLong(p.getProperty("proposalCooldownSeconds","60")),0,86400);
            invitationMillis=expiration; proposalCooldownMillis=cooldown;
        } catch (Exception e) { RegnumNations.LOGGER.warn("Cannot read diplomacy configuration; using default expiration and cooldown",e); }
    }
}
