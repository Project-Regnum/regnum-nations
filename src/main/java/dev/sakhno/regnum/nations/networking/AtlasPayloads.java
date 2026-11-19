package dev.sakhno.regnum.nations.networking;

import dev.sakhno.regnum.nations.RegnumNations;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
    public record Dungeons(ResourceLocation dimension, List<CompoundTag> entries) implements CustomPacketPayload {
        public Dungeons { entries=List.copyOf(entries); }
        public static final Type<Dungeons> TYPE=new Type<>(RegnumNations.id("atlas_dungeons"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Dungeons> CODEC=StreamCodec.of((b,p)->{
            b.writeResourceLocation(p.dimension()); b.writeVarInt(p.entries().size());
            for(var entry:p.entries()) b.writeNbt(entry);
        },b->{
            ResourceLocation dim=b.readResourceLocation();int n=b.readVarInt();
            if(n<0||n>128)throw new IllegalArgumentException("Too many dungeons");
            var entries=new ArrayList<CompoundTag>();
            for(int j=0;j<n;j++) {var entry=b.readNbt();if(entry==null)throw new IllegalArgumentException("Missing dungeon");entries.add(entry);}
            return new Dungeons(dim,entries);
        });
        @Override public Type<Dungeons> type(){return TYPE;}
    }
    public record DungeonMarker(String type, String name, int tier, BlockPos entrance, int level, boolean defeated) {
        private static final int MAX_TYPE_LENGTH = 64;
        private static final int MAX_NAME_LENGTH = 128;

        public DungeonMarker {
            type = type.substring(0, Math.min(type.length(), MAX_TYPE_LENGTH));
            if (name.length() > MAX_NAME_LENGTH) {
                int end = Character.isHighSurrogate(name.charAt(MAX_NAME_LENGTH - 1)) ? MAX_NAME_LENGTH - 1 : MAX_NAME_LENGTH;
                name = name.substring(0, end);
            }
        }

        public static DungeonMarker fromTag(CompoundTag tag) {
            int[] entrances = tag.getIntArray("Entrances");
            var entrance = entrances.length >= 3
                    ? new BlockPos(entrances[0], entrances[1], entrances[2])
                    : new BlockPos(tag.getInt("X") + 24, tag.getInt("Y"), tag.getInt("Z"));
            return new DungeonMarker(tag.getString("Type"), tag.getString("Name"), tag.getInt("Tier"),
                    entrance, tag.getInt("Level"), tag.getBoolean("Defeated"));
        }
    }

    /** A sequenced dimension snapshot, split into bounded packets. */
    public record DungeonMarkers(int sequence, ResourceLocation dimension, boolean last, List<DungeonMarker> entries)
            implements CustomPacketPayload {
        public static final int BATCH_SIZE = 128;
        public DungeonMarkers { entries = List.copyOf(entries); }
        public static final Type<DungeonMarkers> TYPE = new Type<>(RegnumNations.id("atlas_dungeon_markers"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DungeonMarkers> CODEC = StreamCodec.of((b, p) -> {
            b.writeInt(p.sequence());
            b.writeResourceLocation(p.dimension());
            b.writeBoolean(p.last());
            if (p.entries().size() > BATCH_SIZE) throw new IllegalArgumentException("Too many dungeon markers in a batch");
            b.writeVarInt(p.entries().size());
            for (var marker : p.entries()) {
                b.writeUtf(marker.type(), DungeonMarker.MAX_TYPE_LENGTH);
                b.writeUtf(marker.name(), DungeonMarker.MAX_NAME_LENGTH);
                b.writeVarInt(marker.tier());
                b.writeBlockPos(marker.entrance());
                b.writeVarInt(marker.level());
                b.writeBoolean(marker.defeated());
            }
        }, b -> {
            int sequence = b.readInt();
            ResourceLocation dimension = b.readResourceLocation();
            boolean last = b.readBoolean();
            int count = b.readVarInt();
            if (count < 0 || count > BATCH_SIZE) throw new IllegalArgumentException("Invalid dungeon marker count");
            var entries = new ArrayList<DungeonMarker>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new DungeonMarker(b.readUtf(DungeonMarker.MAX_TYPE_LENGTH), b.readUtf(DungeonMarker.MAX_NAME_LENGTH), b.readVarInt(),
                        b.readBlockPos(), b.readVarInt(), b.readBoolean()));
            }
            return new DungeonMarkers(sequence, dimension, last, entries);
        });
        @Override public Type<DungeonMarkers> type() { return TYPE; }
    }

    public record DungeonChange(ResourceLocation dimension) implements CustomPacketPayload {
        public static final Type<DungeonChange> TYPE = new Type<>(RegnumNations.id("atlas_dungeon_change"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DungeonChange> CODEC = StreamCodec.of(
                (b, p) -> b.writeResourceLocation(p.dimension()), b -> new DungeonChange(b.readResourceLocation()));
        @Override public Type<DungeonChange> type() { return TYPE; }
    }

}
