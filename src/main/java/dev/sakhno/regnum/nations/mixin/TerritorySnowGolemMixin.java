package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SnowGolem.class)
public abstract class TerritorySnowGolemMixin {
    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean nations$snowTrail(Level level, BlockPos pos, BlockState state) {
        return (!TerritoryProtectionConfig.entities || TerritoryProtection.terrainAllowed(level, pos)) && level.setBlockAndUpdate(pos, state);
    }
}
