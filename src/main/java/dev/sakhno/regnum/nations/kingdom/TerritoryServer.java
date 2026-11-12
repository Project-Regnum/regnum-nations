package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.networking.TerritoryPayloads.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;
import static dev.sakhno.regnum.nations.networking.TerritoryPayloads.PAGE_SIZE;

/** Server-thread territory workflows, bounded local queries and targeted border notifications. */
public final class TerritoryServer {
    private record Confirmation(UUID token,UUID city,ClaimPos pos,long revision,long expiresAt) { }
    private record Neighbor(UUID city,UUID kingdom,int disputes) { }
    private record Border(ClaimPos pos,UUID city,UUID kingdom,int foreignEdges,int disputes,List<Neighbor> neighbors) { }
    private static final Map<UUID,Confirmation> CONFIRMATIONS=new HashMap<>();
    private static final Map<UUID,Border> BORDERS=new HashMap<>();
    private TerritoryServer() { }
    public static void initialize() {
        TerritoryConfig.load();
        PayloadTypeRegistry.playC2S().register(Request.TYPE,Request.CODEC);
        PayloadTypeRegistry.playS2C().register(State.TYPE,State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE,(request,context) -> {
            String rejected=KingdomManagementServer.gate(context.player(),request.sequence(),request.action()!=Action.VIEW);
            State reply=rejected==null?handle(context.player(),request):error(request,rejected);
            if (ServerPlayNetworking.canSend(context.player(),State.TYPE)) ServerPlayNetworking.send(context.player(),reply);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> { CONFIRMATIONS.remove(handler.player.getUUID()); BORDERS.remove(handler.player.getUUID()); });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { CONFIRMATIONS.clear(); BORDERS.clear(); });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount()%20!=0) return;
            KingdomSavedData data=KingdomService.get(server).data(); if (!data.available()) return;
            data.politicalClaims.expireDue();
            CONFIRMATIONS.values().removeIf(c -> c.expiresAt()<System.nanoTime());
            for (ServerPlayer player:server.getPlayerList().getPlayers()) updateBorder(data,player);
        });
    }
    private static ClaimPos position(Request r) { return new ClaimPos(ResourceKey.create(Registries.DIMENSION,r.dimension()),r.x(),r.z()); }
    static UUID prepareAbandonment(ServerPlayer player,ClaimPos pos,UUID viewed) throws FoundingException {
        KingdomSavedData data=KingdomService.get(player.serverLevel().getServer()).data();
        Settlement city=new ClaimService(data).validate(player,pos,viewed,true);
        UUID token=UUID.randomUUID();
        CONFIRMATIONS.put(player.getUUID(),new Confirmation(token,city.id(),pos,data.territoryRevision(city.id()),System.nanoTime()+30_000_000_000L));
        return token;
    }
    static void consumeAbandonment(ServerPlayer player,ClaimPos pos,UUID city,UUID token) throws FoundingException {
        Confirmation pending=CONFIRMATIONS.remove(player.getUUID());
        KingdomSavedData data=KingdomService.get(player.serverLevel().getServer()).data();
        if (pending==null||token==null||!token.equals(pending.token())||!city.equals(pending.city())||!pos.equals(pending.pos())
                ||pending.revision()!=data.territoryRevision(city)||pending.expiresAt()<System.nanoTime()) throw new FoundingException(CLAIM_CONFIRMATION);
    }
    static void confirmCommand(ServerPlayer player) throws FoundingException {
        Confirmation c=CONFIRMATIONS.get(player.getUUID());
        if (c==null) throw new FoundingException(CLAIM_CONFIRMATION);
        new ClaimService(KingdomService.get(player.serverLevel().getServer()).data()).manage(player,c.pos(),c.city(),true,c.token());
    }
    private static State handle(ServerPlayer player,Request request) {
        try {
            KingdomSavedData data=KingdomService.get(player.serverLevel().getServer()).data(); data.requireAvailable();
            if (!player.isAlive()||player.isRemoved()||player.isSpectator()) throw new FoundingException(INVALID_PLAYER);
            data.politicalClaims.expireDue();
            Settlement own=data.citizenCity(player.getUUID()); Kingdom kingdom=data.playerKingdom(player.getUUID());
            Settlement viewed=request.city()==null?own:data.settlement(request.city());
            boolean kingdomView=kingdom!=null&&KingdomPermissions.allows(data,kingdom.id(),player.getUUID(),KingdomPermission.VIEW_TERRITORY);
            if (viewed!=null&&!CityPermissions.allows(data,viewed,player.getUUID(),CityPermission.VIEW_TERRITORY)
                    &&!(kingdomView&&kingdom.id().equals(viewed.sovereign()))) throw new FoundingException(NOT_OWNER);
            if (request.tab()==Tab.KINGDOM&&!kingdomView) throw new FoundingException(NO_KINGDOM);
            ClaimPos pos=request.action()==Action.WITHDRAW?ClaimPos.at(player.level().dimension(),player.blockPosition()):position(request);
            boolean nearby=nearby(player,pos);
            if (!nearby&&(viewed==null||!viewed.id().equals(data.claimOwner(pos)))) throw new FoundingException(TOO_FAR);
            UUID confirmation=null; String result=""; boolean success=false;
            if (request.action()!=Action.VIEW) {
                switch (request.action()) {
                    case EXPAND,PREPARE_ABANDON,ABANDON -> {
                        if (own==null||viewed==null||!own.id().equals(viewed.id())) throw new FoundingException(NOT_OWNER);
                        if (request.action()==Action.PREPARE_ABANDON) confirmation=prepareAbandonment(player,pos,viewed.id());
                        else new ClaimService(data).manage(player,pos,viewed.id(),request.action()==Action.ABANDON,request.confirmation());
                    }
                    case DEMAND,WITHDRAW -> {
                        if (kingdom==null||!kingdom.id().equals(request.kingdom())||!KingdomPermissions.allows(data,kingdom.id(),player.getUUID(),KingdomPermission.MANAGE_TERRITORIAL_CLAIMS)) throw new FoundingException(NOT_OWNER);
                        if (request.action()==Action.DEMAND) {
                            if (!nearby) throw new FoundingException(TOO_FAR);
                            data.politicalClaims.create(kingdom.id(),pos,request.days());
                        } else data.politicalClaims.withdraw(kingdom.id(),request.demand());
                    }
                    default -> throw new IllegalArgumentException("Unexpected territory action");
                }
                result="territory.regnum-nations."+(request.action()==Action.PREPARE_ABANDON?"confirm_ready":"success"); success=true;
            }
            return snapshot(data,player,request,viewed,kingdom,pos,confirmation,result,success);
        } catch (FoundingException error) { return error(request,error.translationKey()); }
    }
    private static boolean nearby(ServerPlayer player,ClaimPos pos) {
        ClaimPos current=ClaimPos.at(player.level().dimension(),player.blockPosition());
        return current.dimension().equals(pos.dimension())&&Math.abs((long)pos.x()-current.x())<=32&&Math.abs((long)pos.z()-current.z())<=32;
    }
    private static State snapshot(KingdomSavedData data,ServerPlayer player,Request r,Settlement city,Kingdom kingdom,ClaimPos pos,UUID confirmation,String result,boolean success) {
        UUID cityId=city==null?null:city.id(),kingdomId=kingdom==null?null:kingdom.id();
        List<Cell> cells=new ArrayList<>(); List<CityEntry> cities=new ArrayList<>(); List<DemandEntry> demands=new ArrayList<>();
        int matches=0,page=Math.max(0,Math.min(r.page(),1_000_000));
        if (r.tab()==Tab.LOCAL) {
            ClaimPos center=ClaimPos.at(player.level().dimension(),player.blockPosition());
            for (int z=-3;z<=3;z++) for (int x=-3;x<=3;x++) cells.add(cell(data,center.offset(x,z),cityId));
            page=0;
        } else if (r.tab()==Tab.CITY&&city!=null) {
            var all=data.settlementClaims(city.id()).stream().sorted(Comparator.comparing((ClaimPos p) -> p.dimension().location().toString()).thenComparingInt(ClaimPos::x).thenComparingInt(ClaimPos::z)).toList();
            matches=all.size(); page=page(page,matches);
            for (ClaimPos p:all.stream().skip((long)page*PAGE_SIZE).limit(PAGE_SIZE).toList()) cells.add(cell(data,p,cityId));
        } else if (r.tab()==Tab.KINGDOM&&kingdom!=null) {
            var all=data.settlements().stream().filter(c -> kingdom.id().equals(c.sovereign())).sorted(Comparator.comparing(c -> c.name().key())).toList();
            matches=all.size(); page=page(page,matches);
            for (Settlement c:all.stream().skip((long)page*PAGE_SIZE).limit(PAGE_SIZE).toList()) {
                int borders=0,disputes=0;
                for (ClaimPos p:data.settlementClaims(c.id())) { if (foreignEdges(data,p,c.id())>0) borders++; if (data.politicalClaims.countAt(p)>0) disputes++; }
                cities.add(new CityEntry(c.id(),c.name().display(),data.settlementClaimCount(c.id()),borders,disputes));
            }
        } else if (r.tab()==Tab.DISPUTES) {
            var all=data.politicalClaims.involvingOrAt(kingdomId,pos); matches=all.size(); page=page(page,matches);
            for (var d:all.stream().skip((long)page*PAGE_SIZE).limit(PAGE_SIZE).toList()) demands.add(new DemandEntry(d.id(),d.claimant(),d.owner(),data.byId(d.claimant()).name().display(),data.byId(d.owner()).name().display(),cell(data,d.territory(),cityId),d.createdAt(),d.expiresAt(),kingdom!=null&&d.claimant().equals(kingdom.id())&&KingdomPermissions.allows(data,kingdom.id(),player.getUUID(),KingdomPermission.MANAGE_TERRITORIAL_CLAIMS)));
        }
        boolean political=kingdom!=null&&KingdomPermissions.allows(data,kingdom.id(),player.getUUID(),KingdomPermission.MANAGE_TERRITORIAL_CLAIMS);
        return new State(r.sequence(),r.tab(),page,matches,cityId,city==null?"":city.name().display(),kingdomId,kingdom==null?"":kingdom.name().display(),city==null?0:data.settlementClaimCount(city.id()),TerritoryConfig.settlementLimit(),kingdom==null?0:data.claimCount(kingdom.id()),cells,cities,demands,cell(data,pos,cityId),reason(data,player,pos,cityId,false),reason(data,player,pos,cityId,true),political,kingdom!=null&&data.politicalClaims.has(kingdom.id(),pos),confirmation,result,success);
    }
    private static int page(int page,int matches) { return Math.min(page,Math.max(0,(matches-1)/PAGE_SIZE)); }
    private static String reason(KingdomSavedData data,ServerPlayer player,ClaimPos pos,UUID city,boolean release) {
        try { new ClaimService(data).preview(player,pos,city,release); return ""; }
        catch (FoundingException error) { return error.translationKey(); }
    }
    private static Cell cell(KingdomSavedData data,ClaimPos pos,UUID viewerCity) {
        Settlement city=data.settlement(data.claimOwner(pos)); Kingdom kingdom=data.sovereign(city);
        return new Cell(pos.dimension().location(),pos.x(),pos.z(),city==null?null:city.id(),kingdom==null?null:kingdom.id(),city==null?"":city.name().display(),kingdom==null?"":kingdom.name().display(),foreignEdges(data,pos,city==null?viewerCity:city.id()),data.politicalClaims.countAt(pos),new ClaimService(data).adjacent(viewerCity,pos));
    }
    private static int foreignEdges(KingdomSavedData data,ClaimPos pos,UUID reference) {
        Settlement base=data.settlement(reference); int count=0;
        for (ClaimPos neighbor:ClaimService.neighbors(pos)) {
            Settlement other=data.settlement(data.claimOwner(neighbor));
            if (other!=null&&(base==null||!other.id().equals(base.id())&&(base.sovereign()==null||!base.sovereign().equals(other.sovereign())))) count++;
        }
        return count;
    }
    private static void updateBorder(KingdomSavedData data,ServerPlayer player) {
        ClaimPos pos=ClaimPos.at(player.level().dimension(),player.blockPosition());
        Cell here=cell(data,pos,null);
        List<Neighbor> neighbors=new ArrayList<>();
        for (ClaimPos p:ClaimService.neighbors(pos)) {
            UUID owner=data.claimOwner(p); Settlement neighbor=data.settlement(owner);
            neighbors.add(new Neighbor(owner,neighbor==null?null:neighbor.sovereign(),data.politicalClaims.countAt(p)));
        }
        Border now=new Border(pos,here.city(),here.kingdom(),here.foreignEdges(),here.disputes(),List.copyOf(neighbors));
        Border before=BORDERS.put(player.getUUID(),now);
        if (before==null) return;
        if (!Objects.equals(before.city(),now.city())||!Objects.equals(before.kingdom(),now.kingdom())||!before.pos().dimension().equals(pos.dimension())) {
            String classification="wilderness";
            try {
                Settlement own=data.citizenCity(player.getUUID());
                if (here.city()!=null) classification=own!=null&&(here.city().equals(own.id())||own.sovereign()!=null&&own.sovereign().equals(here.kingdom()))?"city_territory":"foreign_territory";
            } catch (FoundingException ignored) { return; }
            player.displayClientMessage(Component.translatable("territory.regnum-nations.crossing",Component.translatable("territory.regnum-nations."+classification),here.cityName(),here.kingdomName()),true);
        } else if (before.pos().equals(now.pos())&&(!before.neighbors().equals(now.neighbors())||before.disputes()!=now.disputes())) player.displayClientMessage(Component.translatable("territory.regnum-nations.nearby_change"),true);
        else if (now.foreignEdges()>0&&before.foreignEdges()!=now.foreignEdges()) player.displayClientMessage(Component.translatable("territory.regnum-nations.border_near",now.foreignEdges()),true);
        if ((before.disputes()!=now.disputes()||!before.pos().equals(now.pos()))&&now.disputes()>0) player.displayClientMessage(Component.translatable("territory.regnum-nations.disputed_warning"),false);
    }
    static void describe(KingdomSavedData data,ServerPlayer player) throws FoundingException {
        Settlement own=data.citizenCity(player.getUUID());
        if (own!=null&&!CityPermissions.allows(data,own,player.getUUID(),CityPermission.VIEW_TERRITORY)) throw new FoundingException(NOT_OWNER);
        ClaimPos pos=ClaimPos.at(player.level().dimension(),player.blockPosition()); Cell c=cell(data,pos,own==null?null:own.id());
        player.sendSystemMessage(Component.translatable("territory.regnum-nations.border_info",c.cityName(),c.kingdomName(),c.foreignEdges(),c.disputes()));
        for (ClaimPos next:ClaimService.neighbors(pos)) {
            Cell neighbor=cell(data,next,own==null?null:own.id());
            player.sendSystemMessage(Component.translatable("territory.regnum-nations.neighbor_info",next.x(),next.z(),neighbor.city()==null?Component.translatable("territory.regnum-nations.wilderness"):Component.literal(neighbor.cityName()),neighbor.kingdomName()));
        }
        if (own!=null) player.sendSystemMessage(Component.translatable("territory.regnum-nations.capacity",data.settlementClaimCount(own.id()),TerritoryConfig.settlementLimit(),Math.max(0,TerritoryConfig.settlementLimit()-data.settlementClaimCount(own.id()))));
    }
    private static State error(Request r,String message) {
        Cell empty=new Cell(r.dimension(),r.x(),r.z(),null,null,"","",0,0,false);
        return new State(r.sequence(),r.tab(),0,0,null,"",null,"",0,TerritoryConfig.settlementLimit(),0,List.of(),List.of(),List.of(),empty,message,message,false,false,null,message,false);
    }
}
