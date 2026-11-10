package dev.sakhno.reigns.networking;

import dev.sakhno.reigns.ProjectReigns;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded wire DTOs only; ownership is always supplied by the server. */
public final class PoliticalMapPayloads {
    public static final int MAX_CELLS = 4096;
    public static final int MAX_CELL_BYTES=900_000;
    public static int cellBytes(Cell cell) { return 64+cell.name().getBytes(java.nio.charset.StandardCharsets.UTF_8).length+cell.politicalName().getBytes(java.nio.charset.StandardCharsets.UTF_8).length; }
    public record Cell(int x, int z, UUID owner, String name, int color, UUID sovereign, String politicalName) {}
    public record Request(int sequence) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ProjectReigns.id("map_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (b, p) -> b.writeInt(p.sequence), b -> new Request(b.readInt()));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record Snapshot(int sequence, ResourceLocation dimension, int centerX, int centerZ,
                           int radius, int status, List<Cell> cells) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ProjectReigns.id("map_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of((b, p) -> {
            b.writeInt(p.sequence); b.writeResourceLocation(p.dimension);
            b.writeInt(p.centerX); b.writeInt(p.centerZ); b.writeVarInt(p.radius); b.writeVarInt(p.status);
            if (p.cells.size() > MAX_CELLS) throw new IllegalArgumentException("Too many map cells");
            b.writeVarInt(p.cells.size());
            int bytes=0;
            for (Cell c : p.cells) {
                bytes+=cellBytes(c); if (bytes>MAX_CELL_BYTES) throw new IllegalArgumentException("Map byte budget exceeded");
                b.writeInt(c.x); b.writeInt(c.z); b.writeUUID(c.owner); b.writeUtf(c.name, 64); b.writeInt(c.color); b.writeNullable(c.sovereign,RegistryFriendlyByteBuf::writeUUID); b.writeUtf(c.politicalName,64);
            }
        }, b -> {
            int sequence = b.readInt(); ResourceLocation dimension = b.readResourceLocation();
            int x = b.readInt(), z = b.readInt(), radius = b.readVarInt(), status = b.readVarInt();
            int count = b.readVarInt();
            if (count < 0 || count > MAX_CELLS) throw new IllegalArgumentException("Invalid map size");
            var cells = new java.util.ArrayList<Cell>(count);
            int bytes=0;
            for (int i=0;i<count;i++) {
                Cell cell=new Cell(b.readInt(),b.readInt(),b.readUUID(),b.readUtf(64),b.readInt(),b.readNullable(RegistryFriendlyByteBuf::readUUID),b.readUtf(64));
                bytes+=cellBytes(cell); if (bytes>MAX_CELL_BYTES) throw new IllegalArgumentException("Map byte budget exceeded"); cells.add(cell);
            }
            return new Snapshot(sequence, dimension, x, z, radius, status, List.copyOf(cells));
        });
        @Override public Type<Snapshot> type() { return TYPE; }
    }
}
