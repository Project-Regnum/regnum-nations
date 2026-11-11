package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightningBolt.class)
public abstract class TerritoryLightningMixin {
    @Redirect(method = "spawnFire", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean nations$lightningFire(Level level, BlockPos pos, BlockState state) {
        return (!TerritoryProtectionConfig.fire || TerritoryProtection.terrainAllowed(level, pos)) && level.setBlockAndUpdate(pos, state);
    }
}
