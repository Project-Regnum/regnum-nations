package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.ClaimService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class TerritoryBlockSupportMixin {
    // Destruction of wilderness support (by a player, blast, fire or piston) must not
    // indirectly drop a protected attachment. Property/connection updates remain vanilla.
    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
    private void nations$boundarySupport(Direction direction, BlockState neighbor, LevelAccessor level,
            BlockPos target, BlockPos source, CallbackInfoReturnable<BlockState> cir) {
        if (!cir.getReturnValue().isAir() || !(level instanceof ServerLevel server)) return;
        if ((target.getX() >> 4) == (source.getX() >> 4) && (target.getZ() >> 4) == (source.getZ() >> 4)) return;
        BlockState current = (BlockState)(Object)this;
        if (!current.isAir() && !ClaimService.canChangeTerrain(server, target) && !ClaimService.sameTerritory(server, source, target)) {
            cir.setReturnValue(current);
        }
    }
}
