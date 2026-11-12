package dev.sakhno.regnum.nations.networking;

import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.kingdom.KingdomRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Additive bounded channels; existing kingdom and city codecs remain unchanged. */
public final class KingdomAdministrationPayloads {
    public static final int PAGE_SIZE=6;
    private KingdomAdministrationPayloads() { }
    public enum Tab { GOVERNMENT, CITIES, AVAILABLE, INBOX, OUTGOING }
    public enum Action { VIEW, INVITE, ACCEPT, REJECT, CANCEL, LEAVE, REMOVE, MEMBER, OFFICER, CHANCELLOR, PREPARE_TRANSFER, TRANSFER }
    public record Entry(UUID id,String name,String detail,KingdomRole role,boolean online,List<Action> actions) {
        public Entry { actions=List.copyOf(actions); if (actions.size()>Action.values().length) throw new IllegalArgumentException("Unbounded actions"); }
    }
    public record Request(int sequence,Action action,Tab tab,String query,int page,UUID kingdom,UUID target,UUID token) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(RegnumNations.id("kingdom_admin_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); b.writeEnum(p.action); b.writeEnum(p.tab); b.writeUtf(p.query,64); b.writeVarInt(p.page);
            uuid(b,p.kingdom); uuid(b,p.target); uuid(b,p.token);
        },b -> new Request(b.readInt(),b.readEnum(Action.class),b.readEnum(Tab.class),b.readUtf(64),b.readVarInt(),uuid(b),uuid(b),uuid(b)));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record State(int sequence,UUID token,UUID kingdom,String name,KingdomRole role,int cities,int members,Tab tab,int page,int matches,
                        List<Entry> entries,UUID transferTarget,String result,boolean success) implements CustomPacketPayload {
        public State { entries=List.copyOf(entries); if (entries.size()>PAGE_SIZE) throw new IllegalArgumentException("Unbounded kingdom page"); }
        public static final Type<State> TYPE=new Type<>(RegnumNations.id("kingdom_admin_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); uuid(b,p.token); uuid(b,p.kingdom); b.writeUtf(p.name,64); b.writeNullable(p.role,(buf,v) -> buf.writeEnum(v));
            b.writeVarInt(p.cities); b.writeVarInt(p.members); b.writeEnum(p.tab); b.writeVarInt(p.page); b.writeVarInt(p.matches); b.writeVarInt(p.entries.size());
            for (Entry e:p.entries) {
                b.writeUUID(e.id); b.writeUtf(e.name,64); b.writeUtf(e.detail,128); b.writeNullable(e.role,(buf,v) -> buf.writeEnum(v)); b.writeBoolean(e.online);
                b.writeVarInt(e.actions.size()); for (Action action:e.actions) b.writeEnum(action);
            }
            uuid(b,p.transferTarget); b.writeUtf(p.result,128); b.writeBoolean(p.success);
        },b -> {
            int sequence=b.readInt(); UUID token=uuid(b),kingdom=uuid(b); String name=b.readUtf(64); KingdomRole role=b.readNullable(buf -> buf.readEnum(KingdomRole.class));
            int cities=b.readVarInt(),members=b.readVarInt(); Tab tab=b.readEnum(Tab.class); int page=b.readVarInt(),matches=b.readVarInt();
            int size=b.readVarInt(); if (size<0||size>PAGE_SIZE) throw new IllegalArgumentException("Unbounded kingdom page");
            List<Entry> entries=new ArrayList<>();
            for (int i=0;i<size;i++) {
                UUID id=b.readUUID(); String label=b.readUtf(64),detail=b.readUtf(128); KingdomRole memberRole=b.readNullable(buf -> buf.readEnum(KingdomRole.class)); boolean online=b.readBoolean();
                int count=b.readVarInt(); if (count<0||count>Action.values().length) throw new IllegalArgumentException("Unbounded actions");
                List<Action> actions=new ArrayList<>(); for (int j=0;j<count;j++) actions.add(b.readEnum(Action.class));
                entries.add(new Entry(id,label,detail,memberRole,online,actions));
            }
            return new State(sequence,token,kingdom,name,role,cities,members,tab,page,matches,entries,uuid(b),b.readUtf(128),b.readBoolean());
        });
        @Override public Type<State> type() { return TYPE; }
    }
    private static void uuid(RegistryFriendlyByteBuf b,UUID value) { b.writeNullable(value,RegistryFriendlyByteBuf::writeUUID); }
    private static UUID uuid(RegistryFriendlyByteBuf b) { return b.readNullable(RegistryFriendlyByteBuf::readUUID); }
}
