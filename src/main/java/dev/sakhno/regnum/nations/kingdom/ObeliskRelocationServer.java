package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.networking.ObeliskRelocationPayloads.*;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

public final class ObeliskRelocationServer {
    private ObeliskRelocationServer() { }
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(Request.TYPE,Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Reply.TYPE,Reply.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE,(request,context) -> {
            var player=context.player(); UUID token=null; boolean success=false;
            String result=KingdomManagementServer.gate(player,request.sequence(),true);
            if (result==null) try {
                var service=KingdomService.get(context.server()).relocation();
                if (request.token()==null) {
                    token=service.select(player,request.target(),request.state(),ResourceKey.create(Registries.DIMENSION,request.dimension()),request.destination());
                    result="relocation.regnum-nations.ready";
                } else { service.commit(player,request.token()); result="relocation.regnum-nations.success"; }
                success=true;
            } catch (FoundingException e) { result=e.translationKey(); }
            if (ServerPlayNetworking.canSend(player,Reply.TYPE)) ServerPlayNetworking.send(player,new Reply(request.sequence(),token,result,success));
        });
    }
}
