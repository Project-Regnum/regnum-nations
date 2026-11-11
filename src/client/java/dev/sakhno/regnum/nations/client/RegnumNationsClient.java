package dev.sakhno.regnum.nations.client;

import dev.sakhno.regnum.nations.client.ui.PoliticalMapScreen;
import dev.sakhno.regnum.nations.client.ui.KingdomManagementScreen;
import dev.sakhno.regnum.nations.client.ui.KingdomFoundingScreen;
import dev.sakhno.regnum.nations.networking.KingdomManagementPayloads;
import dev.sakhno.regnum.nations.networking.PoliticalMapPayloads.Snapshot;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class RegnumNationsClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        dev.sakhno.regnum.nations.client.terrain.AtlasTerrain.initialize();
        ClientPlayNetworking.registerGlobalReceiver(dev.sakhno.regnum.nations.networking.AtlasPayloads.Session.TYPE,
                (payload, context) -> dev.sakhno.regnum.nations.client.terrain.diorama.MapSession.setWorld(payload.world()));
        ClientPlayNetworking.registerGlobalReceiver(dev.sakhno.regnum.nations.networking.AtlasPayloads.Markers.TYPE,
                (payload, context) -> {
                    if (context.client().screen instanceof PoliticalMapScreen map) map.acceptMarkers(payload);
                });
        var key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.project-reigns.map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.project-reigns"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while(key.consumeClick()) if(client.player != null && client.screen == null) client.setScreen(new PoliticalMapScreen());
        });
        ClientPlayNetworking.registerGlobalReceiver(dev.sakhno.regnum.nations.networking.ObeliskRelocationPayloads.Reply.TYPE,(payload,context) -> {
            if (context.client().screen instanceof dev.sakhno.regnum.nations.client.ui.ObeliskRelocationScreen screen) screen.accept(payload);
        });
        ClientPlayNetworking.registerGlobalReceiver(KingdomManagementPayloads.State.TYPE, (payload, context) -> {
            var client = context.client();
            if (client.player == null || client.level == null) return;
            if (payload.intent() == KingdomManagementPayloads.INVALIDATE) {
                if (client.screen instanceof KingdomManagementScreen menu) menu.invalidate();
                else if (client.screen instanceof PoliticalMapScreen map) map.invalidate();
            } else if (payload.intent() == KingdomManagementPayloads.OPEN_FOUNDING || payload.intent() == KingdomManagementPayloads.OPEN_CITY_FOUNDING) {
                client.setScreen(new KingdomFoundingScreen(payload.obelisk(),payload.intent()==KingdomManagementPayloads.OPEN_CITY_FOUNDING));
            } else if (payload.intent() == KingdomManagementPayloads.OPEN_MENU || payload.intent() == KingdomManagementPayloads.OPEN_CITY_MENU) {
                client.setScreen(new KingdomManagementScreen(payload));
            } else if (client.screen instanceof KingdomFoundingScreen founding) founding.accept(payload);
            else if (client.screen instanceof KingdomManagementScreen menu) menu.accept(payload);
            else if (client.screen instanceof PoliticalMapScreen map) map.acceptManagement(payload);
        });
        ClientPlayNetworking.registerGlobalReceiver(Snapshot.TYPE, (payload, context) -> {
            if(context.client().screen instanceof PoliticalMapScreen map) map.accept(payload);
        });
    }
}
