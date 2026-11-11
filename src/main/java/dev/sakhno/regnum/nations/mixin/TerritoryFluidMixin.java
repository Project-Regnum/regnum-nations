package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowingFluid.class)
public abstract class TerritoryFluidMixin {
    // Before vanilla's shape cache: ownership must never become part of its state-only cache.
    // This gate covers spread, slope search and strength/source recalculation in both directions.
    @Inject(method = "canPassThroughWall", at = @At("HEAD"), cancellable = true)
    private void nations$fluidBoundary(Direction direction, BlockGetter level, BlockPos from, BlockState fromState,
            BlockPos to, BlockState toState, CallbackInfoReturnable<Boolean> cir) {
        if (TerritoryProtectionConfig.fluids && !TerritoryProtection.transferAllowed(level, from, to)) cir.setReturnValue(false);
    }
}
