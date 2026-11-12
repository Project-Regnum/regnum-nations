package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.networking.KingdomAdministrationPayloads.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class KingdomAdministrationServer {
    private KingdomAdministrationServer() { }
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(Request.TYPE,Request.CODEC);
        PayloadTypeRegistry.playS2C().register(State.TYPE,State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE,(request,context) -> {
            var player=context.player();
            String rejected=KingdomManagementServer.gate(player,request.sequence(),request.action()!=Action.VIEW);
            State reply=rejected==null?KingdomService.get(context.server()).administrationView().handle(player,request):KingdomAdministrationView.error(request.sequence(),rejected);
            if (ServerPlayNetworking.canSend(player,State.TYPE)) ServerPlayNetworking.send(player,reply);
        });
    }
}
