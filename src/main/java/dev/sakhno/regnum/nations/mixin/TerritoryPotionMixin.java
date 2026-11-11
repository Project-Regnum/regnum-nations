package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.CityPermission;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.ThrownPotion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownPotion.class)
public abstract class TerritoryPotionMixin {
    @Inject(method = "dowseFire", at = @At("HEAD"), cancellable = true)
    private void nations$extinguishing(BlockPos pos, CallbackInfo ci) {
        ThrownPotion potion = (ThrownPotion)(Object)this;
        if (!TerritoryProtection.entityAllowed(potion.level(), potion, pos, CityPermission.INTERACT)) ci.cancel();
    }
}
