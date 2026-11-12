package dev.sakhno.regnum.nations.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {"net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal", "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal"})
public abstract class TerritoryEndermanMixin {
    @Inject(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", ordinal = 0), cancellable = true)
    private void nations$carryTransaction(CallbackInfo ci, @Local Level level, @Local(ordinal = 0) BlockPos target) {
        if (TerritoryProtectionConfig.entities && !TerritoryProtection.terrainAllowed(level, target)) ci.cancel();
    }
}
