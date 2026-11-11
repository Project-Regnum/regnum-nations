package dev.sakhno.regnum.nations.kingdom;

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
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> !anchorDenied(level,player,pos) && !denied(level, player, pos));
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
				(anchorDenied(level,player,pos)||denied(level, player, pos)) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			// A verified capital is a public kingdom interface. It consumes normal block use;
			// sneaking/item placement still follows the existing protection checks.
			if (level instanceof ServerLevel serverLevel && !player.isShiftKeyDown()
					&& level.getBlockEntity(hit.getBlockPos()) instanceof dev.sakhno.regnum.nations.block.KingdomObeliskBlockEntity obelisk) {
				var service=KingdomService.get(serverLevel.getServer());
				Settlement city=service.data().cityAt(level.dimension(),hit.getBlockPos());
				if (city!=null&&city.obeliskId().equals(obelisk.getObeliskId())
						&& (city.legacyAnchor()||obelisk instanceof dev.sakhno.regnum.nations.block.CityObeliskBlockEntity)) return InteractionResult.PASS;
				Kingdom capital = KingdomService.get(serverLevel.getServer()).data().atCapital(level.dimension(), hit.getBlockPos());
				if (capital != null && capital.capitalObeliskId().equals(obelisk.getObeliskId())
						&& (obelisk.getKingdomId() == null || capital.id().equals(obelisk.getKingdomId()))) return InteractionResult.PASS;
			}
			// Check the clicked block and adjacent effect/placement location, including offhand/sneaking use.
			if (denied(level, player, hit.getBlockPos(), CityPermission.INTERACT)
					|| denied(level, player, hit.getBlockPos().relative(hit.getDirection()), CityPermission.INTERACT)) return InteractionResult.FAIL;
			return InteractionResult.PASS;
		});
	}

    private static boolean anchorDenied(Level level,Player player,BlockPos pos) {
        if (level instanceof ServerLevel serverLevel&&ObeliskRelocationService.protectedAnchor(serverLevel,pos)) {
            player.displayClientMessage(Component.translatable("relocation.regnum-nations.protected"),true); return true;
        }
        return false;
    }

	public static boolean denied(Level level, Player player, BlockPos pos) {
		return denied(level, player, pos, CityPermission.BUILD);
	}

	private static boolean denied(Level level, Player player, BlockPos pos, CityPermission permission) {
		if (!(level instanceof ServerLevel serverLevel)) return false;
		if (ClaimService.canAccess(serverLevel, player, pos, permission)) return false;
		if (player != null) player.displayClientMessage(Component.translatable("message.regnum-nations.territory.protected"), true);
		return true;
	}
}
