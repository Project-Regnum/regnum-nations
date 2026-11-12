package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.CityPermission;
import dev.sakhno.regnum.nations.kingdom.DispenserProtection;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class TerritoryLevelMixin {
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
    private void nations$entityDestruction(BlockPos pos, boolean drops, Entity entity, int recursion, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level)(Object)this;
        if (!DispenserProtection.allows(level, pos) || (entity != null && TerritoryProtectionConfig.entities
                && !TerritoryProtection.entityAllowed(level, entity, pos, CityPermission.BUILD))) cir.setReturnValue(false);
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void nations$secondaryDispenserWrites(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
        if (!DispenserProtection.allows((Level)(Object)this, pos)) cir.setReturnValue(false);
    }
}
