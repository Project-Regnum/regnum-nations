package dev.sakhno.regnum.nations.networking;

import dev.sakhno.regnum.nations.RegnumNations;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Additive territory channels. Snapshots and lists have explicit wire bounds. */
public final class TerritoryPayloads {
    public static final int PAGE_SIZE=6;
    private TerritoryPayloads() { }
    public enum Tab { LOCAL, CITY, KINGDOM, DISPUTES }
    public enum Action { VIEW, EXPAND, PREPARE_ABANDON, ABANDON, DEMAND, WITHDRAW }
    public record Cell(ResourceLocation dimension,int x,int z,UUID city,UUID kingdom,String cityName,String kingdomName,int foreignEdges,int disputes,boolean adjacent) { }
    public record CityEntry(UUID id,String name,long claims,int borders,int disputes) { }
    public record DemandEntry(UUID id,UUID claimant,UUID owner,String claimantName,String ownerName,Cell territory,long createdAt,long expiresAt,boolean withdraw) { }
    public record Request(int sequence,Action action,Tab tab,int page,UUID city,UUID kingdom,ResourceLocation dimension,int x,int z,UUID demand,UUID confirmation,int days) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(RegnumNations.id("territory_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); b.writeEnum(p.action); b.writeEnum(p.tab); b.writeVarInt(p.page); uuid(b,p.city); uuid(b,p.kingdom);
            b.writeResourceLocation(p.dimension); b.writeInt(p.x); b.writeInt(p.z); uuid(b,p.demand); uuid(b,p.confirmation); b.writeVarInt(p.days);
        },b -> new Request(b.readInt(),b.readEnum(Action.class),b.readEnum(Tab.class),b.readVarInt(),uuid(b),uuid(b),b.readResourceLocation(),b.readInt(),b.readInt(),uuid(b),uuid(b),b.readVarInt()));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record State(int sequence,Tab tab,int page,int matches,UUID city,String cityName,UUID kingdom,String kingdomName,long cityClaims,int limit,long kingdomClaims,
                        List<Cell> cells,List<CityEntry> cities,List<DemandEntry> demands,Cell selection,String claimReason,String abandonReason,boolean politicalAllowed,boolean existingDemand,
                        UUID confirmation,String result,boolean success) implements CustomPacketPayload {
        public State { cells=List.copyOf(cells); cities=List.copyOf(cities); demands=List.copyOf(demands); if (cells.size()>49||cities.size()>PAGE_SIZE||demands.size()>PAGE_SIZE) throw new IllegalArgumentException("Unbounded territory state"); }
        public static final Type<State> TYPE=new Type<>(RegnumNations.id("territory_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); b.writeEnum(p.tab); b.writeVarInt(p.page); b.writeVarInt(p.matches); uuid(b,p.city); b.writeUtf(p.cityName,64); uuid(b,p.kingdom); b.writeUtf(p.kingdomName,64);
            b.writeLong(p.cityClaims); b.writeVarInt(p.limit); b.writeLong(p.kingdomClaims);
            b.writeVarInt(p.cells.size()); for (Cell c:p.cells) cell(b,c);
            b.writeVarInt(p.cities.size()); for (CityEntry c:p.cities) { b.writeUUID(c.id); b.writeUtf(c.name,64); b.writeLong(c.claims); b.writeVarInt(c.borders); b.writeVarInt(c.disputes); }
            b.writeVarInt(p.demands.size()); for (DemandEntry d:p.demands) { b.writeUUID(d.id); b.writeUUID(d.claimant); b.writeUUID(d.owner); b.writeUtf(d.claimantName,64); b.writeUtf(d.ownerName,64); cell(b,d.territory); b.writeLong(d.createdAt); b.writeLong(d.expiresAt); b.writeBoolean(d.withdraw); }
            cell(b,p.selection); b.writeUtf(p.claimReason,128); b.writeUtf(p.abandonReason,128); b.writeBoolean(p.politicalAllowed); b.writeBoolean(p.existingDemand); uuid(b,p.confirmation); b.writeUtf(p.result,128); b.writeBoolean(p.success);
        },b -> {
            int sequence=b.readInt(); Tab tab=b.readEnum(Tab.class); int page=b.readVarInt(),matches=b.readVarInt(); UUID city=uuid(b); String name=b.readUtf(64); UUID kingdom=uuid(b); String kingdomName=b.readUtf(64);
            long claims=b.readLong(); int limit=b.readVarInt(); long aggregate=b.readLong();
            List<Cell> cells=new ArrayList<>(); for (int i=0,n=size(b,49);i<n;i++) cells.add(cell(b));
            List<CityEntry> cities=new ArrayList<>(); for (int i=0,n=size(b,PAGE_SIZE);i<n;i++) cities.add(new CityEntry(b.readUUID(),b.readUtf(64),b.readLong(),b.readVarInt(),b.readVarInt()));
            List<DemandEntry> demands=new ArrayList<>(); for (int i=0,n=size(b,PAGE_SIZE);i<n;i++) demands.add(new DemandEntry(b.readUUID(),b.readUUID(),b.readUUID(),b.readUtf(64),b.readUtf(64),cell(b),b.readLong(),b.readLong(),b.readBoolean()));
            return new State(sequence,tab,page,matches,city,name,kingdom,kingdomName,claims,limit,aggregate,cells,cities,demands,cell(b),b.readUtf(128),b.readUtf(128),b.readBoolean(),b.readBoolean(),uuid(b),b.readUtf(128),b.readBoolean());
        });
        @Override public Type<State> type() { return TYPE; }
    }
    private static int size(RegistryFriendlyByteBuf b,int max) { int n=b.readVarInt(); if (n<0||n>max) throw new IllegalArgumentException("Unbounded territory list"); return n; }
    private static void cell(RegistryFriendlyByteBuf b,Cell c) {
        b.writeResourceLocation(c.dimension); b.writeInt(c.x); b.writeInt(c.z); uuid(b,c.city); uuid(b,c.kingdom); b.writeUtf(c.cityName,64); b.writeUtf(c.kingdomName,64); b.writeVarInt(c.foreignEdges); b.writeVarInt(c.disputes); b.writeBoolean(c.adjacent);
    }
    private static Cell cell(RegistryFriendlyByteBuf b) { return new Cell(b.readResourceLocation(),b.readInt(),b.readInt(),uuid(b),uuid(b),b.readUtf(64),b.readUtf(64),b.readVarInt(),b.readVarInt(),b.readBoolean()); }
    private static void uuid(RegistryFriendlyByteBuf b,UUID id) { b.writeNullable(id,RegistryFriendlyByteBuf::writeUUID); }
    private static UUID uuid(RegistryFriendlyByteBuf b) { return b.readNullable(RegistryFriendlyByteBuf::readUUID); }
}
