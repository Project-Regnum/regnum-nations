package dev.sakhno.regnum.nations.networking;

import dev.sakhno.regnum.nations.RegnumNations;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Additive atlas messages; the existing political map codecs stay unchanged. */
public final class AtlasPayloads {
    private AtlasPayloads() { }

    /** Stable opaque save identity; contains no terrain, seed or gameplay authority. */
    public record Session(UUID world) implements CustomPacketPayload {
        public static final Type<Session> TYPE = new Type<>(RegnumNations.id("atlas_session"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Session> CODEC = StreamCodec.of(
                (b,p) -> b.writeUUID(p.world()), b -> new Session(b.readUUID()));
        @Override public Type<Session> type() { return TYPE; }
    }

    public record CityMarker(UUID city, BlockPos pos, boolean capital) {
        public CityMarker { pos = pos.immutable(); }
    }

    public record Markers(int sequence, ResourceLocation dimension, List<CityMarker> cities) implements CustomPacketPayload {
        public Markers { cities = List.copyOf(cities); }
        public static final Type<Markers> TYPE = new Type<>(RegnumNations.id("atlas_markers"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Markers> CODEC = StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence()); b.writeResourceLocation(p.dimension());
            if (p.cities().size() > PoliticalMapPayloads.MAX_CELLS) throw new IllegalArgumentException("Too many atlas markers");
            b.writeVarInt(p.cities().size());
            for (CityMarker city : p.cities()) {
                b.writeUUID(city.city()); b.writeBlockPos(city.pos()); b.writeBoolean(city.capital());
            }
        }, b -> {
            int sequence = b.readInt(); ResourceLocation dimension = b.readResourceLocation();
            int count = b.readVarInt();
            if (count < 0 || count > PoliticalMapPayloads.MAX_CELLS) throw new IllegalArgumentException("Invalid atlas markers");
            var cities = new ArrayList<CityMarker>(count);
            for (int i=0; i<count; i++) cities.add(new CityMarker(b.readUUID(), b.readBlockPos(), b.readBoolean()));
            return new Markers(sequence, dimension, cities);
        });
        @Override public Type<Markers> type() { return TYPE; }
    }
}
