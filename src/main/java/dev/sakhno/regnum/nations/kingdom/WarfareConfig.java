package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.RegnumNations;
import java.nio.file.*;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Durations are snapshotted on declaration; existing siege deadlines never change on reload. */
final class WarfareConfig {
    static int preparationSeconds=300, durationSeconds=1800, captureSeconds=120, defenseSeconds=30;
    static final int CAPTURE_RADIUS=6, AREA_RADIUS=24, MAX_ACTIVE=64;
    static void load() {
        preparationSeconds=300; durationSeconds=1800; captureSeconds=120; defenseSeconds=30;
        Path path=FabricLoader.getInstance().getConfigDir().resolve("regnum-nations-warfare.properties");
        try {
            Properties p=new Properties();
            if (Files.exists(path)) { try (var in=Files.newInputStream(path)) { p.load(in); } }
            else Files.writeString(path,"# Seconds. Existing siege deadlines and capture goals are retained.\npreparationSeconds=300\nsiegeDurationSeconds=1800\ncaptureSeconds=120\ndefenseHoldSeconds=30\n");
            int prep=read(p,"preparationSeconds",300,0,86400),duration=read(p,"siegeDurationSeconds",1800,30,86400);
            int capture=read(p,"captureSeconds",120,1,86400),defense=read(p,"defenseHoldSeconds",30,1,86400);
            preparationSeconds=prep; durationSeconds=duration; captureSeconds=capture; defenseSeconds=defense;
        } catch (Exception failure) { RegnumNations.LOGGER.warn("Cannot read warfare configuration; using defaults",failure); }
    }
    private static int read(Properties p,String key,int fallback,int min,int max) {
        return Math.clamp(Integer.parseInt(p.getProperty(key,Integer.toString(fallback))),min,max);
    }
}
