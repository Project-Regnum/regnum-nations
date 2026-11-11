package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.networking.CitizenManagementPayloads.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Fabric receivers run on the authenticated player's server thread. */
public final class CitizenManagementServer {
    private CitizenManagementServer() { }
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(Request.TYPE,Request.CODEC);
        PayloadTypeRegistry.playS2C().register(State.TYPE,State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE,(request,context) -> {
            var player=context.player();
            String rejected=KingdomManagementServer.gate(player,request.sequence(),request.action()!=Action.VIEW);
            State reply=rejected==null?KingdomService.get(context.server()).citizenManagement().handle(player,request):CitizenManagement.error(request.sequence(),rejected);
            if (ServerPlayNetworking.canSend(player,State.TYPE)) ServerPlayNetworking.send(player,reply);
        });
    }
}
