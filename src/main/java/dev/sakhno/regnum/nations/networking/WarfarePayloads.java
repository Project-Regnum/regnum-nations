package dev.sakhno.regnum.nations.networking;

import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.kingdom.KingdomPermission;
import dev.sakhno.regnum.nations.kingdom.WarfareData.*;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Bounded snapshots. Requests contain references and concurrency stamps, never capture values. */
public final class WarfarePayloads {
    public static final int PAGE_SIZE=6;
    private WarfarePayloads() { }
    public enum Tab { WARS, CITIES, OBJECTIVES, SIEGES, HISTORY }
    public enum Action { VIEW, CAPTURE_OBJECTIVE, DEFEND_OBJECTIVE, PEACE_OBJECTIVE, DECLARE_SIEGE, WITHDRAW, CANCEL_OBJECTIVE }
    public record Entry(UUID id, UUID revision, UUID war, UUID city, UUID attacker, UUID defender,
            String cityName, String attackerName, String defenderName, Objective objective, State state,
            long startedAt, long readySeconds, long remainingSeconds, long completedAt, int progress, int captureSeconds,
            int defenseProgress, int defenseSeconds, String obelisk, int attackers, int defenders, String participants, boolean pendingConquest) { }
    public record Request(int sequence,Action action,Tab tab,int page,UUID kingdom,UUID city,UUID war,UUID objective,UUID revision) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(RegnumNations.id("warfare_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); b.writeEnum(p.action); b.writeEnum(p.tab); b.writeVarInt(p.page);
            uuid(b,p.kingdom); uuid(b,p.city); uuid(b,p.war); uuid(b,p.objective); uuid(b,p.revision);
        },b -> new Request(b.readInt(),b.readEnum(Action.class),b.readEnum(Tab.class),b.readVarInt(),uuid(b),uuid(b),uuid(b),uuid(b),uuid(b)));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Snapshot(int sequence,UUID kingdom,Tab tab,int page,int matches,List<Entry> entries,
            Set<KingdomPermission> permissions,String result,boolean success) implements CustomPacketPayload {
        public Snapshot { entries=List.copyOf(entries); permissions=Set.copyOf(permissions); if (entries.size()>PAGE_SIZE) throw new IllegalArgumentException("Unbounded warfare page"); }
        public static final Type<Snapshot> TYPE=new Type<>(RegnumNations.id("warfare_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); uuid(b,p.kingdom); b.writeEnum(p.tab); b.writeVarInt(p.page); b.writeVarInt(p.matches); b.writeVarInt(p.entries.size());
            for (Entry e:p.entries) {
                uuid(b,e.id); uuid(b,e.revision); b.writeUUID(e.war); uuid(b,e.city); b.writeUUID(e.attacker); b.writeUUID(e.defender);
                b.writeUtf(e.cityName,64); b.writeUtf(e.attackerName,64); b.writeUtf(e.defenderName,64);
                b.writeNullable(e.objective,(buf,v) -> buf.writeEnum(v)); b.writeNullable(e.state,(buf,v) -> buf.writeEnum(v));
                b.writeLong(e.startedAt); b.writeLong(e.readySeconds); b.writeLong(e.remainingSeconds); b.writeLong(e.completedAt);
                b.writeVarInt(e.progress); b.writeVarInt(e.captureSeconds); b.writeVarInt(e.defenseProgress); b.writeVarInt(e.defenseSeconds);
                b.writeUtf(e.obelisk,32); b.writeVarInt(e.attackers); b.writeVarInt(e.defenders); b.writeUtf(e.participants,1024); b.writeBoolean(e.pendingConquest);
            }
            b.writeVarInt(p.permissions.size()); for (var permission:p.permissions) b.writeEnum(permission);
            b.writeUtf(p.result,128); b.writeBoolean(p.success);
        },b -> {
            int sequence=b.readInt(); UUID kingdom=uuid(b); Tab tab=b.readEnum(Tab.class); int page=b.readVarInt(),matches=b.readVarInt(),size=b.readVarInt();
            if (size<0||size>PAGE_SIZE) throw new IllegalArgumentException("Unbounded warfare page");
            List<Entry> entries=new ArrayList<>();
            for (int i=0;i<size;i++) entries.add(new Entry(uuid(b),uuid(b),b.readUUID(),uuid(b),b.readUUID(),b.readUUID(),b.readUtf(64),b.readUtf(64),b.readUtf(64),
                    b.readNullable(buf -> buf.readEnum(Objective.class)),b.readNullable(buf -> buf.readEnum(State.class)),b.readLong(),b.readLong(),b.readLong(),b.readLong(),
                    b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(32),b.readVarInt(),b.readVarInt(),b.readUtf(1024),b.readBoolean()));
            int count=b.readVarInt(); if (count<0||count>KingdomPermission.values().length) throw new IllegalArgumentException("Unbounded permissions");
            Set<KingdomPermission> permissions=EnumSet.noneOf(KingdomPermission.class); for (int i=0;i<count;i++) permissions.add(b.readEnum(KingdomPermission.class));
            return new Snapshot(sequence,kingdom,tab,page,matches,entries,permissions,b.readUtf(128),b.readBoolean());
        });
        @Override public Type<Snapshot> type() { return TYPE; }
    }
    private static void uuid(RegistryFriendlyByteBuf b,UUID id) { b.writeNullable(id,RegistryFriendlyByteBuf::writeUUID); }
    private static UUID uuid(RegistryFriendlyByteBuf b) { return b.readNullable(RegistryFriendlyByteBuf::readUUID); }
}
