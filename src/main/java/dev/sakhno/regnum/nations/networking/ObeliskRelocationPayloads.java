package dev.sakhno.regnum.nations.networking;

import dev.sakhno.regnum.nations.RegnumNations;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Separate additive channels keep all existing management codec layouts intact. */
public final class ObeliskRelocationPayloads {
    private ObeliskRelocationPayloads() { }
    public record Request(int sequence,UUID target,boolean state,ResourceLocation dimension,BlockPos destination,UUID token) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(RegnumNations.id("obelisk_relocate"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); b.writeUUID(p.target); b.writeBoolean(p.state); b.writeResourceLocation(p.dimension); b.writeBlockPos(p.destination);
            b.writeNullable(p.token,RegistryFriendlyByteBuf::writeUUID);
        },b -> new Request(b.readInt(),b.readUUID(),b.readBoolean(),b.readResourceLocation(),b.readBlockPos(),b.readNullable(RegistryFriendlyByteBuf::readUUID)));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Reply(int sequence,UUID token,String result,boolean success) implements CustomPacketPayload {
        public static final Type<Reply> TYPE=new Type<>(RegnumNations.id("obelisk_relocation"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Reply> CODEC=StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); b.writeNullable(p.token,RegistryFriendlyByteBuf::writeUUID); b.writeUtf(p.result,128); b.writeBoolean(p.success);
        },b -> new Reply(b.readInt(),b.readNullable(RegistryFriendlyByteBuf::readUUID),b.readUtf(128),b.readBoolean()));
        @Override public Type<Reply> type() { return TYPE; }
    }
}
