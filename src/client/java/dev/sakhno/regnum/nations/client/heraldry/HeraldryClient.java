package dev.sakhno.regnum.nations.client.heraldry;

import dev.sakhno.regnum.nations.kingdom.Heraldry;
import dev.sakhno.regnum.nations.networking.HeraldryPayloads;
import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;

/** Public insignia survive dimension changes, and are cleared when leaving the server. */
public final class HeraldryClient {
    private static final Map<UUID,Heraldry> ARMS=new HashMap<>();
    private static final Heraldry VACANT=new Heraldry(1,Heraldry.Shield.HEATER,Heraldry.Tincture.SABLE,Heraldry.Division.PLAIN,
            Heraldry.Tincture.ARGENT,Heraldry.Ordinary.NONE,Heraldry.Tincture.ARGENT,List.of());
    private HeraldryClient() { }
    public static Heraldry arms(UUID kingdom) { return kingdom==null?VACANT:ARMS.getOrDefault(kingdom,VACANT); }
    public static void accept(HeraldryPayloads.Sync sync) {
        if (sync.reset()) ARMS.clear();
        for (var entry:sync.entries()) { if (entry.arms()==null) ARMS.remove(entry.kingdom()); else ARMS.put(entry.kingdom(),entry.arms()); }
    }
    public static void initialize() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(HeraldryTextures.INSTANCE);
        RoyalBannerRenderer.initialize();
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> { ARMS.clear(); HeraldryTextures.INSTANCE.clear(); });
        ClientPlayNetworking.registerGlobalReceiver(HeraldryPayloads.Sync.TYPE,(payload,context) -> accept(payload));
        ClientPlayNetworking.registerGlobalReceiver(HeraldryPayloads.State.TYPE,(payload,context) -> {
            if (context.client().screen instanceof dev.sakhno.regnum.nations.client.ui.HeraldryScreen screen) screen.accept(payload);
        });
    }
}
