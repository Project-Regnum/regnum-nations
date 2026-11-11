package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EnderDragon.class)
public abstract class TerritoryDragonMixin {
    @Redirect(method = "checkWalls", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean nations$dragonTerrain(Level level, BlockPos pos, boolean moving) {
        return (!TerritoryProtectionConfig.entities || TerritoryProtection.terrainAllowed(level, pos)) && level.removeBlock(pos, moving);
    }
}
