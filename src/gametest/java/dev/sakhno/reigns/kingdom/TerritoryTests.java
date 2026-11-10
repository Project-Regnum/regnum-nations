package dev.sakhno.reigns.kingdom;

import com.mojang.authlib.GameProfile;
import dev.sakhno.reigns.block.KingdomObeliskBlockEntity;
import dev.sakhno.reigns.block.ModBlocks;
import java.nio.file.Files;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static dev.sakhno.reigns.kingdom.FoundingException.Reason.*;

public class TerritoryTests implements FabricGameTest {
	@GameTest(template = EMPTY_STRUCTURE, batch = "territory")
	public void foundingAndClaimMutationsAreAtomic(GameTestHelper h) throws Exception {
		ServerLevel level = h.getLevel();
		BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
		level.setBlockAndUpdate(pos, ModBlocks.KINGDOM_OBELISK.defaultBlockState());
		KingdomSavedData data = new KingdomSavedData();
		KingdomService service = new KingdomService(level.getServer(), data);
		ServerPlayer founder = player(level, pos);
		service.select(founder, pos);
		Kingdom kingdom = service.found(founder, "Territory First");
		ClaimPos capital = ClaimPos.at(level.dimension(), pos);
		h.assertTrue(kingdom.id().equals(data.claimOwner(capital)), "Founding must claim its capital atomically");
		BlockPos otherPos = new BlockPos(pos.getX() ^ 1, pos.getY(), pos.getZ());
		level.setBlockAndUpdate(otherPos, ModBlocks.KINGDOM_OBELISK.defaultBlockState());
		ServerPlayer outsider = player(level, otherPos);
		service.select(outsider, otherPos);
		reject(h, data, TERRITORY_OCCUPIED, () -> service.found(outsider, "Territory Second"));
		h.assertTrue(((KingdomObeliskBlockEntity) level.getBlockEntity(otherPos)).getKingdomId() == null,
				"Overlap must not bind the competing obelisk");
		ClaimService claims = new ClaimService(data);
		reject(h, data, NO_KINGDOM, () -> claims.manage(outsider, false));
		move(founder, capital.offset(1, 1));
		reject(h, data, NOT_ADJACENT, () -> claims.manage(founder, false));
		move(founder, new ClaimPos(Level.NETHER, capital.x(), capital.z()));
		reject(h, data, NOT_ADJACENT, () -> claims.manage(founder, false));
		move(founder, capital.offset(1, 0));
		claims.manage(founder, false);
		reject(h, data, TERRITORY_OCCUPIED, () -> claims.manage(founder, false));
		claims.manage(founder, true);
		h.assertTrue(data.claimOwner(capital.offset(1, 0)) == null, "Unclaim must release non-capital land");
		move(founder, capital);
		reject(h, data, CAPITAL_CHUNK, () -> claims.manage(founder, true));
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "territory")
	public void claimsPersistAndLegacyConflictsPreserveOwners(GameTestHelper h) throws Exception {
		ServerLevel level = h.getLevel();
		KingdomSavedData data = new KingdomSavedData();
		Kingdom first = kingdom(UUID.randomUUID(), new BlockPos(-17, 64, -1), 1, "Legacy First");
		Kingdom second = kingdom(UUID.randomUUID(), new BlockPos(160, 64, 0), 2, "Legacy Second");
		data.add(first);
		data.add(second);
		var directory = Files.createTempDirectory("reigns-claims-");
		try {
			var writer = new DimensionDataStorage(directory.toFile(), level.getServer().getFixerUpper(), level.registryAccess());
			writer.set(KingdomSavedData.FILE_ID, data);
			writer.save();
			var reader = new DimensionDataStorage(directory.toFile(), level.getServer().getFixerUpper(), level.registryAccess());
			KingdomSavedData restored = reader.computeIfAbsent(KingdomSavedData.FACTORY, KingdomSavedData.FILE_ID);
			h.assertTrue(first.id().equals(restored.claimOwner(new ClaimPos(Level.OVERWORLD, -2, -1)))
					&& restored.claimCount(second.id()) == 1, "Dimension and negative chunk coordinates must survive disk reload");
		} finally {
			try (var files = Files.list(directory)) { for (var file : files.toList()) Files.delete(file); }
			Files.delete(directory);
		}
		CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
		var records = saved.getList("Kingdoms", Tag.TAG_COMPOUND);
		for (int i = 0; i < records.size(); i++) {
			if (records.getCompound(i).getUUID("Id").equals(second.id())) {
				records.getCompound(i).putLong("Position", first.capitalPos().offset(-1, 0, 0).asLong());
			}
		}
		KingdomSavedData conflicted = KingdomSavedData.load(saved, level.registryAccess());
		h.assertTrue(first.id().equals(conflicted.claimOwner(ClaimPos.at(first.dimension(), first.capitalPos())))
				&& conflicted.kingdoms().size() == 2, "Existing owner and both histories must survive conflict");
		ServerPlayer loser = new ServerPlayer(level.getServer(), level, new GameProfile(second.founderId(), "LegacyLoser"), ClientInformation.createDefault());
		reject(h, conflicted, CAPITAL_CONFLICT, () -> new ClaimService(conflicted).manage(loser, false));
		saved.putInt("Version", 1);
		saved.remove("Claims");
		KingdomSavedData migrated = KingdomSavedData.load(saved, level.registryAccess());
		h.assertTrue(first.id().equals(migrated.claimOwner(ClaimPos.at(first.dimension(), first.capitalPos())))
				&& migrated.isDirty() && migrated.kingdoms().size() == 2, "Legacy collisions must choose a stable owner without deleting kingdoms");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "territory")
	public void protectionRejectsOutsidersAndAllowsFounder(GameTestHelper h) throws Exception {
		ServerLevel level = h.getLevel();
		// Isolate persistent live-server claims from the existing foundation test structures.
		BlockPos pos = new BlockPos(16_008, 64, 16_008);
		ServerPlayer founder = player(level, pos);
		ServerPlayer outsider = player(level, pos);
		KingdomSavedData data = KingdomService.get(level.getServer()).data();
		data.add(kingdom(founder.getUUID(), pos, 1, "Protection " + UUID.randomUUID().toString().substring(0, 8)));
		level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		h.assertFalse(ClaimService.canBuild(level, outsider, pos), "Outsiders must not build");
		h.assertTrue(ClaimService.canBuild(level, founder, pos), "Founder must build normally");
		h.assertFalse(PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, outsider, pos.below(),
				level.getBlockState(pos.below()), null), "Actual break hook must reject outsiders");
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false);
		h.assertTrue(UseBlockCallback.EVENT.invoker().interact(outsider, level, InteractionHand.OFF_HAND, hit)
				== InteractionResult.FAIL, "Offhand interaction must be rejected");
		BlockItem item = (BlockItem) Items.STONE;
		h.assertFalse(item.place(new BlockPlaceContext(level, outsider, InteractionHand.MAIN_HAND,
				new ItemStack(item), hit)).consumesAction(), "Actual placement mixin must reject outsiders");
		h.assertTrue(level.getBlockState(pos).isAir(), "Denied placement must not mutate the world");
		h.assertTrue(item.place(new BlockPlaceContext(level, founder, InteractionHand.MAIN_HAND,
				new ItemStack(item), hit)).consumesAction(), "Actual placement must allow founder");
		h.succeed();
	}

	private static Kingdom kingdom(UUID founder, BlockPos pos, long time, String name) {
		return new Kingdom(UUID.randomUUID(), new KingdomName(name), founder, Level.OVERWORLD, pos,
				UUID.randomUUID(), time, Kingdom.CapitalStatus.PRESENT);
	}

	private static ServerPlayer player(ServerLevel level, BlockPos pos) {
		ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "ClaimTest"), ClientInformation.createDefault()) {
			@Override public void displayClientMessage(Component message, boolean overlay) { }
		};
		player.setPos(Vec3.atCenterOf(pos).add(0, 0, 2));
		return player;
	}

	private static void move(ServerPlayer player, ClaimPos pos) {
		player.setServerLevel(player.serverLevel().getServer().getLevel(pos.dimension()));
		player.setPos(pos.x() * 16 + 8, 64, pos.z() * 16 + 8);
	}

	private interface Request { void run() throws Exception; }
	private static void reject(GameTestHelper h, KingdomSavedData data, FoundingException.Reason reason, Request request) throws Exception {
		CompoundTag before = data.save(new CompoundTag(), h.getLevel().registryAccess());
		data.setDirty(false);
		try { request.run(); h.fail("Expected " + reason); }
		catch (FoundingException failure) { h.assertTrue(failure.reason() == reason, "Wrong rejection: " + failure.reason()); }
		h.assertTrue(before.equals(data.save(new CompoundTag(), h.getLevel().registryAccess())) && !data.isDirty(),
				"Rejected mutation must preserve registry/claims exactly");
	}
}
