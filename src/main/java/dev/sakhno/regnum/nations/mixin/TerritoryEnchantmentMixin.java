package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.CityPermission;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.ReplaceDisk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ReplaceDisk.class)
public abstract class TerritoryEnchantmentMixin {
    @Redirect(method = "apply", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean nations$frostWalker(ServerLevel level, BlockPos target, BlockState state, ServerLevel original,
            int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position) {
        return (!TerritoryProtectionConfig.entities || TerritoryProtection.entityAllowed(level, entity, target, CityPermission.BUILD))
                && level.setBlockAndUpdate(target, state);
    }
}
