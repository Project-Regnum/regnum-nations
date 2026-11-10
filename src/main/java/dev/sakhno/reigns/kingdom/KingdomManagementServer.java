package dev.sakhno.reigns.kingdom;

import dev.sakhno.reigns.networking.KingdomManagementPayloads.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import static dev.sakhno.reigns.networking.KingdomManagementPayloads.*;

/** Thin server-thread adapters; all gameplay decisions remain in the existing services. */
public final class KingdomManagementServer {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final class Session {
        long readAt, actionAt;
        int lastMutation;
    }
    private KingdomManagementServer() { }

    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(Found.TYPE, Found.CODEC);
        PayloadTypeRegistry.playC2S().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(State.TYPE, State.CODEC);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
        ServerPlayNetworking.registerGlobalReceiver(Found.TYPE, (request, context) -> {
            ServerPlayer player = context.player();
            String rejected = gate(player, request.sequence(), true);
            if (rejected != null) { send(player, error(request.sequence(), rejected)); return; }
            try {
                var service=KingdomService.get(context.server());
                UUID city;
                if (request.city()) city=service.cities().found(player,request.obelisk(),request.name(),request.color()).id();
                else city=service.found(player,request.obelisk(),request.name(),request.color()).capitalSettlementId();
                send(player,state(player,request.sequence(),RESPONSE,city,null,request.city()?"city.project-reigns.founded":"ui.project-reigns.founded",true));
            } catch (FoundingException e) { send(player, error(request.sequence(), e.translationKey())); }
        });
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (request, context) -> {
            ServerPlayer player = context.player();
            boolean mutation = request.action() == Action.CLAIM || request.action() == Action.UNCLAIM;
            String rejected = gate(player, request.sequence(), mutation);
            if (rejected != null) {
                // A small explicit rejection avoids leaving a legitimate rapid refresh loading.
                send(player, error(request.sequence(), rejected));
                return;
            }
            ClaimPos selected = request.action() == Action.VIEW ? null : new ClaimPos(
                    ResourceKey.create(Registries.DIMENSION, request.dimension()), request.x(), request.z());
            String result = "";
            boolean success = false;
            try {
                var service = KingdomService.get(context.server());
                service.data().requireAvailable();
                if (mutation) {
                    new ClaimService(service.data()).manage(player, selected, request.kingdom(), request.action() == Action.UNCLAIM);
                    result = "ui.project-reigns." + (request.action() == Action.UNCLAIM ? "unclaimed" : "claimed");
                    success = true;
                }
                send(player, state(player, request.sequence(), RESPONSE, request.kingdom(), selected, result, success));
            } catch (FoundingException e) { send(player, error(request.sequence(), e.translationKey())); }
        });
    }

    static String gate(ServerPlayer player, int sequence, boolean mutation) {
        if (!player.isAlive() || player.isRemoved()) return "message.project-reigns.founding.invalid_player";
        Session session = SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
        long now = System.nanoTime();
        if (mutation) {
            if (sequence <= session.lastMutation) return "ui.project-reigns.duplicate";
            session.lastMutation = sequence; // Even rejected attempts cannot later be replayed.
            if (now - session.actionAt < 500_000_000L) return "ui.project-reigns.rate_limit";
            session.actionAt = now;
        } else {
            if (now - session.readAt < 150_000_000L) return "ui.project-reigns.rate_limit";
            session.readAt = now;
        }
        return null;
    }

    public static boolean openFounding(ServerPlayer player, UUID obelisk) {
        return send(player, new State(0, OPEN_FOUNDING, obelisk, null, null, null, "", false,null,null));
    }

    /** One event per confirmed dissolution; clients refresh only their currently open views. */
    static void invalidate(MinecraftServer server) {
        for (ServerPlayer player:server.getPlayerList().getPlayers())
            send(player,new State(0,INVALIDATE,null,null,null,null,"",false,null,null));
    }

    public static boolean openMenu(ServerPlayer player, UUID kingdom) {
        try { return send(player, state(player, 0, OPEN_MENU, kingdom, null, "", false)); }
        catch (FoundingException e) { return send(player, new State(0, OPEN_MENU, null, null, null, null, e.translationKey(), false,null,null)); }
    }

    static State state(ServerPlayer player, int sequence, int intent, UUID target, ClaimPos pos,
                               String result, boolean success) throws FoundingException {
        var service = KingdomService.get(player.serverLevel().getServer());
        var data = service.data();
        data.requireAvailable();
        Settlement own=data.byOwner(player.getUUID());
        Settlement city=target==null?own:data.settlement(target);
        if (city==null&&target!=null&&data.byId(target)!=null) city=data.settlement(data.byId(target).capitalSettlementId());
        if (target!=null&&city==null) return error(sequence,"ui.project-reigns.kingdom_missing");
        Kingdom kingdom=data.sovereign(city);
        Overview overview=null; CityOverview cityView=null;
        if (kingdom!=null) {
            var status=service.resolveCapital(kingdom);
            overview=new Overview(kingdom.id(),kingdom.name().display(),playerName(player,kingdom.founderId()),kingdom.dimension().location(),kingdom.capitalPos(),
                    status.name().toLowerCase(Locale.ROOT),data.claimCount(kingdom.id()),kingdom.founderId().equals(player.getUUID()),
                    !kingdom.capitalSettlementId().equals(data.claimOwner(ClaimPos.at(kingdom.dimension(),kingdom.capitalPos()))),kingdom.color());
        }
        if (city!=null) {
            var status=service.cities().resolve(city);
            boolean repair=data.ownedSettlements(city.ownerId()).size()>1||!city.id().equals(data.claimOwner(ClaimPos.at(city.dimension(),city.obeliskPos())));
            cityView=new CityOverview(city.id(),city.name().display(),playerName(player,city.ownerId()),city.dimension().location(),city.obeliskPos(),
                    status.name().toLowerCase(Locale.ROOT),data.settlementClaimCount(city.id()),city.ownerId().equals(player.getUUID()),city.sovereign(),
                    kingdom==null?"":kingdom.name().display(),data.politicalColor(city),repair);
        }
        Selection selection=null;
        if (pos!=null) {
            var claims=new ClaimService(data); Settlement owner=data.settlement(data.claimOwner(pos)); Kingdom political=data.sovereign(owner);
            UUID viewed=city==null?null:city.id();
            selection=new Selection(pos.dimension().location(),pos.x(),pos.z(),owner==null?"":owner.name().display(),claims.adjacent(viewed,pos),
                    reason(claims,player,pos,viewed,false),reason(claims,player,pos,viewed,true),owner==null?null:owner.id(),owner==null?null:owner.sovereign(),
                    political==null?"":political.name().display(),owner==null?0:data.politicalColor(owner));
        }
        return new State(sequence,intent,null,own==null?null:own.sovereign(),overview,selection,result,success,own==null?null:own.id(),cityView);
    }
    private static String playerName(ServerPlayer viewer,UUID id) {
        var server=viewer.serverLevel().getServer(); var online=server.getPlayerList().getPlayer(id);
        if (online!=null) return online.getGameProfile().getName();
        var cache=server.getProfileCache(); return cache==null?"":cache.get(id).map(profile -> profile.getName()).orElse("");
    }
    public static boolean openCityFounding(ServerPlayer player,UUID obelisk) {
        return send(player,new State(0,OPEN_CITY_FOUNDING,obelisk,null,null,null,"",false,null,null));
    }
    public static boolean openCityMenu(ServerPlayer player,UUID city) {
        try { return send(player,state(player,0,OPEN_CITY_MENU,city,null,"",false)); }
        catch (FoundingException e) { return send(player,new State(0,OPEN_CITY_MENU,null,null,null,null,e.translationKey(),false,null,null)); }
    }

    private static String reason(ClaimService claims, ServerPlayer player, ClaimPos pos, UUID viewed, boolean release) {
        try { claims.validate(player, pos, viewed, release); return ""; }
        catch (FoundingException e) { return e.translationKey(); }
    }
    private static State error(int sequence, String result) {
        return new State(sequence, RESPONSE, null, null, null, null, result, false,null,null);
    }
    private static boolean send(ServerPlayer player, State state) {
        if (player.connection == null || !ServerPlayNetworking.canSend(player, State.TYPE)) return false;
        ServerPlayNetworking.send(player, state);
        return true;
    }
}
