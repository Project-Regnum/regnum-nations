package dev.sakhno.reigns.kingdom;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class TerritoryProtection {
	private TerritoryProtection() { }

	public static void initialize() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> !denied(level, player, pos));
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
				denied(level, player, pos) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			// A verified capital is a public kingdom interface. It consumes normal block use;
			// sneaking/item placement still follows the existing protection checks.
			if (level instanceof ServerLevel serverLevel && !player.isShiftKeyDown()
					&& level.getBlockEntity(hit.getBlockPos()) instanceof dev.sakhno.reigns.block.KingdomObeliskBlockEntity obelisk) {
				var service=KingdomService.get(serverLevel.getServer());
				Settlement city=service.data().cityAt(level.dimension(),hit.getBlockPos());
				if (city!=null&&city.obeliskId().equals(obelisk.getObeliskId())
						&& (city.legacyAnchor()||obelisk instanceof dev.sakhno.reigns.block.CityObeliskBlockEntity)) return InteractionResult.PASS;
				Kingdom capital = KingdomService.get(serverLevel.getServer()).data().atCapital(level.dimension(), hit.getBlockPos());
				if (capital != null && capital.capitalObeliskId().equals(obelisk.getObeliskId())
						&& (obelisk.getKingdomId() == null || capital.id().equals(obelisk.getKingdomId()))) return InteractionResult.PASS;
			}
			// Check the clicked block and adjacent effect/placement location, including offhand/sneaking use.
			if (denied(level, player, hit.getBlockPos())
					|| denied(level, player, hit.getBlockPos().relative(hit.getDirection()))) return InteractionResult.FAIL;
			return InteractionResult.PASS;
		});
	}

	public static boolean denied(Level level, Player player, BlockPos pos) {
		if (!(level instanceof ServerLevel serverLevel)) return false;
		if (ClaimService.canBuild(serverLevel, player, pos)) return false;
		if (player != null) player.displayClientMessage(Component.translatable("message.project-reigns.territory.protected"), true);
		return true;
	}
}
