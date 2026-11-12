package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtectionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockEntity.class)
public abstract class TerritoryFallingBlockMixin {
    @Unique private String nations$originDimension;

    @Inject(method = "setStartPos", at = @At("TAIL"))
    private void nations$origin(BlockPos pos, CallbackInfo ci) {
        nations$originDimension = ((FallingBlockEntity)(Object)this).level().dimension().location().toString();
    }

    // Vanilla does not persist DATA_START_POS. Preserve attribution across chunk unload/reload;
    // old/unattributed falling entities may land only in wilderness.
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void nations$saveOrigin(CompoundTag tag, CallbackInfo ci) {
        if (nations$originDimension == null) return;
        CompoundTag origin = new CompoundTag();
        origin.putLong("Position", ((FallingBlockEntity)(Object)this).getStartPos().asLong());
        origin.putString("Dimension", nations$originDimension);
        tag.put("regnum-nations:fall_origin", origin);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void nations$loadOrigin(CompoundTag tag, CallbackInfo ci) {
        CompoundTag origin = tag.getCompound("regnum-nations:fall_origin");
        if (origin.contains("Position", 4) && origin.contains("Dimension", 8)) {
            ((FallingBlockEntity)(Object)this).setStartPos(BlockPos.of(origin.getLong("Position")));
            nations$originDimension = origin.getString("Dimension");
        } else nations$originDimension = null;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean nations$fallingPlacement(Level level, BlockPos pos, BlockState state, int flags) {
        FallingBlockEntity falling = (FallingBlockEntity)(Object)this;
        boolean permitted = TerritoryProtection.terrainAllowed(level, pos)
                || (level.dimension().location().toString().equals(nations$originDimension)
                    && TerritoryProtection.transferAllowed(level, falling.getStartPos(), pos));
        return (!TerritoryProtectionConfig.entities || permitted)
                && level.setBlock(pos, state, flags);
    }
}
