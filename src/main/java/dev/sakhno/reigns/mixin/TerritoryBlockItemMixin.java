package dev.sakhno.reigns.mixin;

import dev.sakhno.reigns.kingdom.TerritoryProtection;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BedBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockItem.class)
public abstract class TerritoryBlockItemMixin {
	@Redirect(method = "place", at = @At(value = "INVOKE", target =
			"Lnet/minecraft/world/item/BlockItem;updatePlacementContext(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/item/context/BlockPlaceContext;"))
	private BlockPlaceContext reigns$protectPlacement(BlockItem item, BlockPlaceContext original) {
		// Validate the resolved target, including scaffolding extension, before any placement writes.
		BlockPlaceContext context = item.updatePlacementContext(original);
		if (context == null) return null;
		if (TerritoryProtection.denied(context.getLevel(), context.getPlayer(), context.getClickedPos())
				|| (item.getBlock() instanceof BedBlock && TerritoryProtection.denied(context.getLevel(),
				context.getPlayer(), context.getClickedPos().relative(context.getHorizontalDirection())))) return null;
		return context;
	}
}
