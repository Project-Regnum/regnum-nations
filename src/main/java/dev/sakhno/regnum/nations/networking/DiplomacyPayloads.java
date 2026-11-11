package dev.sakhno.regnum.nations.networking;

import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.kingdom.DiplomacyData.Relation;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Additive bounded channels; the client never supplies its authority or a resulting relation. */
public final class DiplomacyPayloads {
    public static final int PAGE_SIZE=6;
    private DiplomacyPayloads() { }
    public enum Tab { KINGDOMS, INCOMING, OUTGOING }
    public enum Action { VIEW, INVITE, ACCEPT, REJECT, CANCEL, TERMINATE, HOSTILE, NEUTRAL }
    public record Entry(UUID kingdom,String name,Relation relation,UUID revision,UUID invitation,boolean incoming,long expiresAt,boolean ownHostility,boolean theirHostility) { }
    public record Request(int sequence,Action action,Tab tab,int page,UUID kingdom,UUID target,UUID revision,UUID invitation) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(RegnumNations.id("diplomacy_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); b.writeEnum(p.action); b.writeEnum(p.tab); b.writeVarInt(p.page);
            uuid(b,p.kingdom); uuid(b,p.target); uuid(b,p.revision); uuid(b,p.invitation);
        },b -> new Request(b.readInt(),b.readEnum(Action.class),b.readEnum(Tab.class),b.readVarInt(),uuid(b),uuid(b),uuid(b),uuid(b)));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record State(int sequence,UUID kingdom,String name,boolean manage,Tab tab,int page,int matches,List<Entry> entries,String result,boolean success) implements CustomPacketPayload {
        public State { entries=List.copyOf(entries); if (entries.size()>PAGE_SIZE) throw new IllegalArgumentException("Unbounded diplomacy page"); }
        public static final Type<State> TYPE=new Type<>(RegnumNations.id("diplomacy_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); uuid(b,p.kingdom); b.writeUtf(p.name,64); b.writeBoolean(p.manage); b.writeEnum(p.tab); b.writeVarInt(p.page); b.writeVarInt(p.matches); b.writeVarInt(p.entries.size());
            for (Entry e:p.entries) {
                b.writeUUID(e.kingdom); b.writeUtf(e.name,64); b.writeEnum(e.relation); uuid(b,e.revision); uuid(b,e.invitation);
                b.writeBoolean(e.incoming); b.writeLong(e.expiresAt); b.writeBoolean(e.ownHostility); b.writeBoolean(e.theirHostility);
            }
            b.writeUtf(p.result,128); b.writeBoolean(p.success);
        },b -> {
            int sequence=b.readInt(); UUID kingdom=uuid(b); String name=b.readUtf(64); boolean manage=b.readBoolean(); Tab tab=b.readEnum(Tab.class); int page=b.readVarInt(),matches=b.readVarInt(),size=b.readVarInt();
            if (size<0||size>PAGE_SIZE) throw new IllegalArgumentException("Unbounded diplomacy page");
            List<Entry> entries=new ArrayList<>();
            for (int i=0;i<size;i++) entries.add(new Entry(b.readUUID(),b.readUtf(64),b.readEnum(Relation.class),uuid(b),uuid(b),b.readBoolean(),b.readLong(),b.readBoolean(),b.readBoolean()));
            return new State(sequence,kingdom,name,manage,tab,page,matches,entries,b.readUtf(128),b.readBoolean());
        });
        @Override public Type<State> type() { return TYPE; }
    }
    private static void uuid(RegistryFriendlyByteBuf b,UUID id) { b.writeNullable(id,RegistryFriendlyByteBuf::writeUUID); }
    private static UUID uuid(RegistryFriendlyByteBuf b) { return b.readNullable(RegistryFriendlyByteBuf::readUUID); }
}
