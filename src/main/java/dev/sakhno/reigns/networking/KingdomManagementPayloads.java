package dev.sakhno.reigns.networking;

import dev.sakhno.reigns.ProjectReigns;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded view DTOs. Client-submitted UUIDs and palette indexes carry no authority. */
public final class KingdomManagementPayloads {
    private KingdomManagementPayloads() { }
    public enum Action { VIEW, INSPECT, CLAIM, UNCLAIM }
    public static final int RESPONSE=0, OPEN_MENU=1, OPEN_FOUNDING=2, OPEN_CITY_FOUNDING=3, OPEN_CITY_MENU=4, INVALIDATE=5;
    public record Overview(UUID id,String name,String founder,ResourceLocation dimension,BlockPos capital,String status,
                           long claims,boolean manager,boolean capitalConflict,int color) { }
    public record CityOverview(UUID id,String name,String owner,ResourceLocation dimension,BlockPos capital,String status,
                               long claims,boolean manager,UUID sovereign,String sovereignName,int color,boolean repair) { }
    public record Selection(ResourceLocation dimension,int x,int z,String owner,boolean adjacent,String claimReason,String unclaimReason,
                            UUID settlement,UUID sovereign,String politicalName,int color) { }
    public record Found(int sequence,UUID obelisk,String name,int color,boolean city) implements CustomPacketPayload {
        public static final Type<Found> TYPE=new Type<>(ProjectReigns.id("kingdom_found"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Found> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); b.writeUUID(p.obelisk); b.writeUtf(p.name,128); b.writeVarInt(p.color); b.writeBoolean(p.city);
        },b -> new Found(b.readInt(),b.readUUID(),b.readUtf(128),b.readVarInt(),b.readBoolean()));
        @Override public Type<Found> type() { return TYPE; }
    }
    /** The target is a settlement UUID; kingdom UUIDs remain compatible views of their capital city. */
    public record Request(int sequence,Action action,UUID kingdom,ResourceLocation dimension,int x,int z) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ProjectReigns.id("kingdom_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); b.writeEnum(p.action); b.writeNullable(p.kingdom,RegistryFriendlyByteBuf::writeUUID);
            b.writeResourceLocation(p.dimension); b.writeInt(p.x); b.writeInt(p.z);
        },b -> new Request(b.readInt(),b.readEnum(Action.class),b.readNullable(RegistryFriendlyByteBuf::readUUID),b.readResourceLocation(),b.readInt(),b.readInt()));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record State(int sequence,int intent,UUID obelisk,UUID ownKingdom,Overview overview,Selection selection,String result,
                        boolean success,UUID ownSettlement,CityOverview city) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ProjectReigns.id("kingdom_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); b.writeVarInt(p.intent); b.writeNullable(p.obelisk,RegistryFriendlyByteBuf::writeUUID);
            b.writeNullable(p.ownKingdom,RegistryFriendlyByteBuf::writeUUID);
            b.writeNullable(p.overview,(buf,v) -> {
                buf.writeUUID(v.id); buf.writeUtf(v.name,64); buf.writeUtf(v.founder,64); buf.writeResourceLocation(v.dimension); buf.writeBlockPos(v.capital);
                buf.writeUtf(v.status,32); buf.writeVarLong(v.claims); buf.writeBoolean(v.manager); buf.writeBoolean(v.capitalConflict); buf.writeVarInt(v.color);
            });
            b.writeNullable(p.selection,(buf,v) -> {
                buf.writeResourceLocation(v.dimension); buf.writeInt(v.x); buf.writeInt(v.z); buf.writeUtf(v.owner,64); buf.writeBoolean(v.adjacent);
                buf.writeUtf(v.claimReason,128); buf.writeUtf(v.unclaimReason,128); buf.writeNullable(v.settlement,RegistryFriendlyByteBuf::writeUUID);
                buf.writeNullable(v.sovereign,RegistryFriendlyByteBuf::writeUUID); buf.writeUtf(v.politicalName,64); buf.writeVarInt(v.color);
            });
            b.writeUtf(p.result,128); b.writeBoolean(p.success); b.writeNullable(p.ownSettlement,RegistryFriendlyByteBuf::writeUUID);
            b.writeNullable(p.city,(buf,v) -> {
                buf.writeUUID(v.id); buf.writeUtf(v.name,64); buf.writeUtf(v.owner,64); buf.writeResourceLocation(v.dimension); buf.writeBlockPos(v.capital);
                buf.writeUtf(v.status,32); buf.writeVarLong(v.claims); buf.writeBoolean(v.manager); buf.writeNullable(v.sovereign,RegistryFriendlyByteBuf::writeUUID);
                buf.writeUtf(v.sovereignName,64); buf.writeVarInt(v.color); buf.writeBoolean(v.repair);
            });
        },b -> new State(b.readInt(),b.readVarInt(),b.readNullable(RegistryFriendlyByteBuf::readUUID),b.readNullable(RegistryFriendlyByteBuf::readUUID),
                b.readNullable(buf -> new Overview(buf.readUUID(),buf.readUtf(64),buf.readUtf(64),buf.readResourceLocation(),buf.readBlockPos(),buf.readUtf(32),buf.readVarLong(),buf.readBoolean(),buf.readBoolean(),buf.readVarInt())),
                b.readNullable(buf -> new Selection(buf.readResourceLocation(),buf.readInt(),buf.readInt(),buf.readUtf(64),buf.readBoolean(),buf.readUtf(128),buf.readUtf(128),
                        buf.readNullable(RegistryFriendlyByteBuf::readUUID),buf.readNullable(RegistryFriendlyByteBuf::readUUID),buf.readUtf(64),buf.readVarInt())),
                b.readUtf(128),b.readBoolean(),b.readNullable(RegistryFriendlyByteBuf::readUUID),
                b.readNullable(buf -> new CityOverview(buf.readUUID(),buf.readUtf(64),buf.readUtf(64),buf.readResourceLocation(),buf.readBlockPos(),buf.readUtf(32),buf.readVarLong(),buf.readBoolean(),
                        buf.readNullable(RegistryFriendlyByteBuf::readUUID),buf.readUtf(64),buf.readVarInt(),buf.readBoolean()))));
        @Override public Type<State> type() { return TYPE; }
    }
}
