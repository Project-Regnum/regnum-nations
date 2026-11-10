package dev.sakhno.reigns.kingdom;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;

import dev.sakhno.reigns.block.KingdomObeliskBlockEntity;
import dev.sakhno.reigns.block.ModBlocks;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import static dev.sakhno.reigns.kingdom.FoundingException.Reason.*;

/** All mutations run on the server thread; commands and interactions share the same validation. */
public final class KingdomService {
	public static final long SELECTION_TICKS = 30 * 20;
	public static final double MAX_DISTANCE = 6;
	private static final Map<MinecraftServer, KingdomService> SERVICES = new IdentityHashMap<>();

	private final MinecraftServer server;
	private final KingdomSavedData data;
	private final SettlementService cities;
	private final Map<String,DeleteConfirmation> confirmations=new HashMap<>();
	private record DeleteConfirmation(UUID kingdom,UUID token,Object actor,long at) { }
	private final Map<UUID, Pending> pending = new HashMap<>();

	private record Pending(ServerPlayer player, ResourceKey<Level> dimension, BlockPos pos,
			UUID obeliskId, long selectedAt) { }

	KingdomService(MinecraftServer server, KingdomSavedData data) {
		this.server = server;
		this.data = data;
		this.cities = new SettlementService(this,server,data);
	}

	public static void initialize() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			KingdomService service = SERVICES.get(server);
			if (service != null) {
				service.forget(handler.player.getUUID()); service.cities.disconnect(handler.player.getUUID());
				service.confirmations.remove(handler.player.getUUID().toString());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(SERVICES::remove);
	}

	public static KingdomService get(MinecraftServer server) {
		if (!server.isSameThread()) throw new IllegalStateException("Kingdom service requires the server thread");
		return SERVICES.computeIfAbsent(server, key -> new KingdomService(key, KingdomSavedData.get(key)));
	}

	public KingdomSavedData data() {
		checkThread();
		return data;
	}

	private void checkThread() {
		if (!server.isSameThread()) throw new IllegalStateException("Kingdom service requires the server thread");
	}

	public void forget(UUID playerId) {
		checkThread();
		pending.remove(playerId);
		cities.forget(playerId);
	}

	void validatePlayer(ServerPlayer player) throws FoundingException {
		checkThread();
		if (player.serverLevel().getServer() != server || !player.isAlive() || player.isRemoved() || player.isSpectator()) {
			throw new FoundingException(INVALID_PLAYER);
		}
		data.requireAvailable();
	}

	/** Selection is ephemeral, tied to this player entity/session, and never grants ownership. */
	public void select(ServerPlayer player, BlockPos pos) throws FoundingException {
		forget(player.getUUID());
		validatePlayer(player);
		if (data.byFounder(player.getUUID()) != null) throw new FoundingException(ALREADY_FOUNDER);
		checkDistance(player, pos);
		if (data.byOwner(player.getUUID())!=null) cities.stateAnchor(player,pos);
		KingdomObeliskBlockEntity obelisk = obeliskAt(player.serverLevel(), pos);
		if (obelisk.getKingdomId() != null || data.atCapital(player.level().dimension(), pos) != null) {
			throw new FoundingException(ALREADY_BOUND);
		}
		long now = server.overworld().getGameTime();
		pending.values().removeIf(selection -> now - selection.selectedAt() >= SELECTION_TICKS);
		pending.put(player.getUUID(), new Pending(player, player.level().dimension(), pos.immutable(),
				obelisk.getObeliskId(), now));
	}

	/** Consume even failed attempts. All persistent validation precedes the two dirty-state writes. */
	public Kingdom found(ServerPlayer player, String inputName) throws FoundingException {
		return foundWithColor(player,inputName,PoliticalColors.fallback(player.getUUID()));
	}

	private Kingdom foundWithColor(ServerPlayer player, String inputName, int color) throws FoundingException {
		checkThread();
		Pending selection = pending.remove(player.getUUID());
		validatePlayer(player);
		if (data.byFounder(player.getUUID()) != null) throw new FoundingException(ALREADY_FOUNDER);
		if (selection == null || selection.player() != player) throw new FoundingException(NO_SELECTION);
		long age = server.overworld().getGameTime() - selection.selectedAt();
		if (age < 0 || age >= SELECTION_TICKS) throw new FoundingException(EXPIRED);
		if (!player.level().dimension().equals(selection.dimension())) throw new FoundingException(WRONG_DIMENSION);
		checkDistance(player, selection.pos());
		KingdomObeliskBlockEntity obelisk = obeliskAt(player.serverLevel(), selection.pos());
		if (!obelisk.getObeliskId().equals(selection.obeliskId())) throw new FoundingException(OBELISK_UNAVAILABLE);
		if (obelisk.getKingdomId() != null) throw new FoundingException(ALREADY_BOUND);
		KingdomName name;
		try {
			name = new KingdomName(inputName);
		} catch (IllegalArgumentException exception) {
			throw new FoundingException(INVALID_NAME);
		}
		if (!PoliticalColors.valid(color)) throw new FoundingException(INVALID_COLOR);
		Settlement city=data.byOwner(player.getUUID())==null?null:cities.stateAnchor(player,selection.pos());
		data.validate(player.getUUID(), name, selection.dimension(), selection.pos(), selection.obeliskId());
		UUID kingdomId;
		do {
			kingdomId = UUID.randomUUID();
		} while (kingdomId.equals(selection.obeliskId()) || containsKingdom(kingdomId) || data.settlement(kingdomId)!=null || data.deletedAnchor(kingdomId)!=null);
		Kingdom kingdom = new Kingdom(kingdomId, name, player.getUUID(), selection.dimension(), selection.pos(),
				selection.obeliskId(), System.currentTimeMillis(), Kingdom.CapitalStatus.PRESENT,
				city==null?kingdomId:city.id(),color,"unspecified");
		// No callbacks, waits or world changes between registry insertion and binding on this thread.
		if (city==null) data.add(kingdom); else data.elevate(kingdom,city);
		obelisk.bindKingdom(kingdomId);
		return kingdom;
	}

	/** Bind GUI confirmation to the server-selected placed identity, then reuse founding rules. */
	public Kingdom found(ServerPlayer player, UUID expectedObelisk, String inputName) throws FoundingException {
		return found(player,expectedObelisk,inputName,PoliticalColors.fallback(player.getUUID()));
	}

	public Kingdom found(ServerPlayer player,UUID expectedObelisk,String inputName,int color) throws FoundingException {
		checkThread();
		Pending selection = pending.get(player.getUUID());
		if (selection == null || !selection.obeliskId().equals(expectedObelisk)) {
			pending.remove(player.getUUID());
			throw new FoundingException(NO_SELECTION);
		}
		return foundWithColor(player, inputName, color);
	}

	public SettlementService cities() { checkThread(); return cities; }
	public void cityRemoved(Settlement city) { checkThread(); data.settlementStatus(city,Kingdom.CapitalStatus.MISSING); }

	/** Clear only an exact, persisted deletion tombstone; never adopt or overwrite another binding. */
	public void cleanDeletedBinding(ServerLevel level,BlockPos pos,KingdomObeliskBlockEntity block) {
		checkThread();
		var tombstone=data.deletedAnchor(block.getKingdomId());
		if (tombstone!=null&&data.byId(tombstone.kingdom())==null&&tombstone.dimension().equals(level.dimension())
				&&tombstone.pos().equals(pos)&&tombstone.obelisk().equals(block.getObeliskId())) block.clearKingdom(tombstone.kingdom());
	}
	private static String executorKey(CommandSourceStack source) { return source.getEntity()==null?"console:"+source.getTextName():source.getEntity().getUUID().toString(); }
	public record DeletionRequest(UUID token,boolean existing) { }
	public UUID requestDeletion(CommandSourceStack source,UUID id) throws FoundingException,CommandSyntaxException {
		return prepareDeletion(source,id).token();
	}
	/** Repeated requests keep the current token and deadline; a different target must wait or cancel. */
	public DeletionRequest prepareDeletion(CommandSourceStack source,UUID id) throws FoundingException,CommandSyntaxException {
		checkThread(); String outcome="rejected";
		try {
			requireAdministrator(source); if (source.getServer()!=server) throw adminError("permission"); data.requireAvailable();
			if (data.byId(id)==null) throw adminError("unknown_kingdom",id.toString());
			long now=server.overworld().getGameTime();
			confirmations.values().removeIf(c -> now-c.at()<0 || now-c.at()>=SELECTION_TICKS);
			var existing=confirmations.get(executorKey(source));
			if (existing!=null) {
				if (existing.actor()!=source.getEntity()) {
					confirmations.remove(executorKey(source));
					throw adminError("delete_session_changed");
				}
				if (!existing.kingdom().equals(id)) throw adminError("delete_pending_other",existing.kingdom().toString());
				outcome="existing confirmation retained; deadline unchanged";
				return new DeletionRequest(existing.token(),true);
			}
			UUID token=UUID.randomUUID(); confirmations.put(executorKey(source),new DeleteConfirmation(id,token,source.getEntity(),now));
			outcome="confirmation issued"; return new DeletionRequest(token,false);
		} finally { KingdomAdminCommands.audit(source,"delete-request",id.toString(),"",outcome); }
	}
	/** Stale Cancel links cannot invalidate a newer request. No persistent data is mutated. */
	public void cancelDeletion(CommandSourceStack source,UUID id,UUID token) throws CommandSyntaxException {
		checkThread(); String outcome="rejected";
		try {
			requireAdministrator(source); if (source.getServer()!=server) throw adminError("permission");
			var confirmation=confirmations.get(executorKey(source));
			long age=confirmation==null?-1:server.overworld().getGameTime()-confirmation.at();
			if (confirmation==null || confirmation.actor()!=source.getEntity() || !confirmation.kingdom().equals(id)
					|| !confirmation.token().equals(token) || age<0 || age>=SELECTION_TICKS) throw adminError("delete_confirmation_invalid");
			confirmations.remove(executorKey(source)); outcome="cancelled; no saved state changed";
		} finally { KingdomAdminCommands.audit(source,"delete-cancel",id.toString(),"",outcome); }
	}
	public void confirmDeletion(CommandSourceStack source,UUID id,UUID token) throws FoundingException,CommandSyntaxException {
		checkThread(); DeleteConfirmation confirmation=confirmations.remove(executorKey(source)); String outcome="rejected";
		try {
			requireAdministrator(source); if (source.getServer()!=server) throw adminError("permission"); data.requireAvailable();
			long age=confirmation==null?-1:server.overworld().getGameTime()-confirmation.at();
			if (confirmation==null||confirmation.actor()!=source.getEntity()||!confirmation.kingdom().equals(id)||!confirmation.token().equals(token)
					||age<0||age>=SELECTION_TICKS) throw adminError("delete_confirmation_invalid");
			Kingdom kingdom=data.byId(id); if (kingdom==null) throw adminError("unknown_kingdom",id.toString());
			data.deleteKingdom(id);
			ServerLevel level=server.getLevel(kingdom.dimension());
			if (level!=null) {
				var chunk=level.getChunkSource().getChunkNow(kingdom.capitalPos().getX()>>4,kingdom.capitalPos().getZ()>>4);
				if (chunk!=null&&chunk.getBlockEntity(kingdom.capitalPos()) instanceof KingdomObeliskBlockEntity block) cleanDeletedBinding(level,kingdom.capitalPos(),block);
			}
			KingdomManagementServer.invalidate(server);
			outcome="deleted; settlements and claims preserved as independent city-states";
		} finally { KingdomAdminCommands.audit(source,"delete-confirm",id.toString(),"",outcome); }
	}

	/** Services independently authorize administrators, including calls outside Brigadier routing. */
	static void requireAdministrator(CommandSourceStack source)
			throws CommandSyntaxException {
		if (!source.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
		if (!source.hasPermission(3)) throw adminError("permission");
	}

	static CommandSyntaxException adminError(String key, Object... args) {
		return new SimpleCommandExceptionType(
				Component.translatable("admin.project-reigns." + key, args)).create();
	}

	/** Exact UUID or full normalized name only. Never guess a prefix or choose an ambiguous match. */
	public Kingdom identify(String identifier) throws FoundingException, CommandSyntaxException {
		checkThread(); data.requireAvailable();
		try {
			UUID id = UUID.fromString(identifier);
			if (id.toString().equalsIgnoreCase(identifier)) {
				Kingdom kingdom = data.byId(id);
				if (kingdom == null) throw adminError("unknown_kingdom", identifier);
				return kingdom;
			}
		} catch (IllegalArgumentException ignored) { }
		String key;
		try { key = new KingdomName(identifier).key(); }
		catch (IllegalArgumentException e) { throw adminError("unknown_kingdom", identifier); }
		var matches = data.kingdoms().stream().filter(k -> k.name().key().equals(key)).toList();
		if (matches.size() > 1) throw adminError("ambiguous", identifier);
		if (matches.isEmpty()) throw adminError("unknown_kingdom", identifier);
		return matches.getFirst();
	}

	public Kingdom rename(CommandSourceStack source, UUID id, String input)
			throws FoundingException, CommandSyntaxException {
		checkThread(); requireAdministrator(source);
		if (source.getServer() != server) throw adminError("permission");
		data.requireAvailable();
		if (data.byId(id) == null) throw adminError("unknown_kingdom", id.toString());
		KingdomName name;
		try { name = new KingdomName(input); }
		catch (IllegalArgumentException e) { throw new FoundingException(INVALID_NAME); }
		return data.rename(id, name);
	}

	public BlockPos teleportAdministrator(CommandSourceStack source, UUID id)
			throws FoundingException, CommandSyntaxException {
		checkThread(); requireAdministrator(source);
		if (source.getServer() != server) throw adminError("permission");
		data.requireAvailable();
		Kingdom kingdom = data.byId(id);
		if (kingdom == null) throw adminError("unknown_kingdom", id.toString());
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel target = server.getLevel(kingdom.dimension());
		if (target == null) throw adminError("unknown_dimension", kingdom.dimension().location().toString());
		BlockPos safe = safeCapitalPosition(target, kingdom.capitalPos(), player);
		if (safe == null) throw adminError("unsafe_capital");
		player.stopRiding();
		player.teleportTo(target, safe.getX()+0.5, safe.getY(), safe.getZ()+0.5, player.getYRot(), player.getXRot());
		player.fallDistance = 0;
		return safe;
	}

	/** Bounded conservative landing search: no world edits, unsupported air or hazardous floors. */
	static BlockPos safeCapitalPosition(ServerLevel target, BlockPos capital, ServerPlayer player) {
		int[] heights = {0,1,-1,2,-2,3,-3,4,-4,5,-5,6,-6};
		for (int radius=0;radius<=4;radius++) for (int x=-radius;x<=radius;x++) for (int z=-radius;z<=radius;z++) {
			if (Math.max(Math.abs(x),Math.abs(z)) != radius) continue;
			for (int dy : heights) {
				BlockPos feet = capital.offset(x,1+dy,z);
				if (feet.getY() <= target.getMinBuildHeight() || feet.getY()+2 >= target.getMaxBuildHeight()) continue;
				var box = player.getBoundingBox().move(Vec3.atBottomCenterOf(feet).subtract(player.position()));
				if (!target.getWorldBorder().isWithinBounds(box)) continue;
				var floor = target.getBlockState(feet.below());
				if (!floor.isFaceSturdy(target,feet.below(),Direction.UP)
						|| !target.getBlockState(feet).isAir() || !target.getBlockState(feet.above()).isAir()) continue;
				boolean hazard = false;
				for (BlockPos nearby : BlockPos.betweenClosed(feet.offset(-1,-1,-1),feet.offset(1,1,1))) {
					var state = target.getBlockState(nearby);
					if (state.getFluidState().is(FluidTags.LAVA)
							|| state.is(BlockTags.FIRE)
							|| state.is(Blocks.MAGMA_BLOCK)
							|| state.is(Blocks.CACTUS)
							|| state.is(Blocks.CAMPFIRE)
							|| state.is(Blocks.SOUL_CAMPFIRE)
							|| state.is(Blocks.POWDER_SNOW)
							|| state.is(Blocks.WITHER_ROSE)
							|| state.is(Blocks.SWEET_BERRY_BUSH)) { hazard = true; break; }
				}
				if (!hazard && target.noCollision(player,box)) return feet;
			}
		}
		return null;
	}

	private boolean containsKingdom(UUID id) {
		return data.kingdoms().stream().anyMatch(kingdom -> kingdom.id().equals(id));
	}

	static void checkDistance(ServerPlayer player, BlockPos pos) throws FoundingException {
		if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE * MAX_DISTANCE) {
			throw new FoundingException(TOO_FAR);
		}
	}

	private KingdomObeliskBlockEntity obeliskAt(ServerLevel level, BlockPos pos) throws FoundingException {
		LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
		if (chunk == null || !chunk.getBlockState(pos).is(ModBlocks.KINGDOM_OBELISK)
				|| !(chunk.getBlockEntity(pos) instanceof KingdomObeliskBlockEntity obelisk) || obelisk.isRemoved()) {
			throw new FoundingException(OBELISK_UNAVAILABLE);
		}
		cleanDeletedBinding(level,pos,obelisk);
		return obelisk;
	}

	/** Resolve on demand without loading chunks or scanning the world. History is never deleted. */
	public Kingdom.CapitalStatus resolveCapital(Kingdom kingdom) {
		checkThread();
		ServerLevel level = server.getLevel(kingdom.dimension());
		Kingdom.CapitalStatus status = Kingdom.CapitalStatus.UNRESOLVED;
		if (level != null && level.getChunkSource().getChunkNow(
				kingdom.capitalPos().getX() >> 4, kingdom.capitalPos().getZ() >> 4) != null) {
			status = Kingdom.CapitalStatus.MISSING;
			try {
				KingdomObeliskBlockEntity obelisk = obeliskAt(level, kingdom.capitalPos());
				if (obelisk.getObeliskId().equals(kingdom.capitalObeliskId())
						&& (obelisk.getKingdomId() == null || obelisk.getKingdomId().equals(kingdom.id()))) {
					// Recover a missing block-side association after a non-atomic world/chunk save.
					if (obelisk.getKingdomId() == null) obelisk.bindKingdom(kingdom.id());
					status = Kingdom.CapitalStatus.PRESENT;
				}
			} catch (FoundingException ignored) {
				// The loaded chunk confirms the expected capital is absent.
			}
		}
		data.capitalStatus(kingdom, status);
		return status;
	}

	public void capitalRemoved(ServerLevel level, BlockPos pos) {
		checkThread();
		Kingdom kingdom = data.atCapital(level.dimension(), pos);
		if (kingdom != null) data.capitalStatus(kingdom, Kingdom.CapitalStatus.MISSING);
		Settlement city=data.cityAt(level.dimension(),pos);
		if (city!=null&&city.legacyAnchor()) cityRemoved(city);
	}
}
