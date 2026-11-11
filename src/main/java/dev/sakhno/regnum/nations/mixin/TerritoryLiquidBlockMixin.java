package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LiquidBlock.class)
public abstract class TerritoryLiquidBlockMixin {
    @Redirect(method = "shouldSpreadLiquid", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"))
    private FluidState nations$preventBoundaryCooling(Level level, BlockPos neighbor, Level original, BlockPos lava, BlockState state) {
        return TerritoryProtectionConfig.fluids && !TerritoryProtection.transferAllowed(level, neighbor, lava)
                ? Fluids.EMPTY.defaultFluidState() : level.getFluidState(neighbor);
    }

    @Redirect(method = "shouldSpreadLiquid", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState nations$preventBoundaryBasalt(Level level, BlockPos neighbor, Level original, BlockPos lava, BlockState state) {
        return TerritoryProtectionConfig.fluids && !TerritoryProtection.transferAllowed(level, neighbor, lava)
                ? Blocks.AIR.defaultBlockState() : level.getBlockState(neighbor);
    }
}
