package dev.sakhno.reigns.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Dimension is part of identity; shifting correctly handles negative block coordinates. */
public record ClaimPos(ResourceKey<Level> dimension, int x, int z) {
	public static ClaimPos at(ResourceKey<Level> dimension, BlockPos pos) {
		return new ClaimPos(dimension, pos.getX() >> 4, pos.getZ() >> 4);
	}

	public ClaimPos offset(int dx, int dz) {
		return new ClaimPos(dimension, x + dx, z + dz);
	}
}
