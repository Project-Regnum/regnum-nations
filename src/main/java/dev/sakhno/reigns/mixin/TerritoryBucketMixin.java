package dev.sakhno.reigns.mixin;

import dev.sakhno.reigns.kingdom.TerritoryProtection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
public abstract class TerritoryBucketMixin extends Item {
	protected TerritoryBucketMixin(Properties properties) { super(properties); }

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void reigns$protectBucket(Level level, Player player, InteractionHand hand,
			CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
		if (level.isClientSide) return;
		// Buckets use an air-use packet, bypassing UseBlockCallback. Cover pickup and pour ray casts.
		for (ClipContext.Fluid fluid : new ClipContext.Fluid[] {ClipContext.Fluid.SOURCE_ONLY, ClipContext.Fluid.NONE}) {
			var hit = getPlayerPOVHitResult(level, player, fluid);
			if (hit.getType() == HitResult.Type.BLOCK && (TerritoryProtection.denied(level, player, hit.getBlockPos())
					|| TerritoryProtection.denied(level, player, hit.getBlockPos().relative(hit.getDirection())))) {
				cir.setReturnValue(InteractionResultHolder.fail(player.getItemInHand(hand)));
				return;
			}
		}
	}
}
