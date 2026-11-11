package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.CityPermission;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ButtonBlock.class)
public abstract class TerritoryButtonMixin {
    // A lingering arrow is checked again on entity collision and scheduled ticks, after onHitBlock.
    @Redirect(method = "checkPressed", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private <T extends Entity> List<T> nations$authorizedArrows(Level level, Class<T> type, AABB bounds,
            BlockState state, Level original, BlockPos button) {
        return level.getEntitiesOfClass(type, bounds, entity -> TerritoryProtection.entityAllowed(level, entity, button, CityPermission.INTERACT));
    }
}
