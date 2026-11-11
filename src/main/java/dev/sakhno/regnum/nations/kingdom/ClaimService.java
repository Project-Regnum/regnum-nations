package dev.sakhno.regnum.nations.kingdom;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;

/** Settlement ownership authorizes land management; sovereignty never grants private-domain rights. */
public final class ClaimService {
	private final KingdomSavedData data;

	public ClaimService(KingdomSavedData data) {
		this.data = data;
	}

	public void manage(ServerPlayer player, boolean release) throws FoundingException {
		manage(player, ClaimPos.at(player.level().dimension(), player.blockPosition()), null, release);
	}

	/** UI and command actions use the same checks; a selected remote chunk is never substituted. */
	public void manage(ServerPlayer player, ClaimPos pos, UUID viewedKingdom, boolean release)
			throws FoundingException {
		Settlement kingdom = validate(player, pos, viewedKingdom, release);
		if (release) data.removeClaim(pos);
		else data.putClaim(pos, kingdom.id());
	}

	/** Read-only preview of the exact mutation checks. Never changes dirty state. */
	public Settlement validate(ServerPlayer player, ClaimPos pos, UUID viewedKingdom, boolean release)
			throws FoundingException {
		if (!player.serverLevel().getServer().isSameThread()) throw new IllegalStateException("Server thread required");
		if (!player.isAlive() || player.isRemoved() || player.isSpectator()) throw new FoundingException(INVALID_PLAYER);
		data.requireAvailable();
		Settlement kingdom = data.citizenCity(player.getUUID());
		if (kingdom == null) throw new FoundingException(NO_KINGDOM);
		if (!CityPermissions.allows(data,kingdom,player.getUUID(),CityPermission.MANAGE_CLAIMS)) throw new FoundingException(NOT_OWNER);
		if (viewedKingdom != null && !kingdom.id().equals(viewedKingdom) && !viewedKingdom.equals(kingdom.sovereign())) throw new FoundingException(NOT_OWNER);
		ClaimPos capital = ClaimPos.at(kingdom.dimension(), kingdom.obeliskPos());
		if (!kingdom.id().equals(data.claimOwner(capital))) throw new FoundingException(CAPITAL_CONFLICT);
		if (!pos.dimension().equals(player.level().dimension())) throw new FoundingException(WRONG_DIMENSION);
		if (!pos.equals(ClaimPos.at(player.level().dimension(), player.blockPosition()))) throw new FoundingException(NOT_IN_CHUNK);
		if (release) {
			validateRelease(kingdom, pos);
		} else {
			if (data.claimOwner(pos) != null) throw new FoundingException(TERRITORY_OCCUPIED);
			if (!adjacent(kingdom.id(), pos)) throw new FoundingException(NOT_ADJACENT);
		}
		return kingdom;
	}

	/** Explicit administrative operation, never a blanket bypass of ordinary claim management. */
	public void forceUnclaim(CommandSourceStack source, UUID kingdomId, ClaimPos pos)
			throws FoundingException, CommandSyntaxException {
		KingdomService.requireAdministrator(source);
		data.requireAvailable();
		Kingdom kingdom = data.byId(kingdomId);
		if (kingdom == null) throw KingdomService.adminError("unknown_kingdom", kingdomId.toString());
		if (source.getServer().getLevel(pos.dimension()) == null) {
			throw KingdomService.adminError("unknown_dimension", pos.dimension().location().toString());
		}
		Settlement owner=data.settlement(data.claimOwner(pos));
		if (owner==null||!kingdomId.equals(owner.sovereign())) throw new FoundingException(NOT_OWNER);
		validateRelease(owner,pos);
		data.removeClaim(pos);
	}

	private void validateRelease(Settlement kingdom, ClaimPos pos) throws FoundingException {
		if (!kingdom.id().equals(data.claimOwner(pos))) throw new FoundingException(NOT_OWNER);
		if (data.isCapital(pos)) {
			throw new FoundingException(CAPITAL_CHUNK);
		}
	}

	public boolean adjacent(UUID kingdom, ClaimPos pos) {
		return kingdom != null && (kingdom.equals(data.claimOwner(pos.offset(1, 0)))
				|| kingdom.equals(data.claimOwner(pos.offset(-1, 0)))
				|| kingdom.equals(data.claimOwner(pos.offset(0, 1)))
				|| kingdom.equals(data.claimOwner(pos.offset(0, -1))));
	}

	/** Fail closed on unreadable registry data. No OP or Creative bypass. */
	public static boolean canBuild(ServerLevel level, Player player, BlockPos pos) {
		return canAccess(level,player,pos,CityPermission.BUILD);
	}

	/** Block-action gate shared by build and interaction hooks. Wilderness needs no municipal permission. */
	public static boolean canAccess(ServerLevel level, Player player, BlockPos pos, CityPermission permission) {
		KingdomSavedData data = KingdomService.get(level.getServer()).data();
		if (!data.available()) return false;
		UUID ownerId=data.claimOwner(ClaimPos.at(level.dimension(),pos));
		Settlement owner=data.settlement(ownerId);
		return ownerId==null || CityPermissions.allows(data,owner,player==null?null:player.getUUID(),permission);
	}

	/** Unattributed changes have no municipal permission, even when triggered by a member. */
	public static boolean canChangeTerrain(ServerLevel level, BlockPos pos) {
		return canAccess(level, null, pos, CityPermission.BUILD);
	}

	/** Machines have no player identity. Only the same current city domain may exchange blocks/fluids. */
	public static boolean sameTerritory(ServerLevel level, BlockPos source, BlockPos target) {
		KingdomSavedData data = KingdomService.get(level.getServer()).data();
		if (!data.available()) return false;
		UUID sourceOwner = data.claimOwner(ClaimPos.at(level.dimension(), source));
		UUID targetOwner = data.claimOwner(ClaimPos.at(level.dimension(), target));
		return java.util.Objects.equals(sourceOwner, targetOwner)
				&& (sourceOwner == null || data.settlement(sourceOwner) != null);
	}

	/** One registry read per explosion; include air positions because vanilla also uses this list for fire. */
	public static void filterExplosion(ServerLevel level, java.util.List<BlockPos> affected) {
		KingdomSavedData data = KingdomService.get(level.getServer()).data();
		if (!data.available()) affected.clear();
		else affected.removeIf(pos -> data.claimOwner(ClaimPos.at(level.dimension(), pos)) != null);
	}
}
