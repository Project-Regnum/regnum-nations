package dev.sakhno.regnum.nations.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PistonStructureResolver.class)
public abstract class TerritoryPistonResolverMixin {
    @Shadow @Final private BlockPos pistonPos;
    @Shadow @Final private Direction pistonDirection;
    @Shadow @Final private boolean extending;

    // Vanilla removes the head before resolving a pull. Model that removal without making
    // any writes, so protection can validate the complete operation before triggerEvent.
    @Redirect(method = {"resolve", "addBlockLine", "addBranchingBlocks"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState nations$retractionHead(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !extending && pos.equals(pistonPos.relative(pistonDirection)) && state.is(Blocks.PISTON_HEAD)
                ? Blocks.AIR.defaultBlockState() : state;
    }
}
