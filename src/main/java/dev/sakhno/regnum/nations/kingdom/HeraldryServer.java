package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.networking.HeraldryPayloads.*;
import static dev.sakhno.regnum.nations.networking.HeraldryPayloads.BATCH_SIZE;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Bounded requests and public insignia deltas; no banner scans or per-tick synchronization. */
public final class HeraldryServer {
    private record Session(ServerPlayer player,UUID kingdom,UUID token,Heraldry baseline,long at,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) { }
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<MinecraftServer,Map<UUID,Heraldry>> PUBLISHED=new IdentityHashMap<>();
    private HeraldryServer() { }
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(Request.TYPE,Request.CODEC);
        PayloadTypeRegistry.playS2C().register(State.TYPE,State.CODEC);
        PayloadTypeRegistry.playS2C().register(Sync.TYPE,Sync.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> login(handler.player,KingdomService.get(server).data()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> SESSIONS.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { PUBLISHED.remove(server); SESSIONS.entrySet().removeIf(e -> e.getValue().player.serverLevel().getServer()==server); });
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE,(request,context) -> {
            var player=context.player(); String rejected=KingdomManagementServer.gate(player,request.sequence(),request.action()!=Action.VIEW);
            var reply=rejected==null?handle(player,request):error(request.sequence(),rejected);
            if (player.connection!=null&&ServerPlayNetworking.canSend(player,State.TYPE)) ServerPlayNetworking.send(player,reply);
        });
    }
    static State handle(ServerPlayer player,Request request) {
        try {
            var service=KingdomService.get(player.serverLevel().getServer()); service.validatePlayer(player);
            var data=service.data(); UUID id=request.kingdom();
            if (id==null&&request.action()==Action.VIEW) { var own=data.playerKingdom(player.getUUID()); id=own==null?null:own.id(); }
            var kingdom=data.byId(id); if (kingdom==null) return error(request.sequence(),"ui.regnum-nations.kingdom_missing");
            var heraldry=new HeraldryService(data); boolean success=false; String result="";
            if (request.action()!=Action.VIEW) {
                var session=SESSIONS.remove(player.getUUID());
                if (!heraldry.canEdit(id,player.getUUID())) return error(request.sequence(),"heraldry.regnum-nations.forbidden");
                if (session==null||session.player!=player||!session.kingdom.equals(id)||!session.token.equals(request.token())
                        ||!session.dimension.equals(player.level().dimension())||System.nanoTime()-session.at>300_000_000_000L
                        ||!session.baseline.equals(data.heraldry(id))) return error(request.sequence(),"heraldry.regnum-nations.stale");
                if (request.action()==Action.SAVE) {
                    if (request.arms()==null) return error(request.sequence(),"heraldry.regnum-nations.invalid");
                    heraldry.save(id,player.getUUID(),request.arms()); reconcile(player.serverLevel().getServer(),data);
                    result="heraldry.regnum-nations.saved";
                } else {
                    try { heraldry.seal(player,id); } catch (IllegalArgumentException e) { return error(request.sequence(),"heraldry.regnum-nations.hold_blank"); }
                    result="heraldry.regnum-nations.sealed";
                }
                success=true;
            }
            boolean editable=heraldry.canEdit(id,player.getUUID()); UUID token=editable?UUID.randomUUID():null;
            if (editable) SESSIONS.put(player.getUUID(),new Session(player,id,token,data.heraldry(id),System.nanoTime(),player.level().dimension()));
            return new State(request.sequence(),id,kingdom.name().display(),data.heraldry(id),editable,token,result,success);
        } catch (FoundingException e) { return error(request.sequence(),e.translationKey()); }
    }
    static State error(int sequence,String key) { return new State(sequence,null,"",null,false,null,key,false); }
    static void login(ServerPlayer player,KingdomSavedData data) {
        var entries=data.kingdoms().stream().sorted(Comparator.comparing(Kingdom::id)).map(k -> new Entry(k.id(),data.heraldry(k.id()))).toList();
        // Seed the baseline even when no political change has happened since server startup.
        // Otherwise the first deletion cannot emit a tombstone for arms already sent at login.
        PUBLISHED.computeIfAbsent(player.serverLevel().getServer(),server -> {
            var snapshot=new HashMap<UUID,Heraldry>();
            for (var entry:entries) snapshot.put(entry.kingdom(),entry.arms());
            return snapshot;
        });
        send(player,true,entries);
    }
    public static void reconcile(MinecraftServer server,KingdomSavedData data) {
        var changed=changes(server,data);
        if (!changed.isEmpty()) for (var player:server.getPlayerList().getPlayers()) send(player,false,changed);
    }
    static List<Entry> changes(MinecraftServer server,KingdomSavedData data) {
        var previous=PUBLISHED.computeIfAbsent(server,key -> new HashMap<>()); var current=new HashMap<UUID,Heraldry>(); var changed=new ArrayList<Entry>();
        for (var k:data.kingdoms()) { var arms=data.heraldry(k.id()); current.put(k.id(),arms); if (!arms.equals(previous.get(k.id()))) changed.add(new Entry(k.id(),arms)); }
        for (var id:previous.keySet()) if (!current.containsKey(id)) changed.add(new Entry(id,null));
        previous.clear(); previous.putAll(current);
        return List.copyOf(changed);
    }
    private static void send(ServerPlayer player,boolean reset,List<Entry> entries) {
        if (player.connection==null||!ServerPlayNetworking.canSend(player,Sync.TYPE)) return;
        for (var packet:batches(reset,entries)) ServerPlayNetworking.send(player,packet);
    }
    /** Shared by login and deltas. Only the first login batch clears the client's cache. */
    static List<Sync> batches(boolean reset,List<Entry> entries) {
        if (entries.isEmpty()) return reset?List.of(new Sync(true,List.of())):List.of();
        var packets=new ArrayList<Sync>();
        for (int i=0;i<entries.size();i+=BATCH_SIZE)
            packets.add(new Sync(reset&&i==0,entries.subList(i,Math.min(i+BATCH_SIZE,entries.size()))));
        return List.copyOf(packets);
    }
}
