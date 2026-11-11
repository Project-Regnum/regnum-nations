package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.entity.monster.Silverfish$SilverfishMergeWithStoneGoal")
public abstract class TerritorySilverfishMixin {
    // Hide protected targets before vanilla can produce drops, consume items or discard a mob.
    @Redirect(method = "start", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/LevelAccessor;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState nations$terrainTarget(LevelAccessor level, BlockPos pos) {
        return TerritoryProtectionConfig.entities && level instanceof Level world && !TerritoryProtection.terrainAllowed(world, pos)
                ? Blocks.BEDROCK.defaultBlockState() : level.getBlockState(pos);
    }
}
