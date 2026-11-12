package dev.sakhno.regnum.nations.mixin;

import dev.sakhno.regnum.nations.kingdom.TerritoryProtection;
import dev.sakhno.regnum.nations.kingdom.TerritoryProtectionConfig;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SpongeBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SpongeBlock.class)
public abstract class TerritorySpongeMixin {
    @Redirect(method = "removeWaterBreadthFirstSearch", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/core/BlockPos;breadthFirstTraversal(Lnet/minecraft/core/BlockPos;IILjava/util/function/BiConsumer;Ljava/util/function/Predicate;)I"))
    private int nations$absorptionBoundary(BlockPos start, int depth, int count,
            BiConsumer<BlockPos, Consumer<BlockPos>> neighbors, Predicate<BlockPos> absorb, Level level, BlockPos sponge) {
        return BlockPos.breadthFirstTraversal(start, depth, count, neighbors, target ->
                (!TerritoryProtectionConfig.fluids || TerritoryProtection.transferAllowed(level, sponge, target)) && absorb.test(target));
    }
}
