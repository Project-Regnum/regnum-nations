package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.networking.WarfarePayloads.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;

/** Common-only registration: dedicated servers never load warfare screens. */
public final class WarfareServer {
    private WarfareServer() { }
    public static void initialize() {
        WarfareConfig.load();
        PayloadTypeRegistry.playC2S().register(Request.TYPE,Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Snapshot.TYPE,Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE,(request,context) -> {
            var player=context.player();
            String rejected=KingdomManagementServer.gate(player,request.sequence(),request.action()!=Action.VIEW);
            var reply=rejected==null?KingdomService.get(context.server()).warfare().handle(player,request):WarfareService.error(request.sequence(),rejected);
            if (ServerPlayNetworking.canSend(player,Snapshot.TYPE)) ServerPlayNetworking.send(player,reply);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> KingdomService.get(server).warfare().step());
        ServerTickEvents.END_SERVER_TICK.register(server -> KingdomService.get(server).warfare().step());
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> KingdomService.get(server).warfare().disconnect(handler.player));
    }
}
