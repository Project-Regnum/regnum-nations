package dev.sakhno.regnum.nations.networking;

import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.kingdom.CityRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Bounded additive channels. UUIDs identify players/invitations; labels and permission flags are display only. */
public final class CitizenManagementPayloads {
    public static final int PAGE_SIZE=6;
    private CitizenManagementPayloads() { }
    public enum Tab { MEMBERS, ONLINE, INBOX, OUTGOING }
    public enum Action { VIEW, INVITE, ACCEPT, DECLINE, PROMOTE, DEMOTE, KICK, LEAVE }
    public record Entry(UUID id,UUID player,String name,CityRole role,boolean online,UUID city,String cityName,int seconds) { }
    public record Request(int sequence,Action action,Tab tab,String query,int page,UUID city,UUID target,UUID token) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(RegnumNations.id("citizen_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); b.writeEnum(p.action); b.writeEnum(p.tab); b.writeUtf(p.query,64); b.writeVarInt(p.page);
            uuid(b,p.city); uuid(b,p.target); uuid(b,p.token);
        },b -> new Request(b.readInt(),b.readEnum(Action.class),b.readEnum(Tab.class),b.readUtf(64),b.readVarInt(),uuid(b),uuid(b),uuid(b)));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record State(int sequence,UUID token,UUID city,String cityName,int citizens,boolean invite,boolean manage,boolean leave,
                        Tab tab,int page,int matches,List<Entry> entries,String result,boolean success) implements CustomPacketPayload {
        public State { entries=List.copyOf(entries); if (entries.size()>PAGE_SIZE) throw new IllegalArgumentException("Unbounded citizen page"); }
        public static final Type<State> TYPE=new Type<>(RegnumNations.id("citizen_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); uuid(b,p.token); uuid(b,p.city); b.writeUtf(p.cityName,64); b.writeVarInt(p.citizens);
            b.writeBoolean(p.invite); b.writeBoolean(p.manage); b.writeBoolean(p.leave); b.writeEnum(p.tab); b.writeVarInt(p.page); b.writeVarInt(p.matches);
            b.writeVarInt(p.entries.size());
            for (Entry e:p.entries) {
                b.writeUUID(e.id); b.writeUUID(e.player); b.writeUtf(e.name,64); b.writeNullable(e.role,(buf,value) -> buf.writeEnum(value)); b.writeBoolean(e.online);
                uuid(b,e.city); b.writeUtf(e.cityName,64); b.writeVarInt(e.seconds);
            }
            b.writeUtf(p.result,128); b.writeBoolean(p.success);
        },b -> {
            int sequence=b.readInt(); UUID token=uuid(b),city=uuid(b); String name=b.readUtf(64); int citizens=b.readVarInt();
            boolean invite=b.readBoolean(),manage=b.readBoolean(),leave=b.readBoolean(); Tab tab=b.readEnum(Tab.class); int page=b.readVarInt(),matches=b.readVarInt();
            int size=b.readVarInt(); if (size<0||size>PAGE_SIZE) throw new IllegalArgumentException("Unbounded citizen page");
            List<Entry> entries=new ArrayList<>();
            for (int i=0;i<size;i++) entries.add(new Entry(b.readUUID(),b.readUUID(),b.readUtf(64),b.readNullable(buf -> buf.readEnum(CityRole.class)),b.readBoolean(),uuid(b),b.readUtf(64),b.readVarInt()));
            return new State(sequence,token,city,name,citizens,invite,manage,leave,tab,page,matches,entries,b.readUtf(128),b.readBoolean());
        });
        @Override public Type<State> type() { return TYPE; }
    }
    private static void uuid(RegistryFriendlyByteBuf b,UUID value) { b.writeNullable(value,RegistryFriendlyByteBuf::writeUUID); }
    private static UUID uuid(RegistryFriendlyByteBuf b) { return b.readNullable(RegistryFriendlyByteBuf::readUUID); }
}
