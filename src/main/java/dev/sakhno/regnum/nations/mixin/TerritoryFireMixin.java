package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FireBlock.class)
public abstract class TerritoryFireMixin {
    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void nations$preventBurning(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
        if (TerritoryProtectionConfig.fire && !TerritoryProtection.terrainAllowed(level, pos)) ci.cancel();
    }

    @Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private void nations$preventSpread(LevelReader level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (TerritoryProtectionConfig.fire && level instanceof Level world && !TerritoryProtection.terrainAllowed(world, pos)) cir.setReturnValue(0);
    }
}
