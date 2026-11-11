package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.ClaimService;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtectionConfig;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public abstract class TerritoryExplosionMixin {
    @Shadow @Final private Level level;
    @Shadow public abstract List<BlockPos> getToBlow();

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void nations$filterBlocksAndFire(boolean particles, CallbackInfo ci) {
        if (TerritoryProtectionConfig.explosions && level instanceof ServerLevel server) {
            ClaimService.filterExplosion(server, getToBlow());
        }
    }
}
