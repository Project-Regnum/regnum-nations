package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.networking.DiplomacyPayloads.*;
import net.fabricmc.fabric.api.networking.v1.*;

public final class DiplomacyServer {
    private DiplomacyServer() { }
    public static void initialize() {
        DiplomacyConfig.load();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> KingdomService.get(server).diplomacy().expireDue());
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> KingdomService.get(server).diplomacy().expireDue());
        PayloadTypeRegistry.playC2S().register(dev.sakhno.regnum.nations.networking.InterstatePayloads.Request.TYPE,dev.sakhno.regnum.nations.networking.InterstatePayloads.Request.CODEC);
        PayloadTypeRegistry.playS2C().register(dev.sakhno.regnum.nations.networking.InterstatePayloads.State.TYPE,dev.sakhno.regnum.nations.networking.InterstatePayloads.State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(dev.sakhno.regnum.nations.networking.InterstatePayloads.Request.TYPE,(request,context) -> {
            var player=context.player();
            String rejected=KingdomManagementServer.gate(player,request.sequence(),request.action()!=dev.sakhno.regnum.nations.networking.InterstatePayloads.Action.VIEW);
            var reply=rejected==null?KingdomService.get(context.server()).diplomacy().handle(player,request):DiplomacyService.error(request.sequence(),rejected);
            if (ServerPlayNetworking.canSend(player,dev.sakhno.regnum.nations.networking.InterstatePayloads.State.TYPE)) ServerPlayNetworking.send(player,reply);
        });
        PayloadTypeRegistry.playC2S().register(Request.TYPE,Request.CODEC);
        PayloadTypeRegistry.playS2C().register(State.TYPE,State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE,(request,context) -> {
            var player=context.player();
            String rejected=KingdomManagementServer.gate(player,request.sequence(),request.action()!=Action.VIEW);
            State reply=rejected==null?KingdomService.get(context.server()).diplomacy().handle(player,request):DiplomacyService.legacyError(request.sequence(),rejected);
            if (ServerPlayNetworking.canSend(player,State.TYPE)) ServerPlayNetworking.send(player,reply);
        });
    }
}
