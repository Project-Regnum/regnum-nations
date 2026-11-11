package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = {"net.minecraft.world.entity.ai.goal.EatBlockGoal", "net.minecraft.world.entity.animal.Rabbit$RaidGardenGoal", "net.minecraft.world.entity.monster.Silverfish$SilverfishWakeUpFriendsGoal"})
public abstract class TerritoryMobGoalMixin {
    // Hide protected targets before vanilla can produce drops, consume items or discard a mob.
    @Redirect(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState nations$terrainTarget(Level level, BlockPos pos) {
        return TerritoryProtectionConfig.entities && !TerritoryProtection.terrainAllowed(level, pos)
                ? Blocks.BEDROCK.defaultBlockState() : level.getBlockState(pos);
    }
}
