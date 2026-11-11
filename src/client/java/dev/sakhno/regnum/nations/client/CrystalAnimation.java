package dev.sakhno.regnum.nations.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sakhno.regnum.nations.RegnumNations;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Exported source pivots and a shared world clock; no per-obelisk mutable animation state. */
public final class CrystalAnimation implements SimpleSynchronousResourceReloadListener {
    private static final ResourceLocation RESOURCE = RegnumNations.id("obelisk_animation.json");
    private static final Settings STILL = new Settings(0, 0, 0, 0, 1, 0, 1);
    private static volatile Profiles profiles = new Profiles(STILL, STILL);

    public record Motion(float degrees, double bob, float brightness) { }

    /** Pivot and amplitude are already converted from model units to blocks. */
    public record Settings(double x, double y, double z, int rotationTicks, int bobTicks,
                           double amplitude, float brightnessMin) {
        public Motion sample(long gameTime, float partialTick) {
            if (rotationTicks == 0) return new Motion(0, 0, 1);
            // Reduce the integer tick first: float precision never degrades in old worlds.
            double rotation = (Math.floorMod(gameTime, rotationTicks) + partialTick) / rotationTicks;
            double phase = (Math.floorMod(gameTime, bobTicks) + partialTick) * (Math.PI * 2 / bobTicks);
            double wave = Math.sin(phase);
            return new Motion((float)(rotation * 360), wave * amplitude,
                    (float)(brightnessMin + (1 - brightnessMin) * (.5 + .5 * wave)));
        }
    }

    private record Profiles(Settings city, Settings kingdom) { }

    public static Settings settings(boolean kingdom) {
        var current = profiles;
        return kingdom ? current.kingdom : current.city;
    }

    @Override public ResourceLocation getFabricId() {
        return RegnumNations.id("obelisk_animation");
    }

    @Override public void onResourceManagerReload(ResourceManager manager) {
        try (var input = manager.getResourceOrThrow(RESOURCE).open();
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            var city = read(root.getAsJsonObject("city"));
            var kingdom = read(root.getAsJsonObject("kingdom"));
            profiles = new Profiles(city, kingdom);
        } catch (Exception error) {
            // A broken resource pack must not rotate around an obsolete pivot.
            profiles = new Profiles(STILL, STILL);
            RegnumNations.LOGGER.warn("Could not load obelisk animation; crystals will render at rest", error);
        }
    }

    private static Settings read(JsonObject object) {
        var pivot = object.getAsJsonArray("pivot");
        if (pivot.size() != 3) throw new IllegalArgumentException("Crystal pivot must have three coordinates");
        double x = pivot.get(0).getAsDouble() / 16;
        double y = pivot.get(1).getAsDouble() / 16;
        double z = pivot.get(2).getAsDouble() / 16;
        int rotation = object.get("rotation_ticks").getAsInt();
        int bob = object.get("bob_ticks").getAsInt();
        double amplitude = object.get("bob_amplitude").getAsDouble() / 16;
        float brightness = object.get("brightness_min").getAsFloat();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || rotation < 20 || bob < 20 || !Double.isFinite(amplitude) || amplitude < 0 || amplitude > .125
                || !Float.isFinite(brightness) || brightness < .8F || brightness > 1)
            throw new IllegalArgumentException("Invalid crystal animation profile");
        return new Settings(x, y, z, rotation, bob, amplitude, brightness);
    }
}
