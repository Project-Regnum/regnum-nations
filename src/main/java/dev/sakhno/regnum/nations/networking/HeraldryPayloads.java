package dev.sakhno.regnum.nations.networking;

import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.kingdom.Heraldry;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Additive channels. No arbitrary NBT, URLs, file paths or user-owned registry identifiers. */
public final class HeraldryPayloads {
    public static final int BATCH_SIZE=32;
    private HeraldryPayloads() { }
    public enum Action { VIEW, SAVE, SEAL }
    public record Request(int sequence,Action action,UUID kingdom,UUID token,Heraldry arms) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(RegnumNations.id("heraldry_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); b.writeEnum(p.action); uuid(b,p.kingdom); uuid(b,p.token);
            b.writeNullable(p.arms,(buf,v) -> v.write(buf));
        },b -> new Request(b.readInt(),b.readEnum(Action.class),uuid(b),uuid(b),b.readNullable(Heraldry::read)));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record State(int sequence,UUID kingdom,String name,Heraldry arms,boolean editable,UUID token,String result,boolean success) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(RegnumNations.id("heraldry_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); uuid(b,p.kingdom); b.writeUtf(p.name,64); b.writeNullable(p.arms,(buf,v) -> v.write(buf));
            b.writeBoolean(p.editable); uuid(b,p.token); b.writeUtf(p.result,128); b.writeBoolean(p.success);
        },b -> new State(b.readInt(),uuid(b),b.readUtf(64),b.readNullable(Heraldry::read),b.readBoolean(),uuid(b),b.readUtf(128),b.readBoolean()));
        @Override public Type<State> type() { return TYPE; }
    }
    public record Entry(UUID kingdom,Heraldry arms) { }
    public record Sync(boolean reset,List<Entry> entries) implements CustomPacketPayload {
        public Sync { entries=List.copyOf(entries); if (entries.size()>BATCH_SIZE) throw new IllegalArgumentException("Heraldry batch too large"); }
        public static final Type<Sync> TYPE=new Type<>(RegnumNations.id("heraldry_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Sync> CODEC=StreamCodec.of((b,p) -> {
            b.writeBoolean(p.reset); b.writeByte(p.entries.size());
            for (var e:p.entries) { b.writeUUID(e.kingdom); b.writeNullable(e.arms,(buf,v) -> v.write(buf)); }
        },b -> {
            boolean reset=b.readBoolean(); int count=b.readUnsignedByte();
            if (count>BATCH_SIZE) throw new IllegalArgumentException("Heraldry batch too large");
            var entries=new ArrayList<Entry>();
            for (int i=0;i<count;i++) entries.add(new Entry(b.readUUID(),b.readNullable(Heraldry::read)));
            return new Sync(reset,entries);
        });
        @Override public Type<Sync> type() { return TYPE; }
    }
    private static void uuid(RegistryFriendlyByteBuf b,UUID id) { b.writeNullable(id,RegistryFriendlyByteBuf::writeUUID); }
    private static UUID uuid(RegistryFriendlyByteBuf b) { return b.readNullable(RegistryFriendlyByteBuf::readUUID); }
}
