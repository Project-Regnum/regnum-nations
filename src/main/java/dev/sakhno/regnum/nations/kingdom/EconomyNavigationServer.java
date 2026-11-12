package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.api.v1.*;
import dev.sakhno.regnum.nations.networking.EconomyNavigationPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;

/** Navigation snapshots are advisory. Companion financial handlers must call NationsApi.authorize
 * again on the server, including dimension/distance/current market checks for location actions.
 */
public final class EconomyNavigationServer {
    private EconomyNavigationServer() { }
    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(Capabilities.TYPE, Capabilities.CODEC);
        PayloadTypeRegistry.playS2C().register(State.TYPE, State.CODEC);
        PayloadTypeRegistry.playC2S().register(Request.TYPE, Request.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (ServerPlayNetworking.canSend(handler.player, Capabilities.TYPE))
                ServerPlayNetworking.send(handler.player, new Capabilities(NationsIntegrations.economyProviders()));
        });
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (request, context) -> {
            var player = context.player(); var api = NationsApi.get(context.server());
            ApiResult.Status status = ApiResult.Status.UNAVAILABLE;
            Optional<EntitySnapshot> entity = Optional.empty(); String role = ""; Set<EconomyPermission> permissions = Set.of();
            String rejected = KingdomManagementServer.gate(player, request.sequence(), false);
            if (rejected == null && !NationsIntegrations.economyProviders().isEmpty()) {
                var resolved = api.resolve(request.entity()); status = resolved.status();
                if (resolved.valid()) {
                    var membership = api.membership(request.entity(), player.getUUID());
                    var rights = api.permissions(player, request.entity());
                    boolean admin = player.createCommandSourceStack().hasPermission(3);
                    if (!rights.valid() || !membership.valid() && !admin) status = ApiResult.Status.UNAUTHORIZED;
                    else {
                        entity = resolved.value(); permissions = rights.value().orElseThrow();
                        role = membership.value().map(GovernmentMembership::role).orElse("ADMINISTRATOR");
                    }
                }
            }
            if (ServerPlayNetworking.canSend(player, State.TYPE)) ServerPlayNetworking.send(player,
                    new State(request.sequence(), status, entity, role, permissions));
        });
    }
}
