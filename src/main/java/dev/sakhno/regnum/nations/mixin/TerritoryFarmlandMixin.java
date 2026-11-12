package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.CityPermission;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FarmBlock.class)
public abstract class TerritoryFarmlandMixin {
    @Inject(method = "turnToDirt", at = @At("HEAD"), cancellable = true)
    private static void nations$trampling(Entity entity, BlockState state, Level level, BlockPos pos, CallbackInfo ci) {
        if (entity != null && TerritoryProtectionConfig.entities
                && !TerritoryProtection.entityAllowed(level, entity, pos, CityPermission.BUILD)) ci.cancel();
    }
}
