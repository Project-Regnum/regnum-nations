// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Dara Adedeji. Adapted from Diorama v0.2.1; see THIRD_PARTY_NOTICES.md.
package dev.sakhno.regnum.nations.client.mixin;

import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The hashed world seed a server sends with each level, which tells its worlds apart. */
@Mixin(BiomeManager.class)
public interface AtlasBiomeManagerAccessor {
	@Accessor("biomeZoomSeed")
	long atlas$biomeZoomSeed();
}
