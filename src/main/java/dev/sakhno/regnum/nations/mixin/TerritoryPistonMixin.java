package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
public abstract class TerritoryPistonMixin {
    @Shadow @Final private boolean isSticky;

    @Inject(method = "triggerEvent", at = @At("HEAD"), cancellable = true)
    private void nations$atomicMovement(BlockState state, Level level, BlockPos pos, int type, int data,
            CallbackInfoReturnable<Boolean> cir) {
        if (type >= 0 && type <= 2 && !TerritoryProtection.pistonAllowed(level, pos,
                state.getValue(PistonBaseBlock.FACING), type == 0, isSticky)) cir.setReturnValue(false);
    }

}
