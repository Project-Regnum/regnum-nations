// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Dara Adedeji. Adapted from Diorama v0.2.1; see THIRD_PARTY_NOTICES.md.
package dev.sakhno.regnum.nations.client.mixin;

import dev.sakhno.regnum.nations.client.terrain.diorama.MapSession;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tells the map when a block changes in a client-side chunk. */
@Mixin(LevelChunk.class)
public abstract class AtlasLevelChunkMixin {
	@Shadow
	@Final
	private Level level;

	@Inject(method = "setBlockState", at = @At("RETURN"))
	// The third parameter was a boolean, isMoving.
	private void atlas$onSetBlockState(BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
		BlockState old = cir.getReturnValue();
		if (old != null && this.level instanceof ClientLevel clientLevel) {
			MapSession.blockChanged(clientLevel, pos, old, state);
		}
	}
}
