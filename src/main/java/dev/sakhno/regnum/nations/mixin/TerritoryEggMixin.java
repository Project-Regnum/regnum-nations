package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.CityPermission;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TurtleEggBlock.class)
public abstract class TerritoryEggMixin {
    @Inject(method = "destroyEgg", at = @At("HEAD"), cancellable = true)
    private void nations$trampling(Level level, BlockState state, BlockPos pos, Entity entity, int chance, CallbackInfo ci) {
        if (TerritoryProtectionConfig.entities && !TerritoryProtection.entityAllowed(level, entity, pos, CityPermission.BUILD)) ci.cancel();
    }
}
