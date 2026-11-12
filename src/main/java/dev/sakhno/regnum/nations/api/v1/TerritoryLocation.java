package dev.sakhno.regnum.nations.api.v1;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Chunk ownership includes dimension and uses arithmetic shifts for negative block coordinates. */
public record TerritoryLocation(ResourceLocation dimension, int chunkX, int chunkZ) {
    public TerritoryLocation { Objects.requireNonNull(dimension); }
}
