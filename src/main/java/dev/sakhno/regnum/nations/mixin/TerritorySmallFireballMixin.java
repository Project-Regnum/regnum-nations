package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.CityPermission;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SmallFireball.class)
public abstract class TerritorySmallFireballMixin {
    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
    private void nations$fireballIgnition(BlockHitResult hit, CallbackInfo ci) {
        SmallFireball projectile = (SmallFireball)(Object)this;
        if (!TerritoryProtection.entityAllowed(projectile.level(), projectile, hit.getBlockPos().relative(hit.getDirection()), CityPermission.INTERACT)) ci.cancel();
    }
}
