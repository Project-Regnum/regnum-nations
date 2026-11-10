package dev.sakhno.regnum.nations.kingdom;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** A separate cache identity; never changes the nations registry or its schema. */
public final class AtlasWorldIdentity extends SavedData {
    public static final String FILE_ID = "regnum-nations-atlas-world";
    static final Factory<AtlasWorldIdentity> FACTORY = new Factory<>(AtlasWorldIdentity::new, AtlasWorldIdentity::load, null);
    private final UUID id;

    private AtlasWorldIdentity() { this(UUID.randomUUID()); setDirty(); }
    private AtlasWorldIdentity(UUID id) { this.id = id; }
    static AtlasWorldIdentity load(CompoundTag tag, HolderLookup.Provider registries) {
        return tag.hasUUID("World") ? new AtlasWorldIdentity(tag.getUUID("World")) : new AtlasWorldIdentity();
    }
    public static UUID get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID).id;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putUUID("World", id);
        return tag;
    }
}
