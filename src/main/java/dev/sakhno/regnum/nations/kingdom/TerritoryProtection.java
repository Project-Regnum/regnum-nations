package dev.sakhno.regnum.nations.kingdom;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;

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

	public static boolean denied(Level level, Player player, BlockPos pos, CityPermission permission) {
		if (!(level instanceof ServerLevel serverLevel)) return false;
		if (ClaimService.canAccess(serverLevel, player, pos, permission)) return false;
		if (player != null) player.displayClientMessage(Component.translatable("message.regnum-nations.territory.protected"), true);
		return true;
	}

    public static boolean terrainAllowed(Level level, BlockPos pos) {
        return !(level instanceof ServerLevel server) || ClaimService.canChangeTerrain(server, pos);
    }

    public static boolean transferAllowed(Object level, BlockPos source, BlockPos target) {
        return !(level instanceof ServerLevel server) || ClaimService.sameTerritory(server, source, target);
    }

    /** Resolve the entire sticky graph before triggerEvent makes its first write (including retraction). */
    public static boolean pistonAllowed(Level level, BlockPos piston, Direction facing, boolean extending, boolean sticky) {
        if (!TerritoryProtectionConfig.pistons || !(level instanceof ServerLevel)) return true;
        if (!transferAllowed(level, piston, piston.relative(facing))) return false;
        if (!extending && !sticky) return true;
        // Vanilla can finalize a still-extending block before resolving a rapid sticky retraction.
        if (!extending && level.getBlockEntity(piston.relative(facing, 2)) instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity
                && !transferAllowed(level, piston, piston.relative(facing, 2))) return false;
        var movement = new PistonStructureResolver(level, piston, facing, extending);
        if (!movement.resolve()) return true; // Vanilla handles an immovable structure.
        Direction travel = extending ? facing : facing.getOpposite();
        for (BlockPos source : movement.getToPush()) {
            if (!transferAllowed(level, piston, source) || !transferAllowed(level, piston, source.relative(travel))) return false;
        }
        for (BlockPos destroyed : movement.getToDestroy()) {
            if (!transferAllowed(level, piston, destroyed)) return false;
        }
        return true;
    }

    /** Real player/projectile permissions stay centralized; mobs and ownerless projectiles have none. */
    public static boolean entityAllowed(Level level, Entity entity, BlockPos pos, CityPermission permission) {
        if (!(level instanceof ServerLevel server)) return true;
        Entity actor = entity instanceof Projectile projectile ? projectile.getOwner() : entity;
        return ClaimService.canAccess(server, actor instanceof Player player ? player : null, pos, permission);
    }
}
