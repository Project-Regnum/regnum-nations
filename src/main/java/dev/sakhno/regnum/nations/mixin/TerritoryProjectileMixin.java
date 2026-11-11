package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.CityPermission;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
public abstract class TerritoryProjectileMixin {
    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true)
    private void nations$blockImpact(BlockHitResult hit, CallbackInfo ci) {
        Projectile projectile = (Projectile)(Object)this;
        if (!TerritoryProtection.entityAllowed(projectile.level(), projectile, hit.getBlockPos(), CityPermission.INTERACT)) ci.cancel();
    }

    @Inject(method = "mayInteract", at = @At("HEAD"), cancellable = true)
    private void nations$permission(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!TerritoryProtection.entityAllowed(level, (Projectile)(Object)this, pos, CityPermission.INTERACT)) cir.setReturnValue(false);
    }
}
