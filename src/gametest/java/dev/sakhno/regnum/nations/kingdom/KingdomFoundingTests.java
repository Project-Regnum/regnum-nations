package dev.sakhno.regnum.nations.kingdom;

import com.mojang.authlib.GameProfile;
import dev.sakhno.regnum.nations.block.KingdomObeliskBlockEntity;
import dev.sakhno.regnum.nations.block.ModBlocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;

public class KingdomFoundingTests implements FabricGameTest {
	private static final BlockPos POS = new BlockPos(1, 1, 1);
	private static final BlockPos SECOND = new BlockPos(3, 1, 1);

	@GameTest(template = EMPTY_STRUCTURE)
	public void foundingRequiresSelectionAndPersistsDistinctIdentities(GameTestHelper helper) throws Exception {
		Fixture f = new Fixture(helper);
		helper.assertTrue(f.data.kingdoms().isEmpty() && f.obelisk.getKingdomId() == null,
				"Placement alone must never found a kingdom");
		rejected(f, NO_SELECTION, () -> f.service.found(f.player, "Golden Vale"));
		f.service.select(f.player, f.pos);
		helper.assertTrue(f.data.kingdoms().isEmpty(), "Selection alone must never found a kingdom");
		Kingdom kingdom = f.service.found(f.player, "  Golden   Vale  ");
		helper.assertTrue(f.data.kingdoms().size() == 1 && f.data.isDirty(), "Founding must dirty exactly one registry record");
		helper.assertTrue(kingdom.name().display().equals("Golden Vale"), "Display name must be normalized");
		helper.assertTrue(kingdom.founderId().equals(f.player.getUUID()), "Founder must be the requesting player");
		helper.assertTrue(kingdom.dimension().equals(f.level.dimension()) && kingdom.capitalPos().equals(f.pos),
				"Capital must include the server dimension and position");
		helper.assertTrue(kingdom.createdAt() > 0, "Creation timestamp must be recorded");
		helper.assertFalse(kingdom.id().equals(f.obelisk.getObeliskId()), "Kingdom and obelisk identities must differ");
		helper.assertTrue(kingdom.capitalObeliskId().equals(f.obelisk.getObeliskId())
				&& kingdom.id().equals(f.obelisk.getKingdomId()), "Association must be explicit on both sides");
		CompoundTag block = f.obelisk.saveWithFullMetadata(f.level.registryAccess());
		helper.assertTrue(block.getUUID("KingdomId").equals(kingdom.id())
				&& block.getUUID("ObeliskId").equals(kingdom.capitalObeliskId()), "Both identities must be saved separately");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void invalidAndDuplicateNamesDoNotPartiallyMutateState(GameTestHelper helper) throws Exception {
		Fixture f = new Fixture(helper);
		for (String invalid : List.of("ab", "§aGolden", "A".repeat(33), "---")) {
			f.service.select(f.player, f.pos);
			rejected(f, INVALID_NAME, () -> f.service.found(f.player, invalid));
		}
		f.service.select(f.player, f.pos);
		f.service.found(f.player, "Золота Долина");
		helper.setBlock(SECOND, ModBlocks.KINGDOM_OBELISK);
		ServerPlayer second = f.anotherPlayer();
		f.service.select(second, helper.absolutePos(SECOND));
		KingdomObeliskBlockEntity available = helper.getBlockEntity(SECOND);
		rejected(f, f.data, available, DUPLICATE_NAME, () -> f.service.found(second, "  ЗОЛОТА   ДОЛИНА  "));
		helper.assertTrue(available.getKingdomId() == null, "Duplicate name must leave the other obelisk unbound");
		rejected(f, NO_SELECTION, () -> f.service.found(second, "Different Vale"));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void competingConfirmationsAndRepeatedRequestsAreSafe(GameTestHelper helper) throws Exception {
		Fixture f = new Fixture(helper);
		ServerPlayer second = f.anotherPlayer();
		f.service.select(f.player, f.pos);
		f.service.select(second, f.pos);
		Kingdom first = f.service.found(f.player, "First Vale");
		rejected(f, ALREADY_BOUND, () -> f.service.found(second, "Second Vale"));
		rejected(f, ALREADY_FOUNDER, () -> f.service.found(f.player, "First Vale"));
		helper.assertTrue(f.data.kingdoms().size() == 1 && first.id().equals(f.obelisk.getKingdomId()),
				"Only the first validated request may create a record or bind the capital");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void oneFounderConstraintSurvivesCapitalDestruction(GameTestHelper helper) throws Exception {
		Fixture f = new Fixture(helper);
		f.service.select(f.player, f.pos);
		Kingdom kingdom = f.service.found(f.player, "Lost Vale");
		helper.setBlock(SECOND, ModBlocks.KINGDOM_OBELISK);
		rejected(f, ALREADY_FOUNDER, () -> f.service.select(f.player, helper.absolutePos(SECOND)));
		f.level.destroyBlock(f.pos, false);
		helper.assertTrue(f.service.resolveCapital(kingdom) == Kingdom.CapitalStatus.MISSING, "Removed capital must be missing");
		rejected(f, ALREADY_FOUNDER, () -> f.service.select(f.player, helper.absolutePos(SECOND)));
		rejected(f, ALREADY_FOUNDER, () -> f.service.found(f.player, "New Vale"));
		helper.assertTrue(f.data.byFounder(f.player.getUUID()) != null, "Destruction must preserve kingdom history and founder constraint");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void selectionExpiresAndCannotBeReplayed(GameTestHelper helper) throws Exception {
		Fixture f = new Fixture(helper);
		f.service.select(f.player, f.pos);
		ServerLevelData clock = (ServerLevelData) f.level.getServer().overworld().getLevelData();
		long before = clock.getGameTime();
		try {
			clock.setGameTime(before + KingdomService.SELECTION_TICKS);
			rejected(f, EXPIRED, () -> f.service.found(f.player, "Expired Vale"));
		} finally {
			clock.setGameTime(before);
		}
		rejected(f, NO_SELECTION, () -> f.service.found(f.player, "Expired Vale"));
		f.service.select(f.player, f.pos);
		f.service.forget(f.player.getUUID());
		rejected(f, NO_SELECTION, () -> f.service.found(f.player, "Logged Out Vale"));
		f.service.select(f.player, f.pos);
		KingdomService restarted = new KingdomService(f.level.getServer(), f.data);
		rejected(f, NO_SELECTION, () -> restarted.found(f.player, "Restarted Vale"));
		rejected(f, NO_SELECTION, () -> f.service.found(f.anotherPlayer(), "Other Player Vale"));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void respawnedPlayerCannotReuseEntitySelection(GameTestHelper helper) throws Exception {
		Fixture f = new Fixture(helper);
		f.service.select(f.player, f.pos);
		ServerPlayer respawned = new ServerPlayer(f.level.getServer(), f.level,
				f.player.getGameProfile(), ClientInformation.createDefault());
		respawned.setPos(f.player.position());
		rejected(f, NO_SELECTION, () -> f.service.found(respawned, "Respawn Vale"));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wrongDimensionAndDistanceAreRejectedOnServer(GameTestHelper helper) throws Exception {
		Fixture f = new Fixture(helper);
		f.service.select(f.player, f.pos);
		ServerLevel other = f.level.getServer().getLevel(Level.NETHER);
		f.player.setServerLevel(other);
		try {
			rejected(f, WRONG_DIMENSION, () -> f.service.found(f.player, "Other Dimension Vale"));
		} finally {
			f.player.setServerLevel(f.level);
		}
		f.service.select(f.player, f.pos);
		f.player.setPos(Vec3.atCenterOf(f.pos).add(KingdomService.MAX_DISTANCE + 1, 0, 0));
		rejected(f, TOO_FAR, () -> f.service.found(f.player, "Far Vale"));
		rejected(f, TOO_FAR, () -> f.service.select(f.player, f.pos));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void removedOrReplacedObeliskInvalidatesSelection(GameTestHelper helper) throws Exception {
		Fixture f = new Fixture(helper);
		f.service.select(f.player, f.pos);
		helper.setBlock(POS, Blocks.AIR);
		rejected(f, OBELISK_UNAVAILABLE, () -> f.service.found(f.player, "Removed Vale"));
		helper.setBlock(POS, ModBlocks.KINGDOM_OBELISK);
		f.service.select(f.player, f.pos);
		KingdomObeliskBlockEntity original = helper.getBlockEntity(POS);
		helper.setBlock(POS, Blocks.AIR);
		helper.setBlock(POS, ModBlocks.KINGDOM_OBELISK);
		rejected(f, OBELISK_UNAVAILABLE, () -> f.service.found(f.player, "Replacement Vale"));
		KingdomObeliskBlockEntity replacement = helper.getBlockEntity(POS);
		helper.assertFalse(replacement.getObeliskId().equals(original.getObeliskId()), "Replacement identity must differ");
		helper.assertTrue(replacement.getKingdomId() == null, "Stale selection must not bind replacement");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spectatorAndDeadPlayersCannotFound(GameTestHelper helper) throws Exception {
		Fixture f = new Fixture(helper);
		f.service.select(f.player, f.pos);
		f.player.gameMode.changeGameModeForPlayer(GameType.SPECTATOR);
		rejected(f, INVALID_PLAYER, () -> f.service.found(f.player, "Spectator Vale"));
		rejected(f, INVALID_PLAYER, () -> f.service.select(f.player, f.pos));
		f.player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
		f.service.select(f.player, f.pos);
		f.player.setHealth(0);
		rejected(f, INVALID_PLAYER, () -> f.service.found(f.player, "Dead Vale"));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void savedDataAndCapitalBindingSurviveDiskReload(GameTestHelper helper) throws Exception {
		Fixture f = new Fixture(helper);
		f.service.select(f.player, f.pos);
		Kingdom expected = f.service.found(f.player, "Saved Vale");
		Path directory = Files.createTempDirectory("regnum-nations-kingdoms-");
		try {
			DimensionDataStorage writer = new DimensionDataStorage(directory.toFile(),
					f.level.getServer().getFixerUpper(), f.level.registryAccess());
			writer.set(KingdomSavedData.FILE_ID, f.data);
			writer.save();
			helper.assertTrue(Files.size(directory.resolve(KingdomSavedData.FILE_ID + ".dat")) > 0,
					"Minecraft saved-data storage must write the registry to disk");
			DimensionDataStorage reader = new DimensionDataStorage(directory.toFile(),
					f.level.getServer().getFixerUpper(), f.level.registryAccess());
			KingdomSavedData restored = reader.computeIfAbsent(KingdomSavedData.FACTORY, KingdomSavedData.FILE_ID);
			Kingdom loaded = restored.byFounder(f.player.getUUID());
			helper.assertTrue(loaded != null && loaded.withCapitalStatus(Kingdom.CapitalStatus.PRESENT).equals(expected),
					"Every kingdom field must survive Minecraft's disk serialization");
			helper.assertTrue(loaded.capitalStatus() == Kingdom.CapitalStatus.UNRESOLVED,
					"Reload must not assume that capitals have loaded");
			helper.assertTrue(reader.computeIfAbsent(KingdomSavedData.FACTORY, KingdomSavedData.FILE_ID) == restored
					&& restored.kingdoms().size() == 1, "Repeated retrieval must never duplicate records");
			Path blockFile = directory.resolve("capital.nbt");
			NbtIo.write(f.obelisk.saveWithFullMetadata(f.level.registryAccess()), blockFile);
			KingdomObeliskBlockEntity capital = restoreCapital(f, NbtIo.read(blockFile));
			helper.assertTrue(expected.id().equals(capital.getKingdomId())
					&& expected.capitalObeliskId().equals(capital.getObeliskId()), "Capital association must survive disk reload");
			KingdomService reloadedService = new KingdomService(f.level.getServer(), restored);
			helper.assertTrue(reloadedService.resolveCapital(loaded) == Kingdom.CapitalStatus.PRESENT,
					"Reloaded registry must resolve the reloaded capital");
			rejected(f, restored, capital, ALREADY_FOUNDER, () -> reloadedService.found(f.player, "Duplicate Vale"));
			rejected(f, restored, capital, ALREADY_BOUND, () -> reloadedService.select(f.anotherPlayer(), f.pos));
			helper.assertTrue(restored.kingdoms().size() == 1, "Reloaded registry must retain exactly one record");
		} finally {
			try (var files = Files.list(directory)) {
				for (Path file : files.toList()) Files.delete(file);
			}
			Files.delete(directory);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void registryRepairsOnlyMatchingCapitalAndRetainsMissingHistory(GameTestHelper helper) throws Exception {
		Fixture f = new Fixture(helper);
		f.service.select(f.player, f.pos);
		Kingdom kingdom = f.service.found(f.player, "Recovery Vale");
		CompoundTag block = f.obelisk.saveWithFullMetadata(f.level.registryAccess());
		block.remove("KingdomId");
		KingdomObeliskBlockEntity capital = restoreCapital(f, block);
		rejected(f, f.data, capital, ALREADY_BOUND, () -> f.service.select(f.anotherPlayer(), f.pos));
		helper.assertTrue(capital.getKingdomId() == null, "Failed founding selection must not mutate persistent block state");
		helper.assertTrue(f.service.resolveCapital(kingdom) == Kingdom.CapitalStatus.PRESENT
				&& kingdom.id().equals(capital.getKingdomId()), "Registry may restore a lost link for the exact capital identity");
		block.putUUID("KingdomId", UUID.randomUUID());
		KingdomObeliskBlockEntity conflicted = restoreCapital(f, block);
		helper.assertTrue(f.service.resolveCapital(kingdom) == Kingdom.CapitalStatus.MISSING,
				"Conflicting associations must remain missing rather than be overwritten");
		helper.assertFalse(kingdom.id().equals(conflicted.getKingdomId()), "Conflict must not be silently rebound");
		helper.setBlock(POS, Blocks.AIR);
		helper.setBlock(POS, ModBlocks.KINGDOM_OBELISK);
		KingdomObeliskBlockEntity replacement = helper.getBlockEntity(POS);
		helper.assertTrue(f.service.resolveCapital(kingdom) == Kingdom.CapitalStatus.MISSING
				&& replacement.getKingdomId() == null, "Replacement must never inherit the missing capital identity");
		helper.assertTrue(f.data.kingdoms().size() == 1, "Missing/conflicting capitals must retain history");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void unloadedCapitalIsUnresolvedWithoutLoadingItsChunk(GameTestHelper helper) throws Exception {
		Fixture f = new Fixture(helper);
		f.service.select(f.player, f.pos);
		f.service.found(f.player, "Remote Vale");
		CompoundTag saved = f.data.save(new CompoundTag(), f.level.registryAccess());
		BlockPos remote = new BlockPos(29_000_000, 64, 29_000_000);
		saved.getList("Kingdoms", Tag.TAG_COMPOUND).getCompound(0).putLong("Position", remote.asLong());
		KingdomSavedData restored = KingdomSavedData.load(saved, f.level.registryAccess());
		KingdomService service = new KingdomService(f.level.getServer(), restored);
		helper.assertTrue(f.level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null,
				"Remote chunk must initially be unloaded");
		helper.assertTrue(service.resolveCapital(restored.byFounder(f.player.getUUID())) == Kingdom.CapitalStatus.UNRESOLVED,
				"Unloaded capital must be unresolved");
		helper.assertTrue(f.level.getChunkSource().getChunkNow(remote.getX() >> 4, remote.getZ() >> 4) == null,
				"Capital resolution must never load chunks");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void unreadableSchemaDisablesFoundingAndPreservesNbt(GameTestHelper helper) throws Exception {
		Fixture f = new Fixture(helper);
		CompoundTag unknown = new CompoundTag();
		unknown.putInt("Version", 99);
		unknown.putString("History", "preserve me");
		KingdomSavedData restored = KingdomSavedData.load(unknown, f.level.registryAccess());
		KingdomService service = new KingdomService(f.level.getServer(), restored);
		rejected(f, restored, f.obelisk, DATA_UNAVAILABLE, () -> service.select(f.player, f.pos));
		helper.assertTrue(unknown.equals(restored.save(new CompoundTag(), f.level.registryAccess())),
				"Unknown saved data must be preserved verbatim");
		f.service.select(f.player, f.pos);
		f.service.found(f.player, "Invalid Data Vale");
		CompoundTag malformed = f.data.save(new CompoundTag(), f.level.registryAccess());
		malformed.getList("Kingdoms", Tag.TAG_COMPOUND).getCompound(0).putString("Dimension", "Invalid dimension!");
		KingdomSavedData malformedData = KingdomSavedData.load(malformed, f.level.registryAccess());
		KingdomService malformedService = new KingdomService(f.level.getServer(), malformedData);
		rejected(f, malformedData, f.obelisk, DATA_UNAVAILABLE, () -> malformedService.select(f.anotherPlayer(), f.pos));
		helper.assertTrue(malformed.equals(malformedData.save(new CompoundTag(), f.level.registryAccess())),
				"Malformed dimension must preserve history and disable founding rather than create an empty registry");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void commandWorksWithoutOpAndUsesLocalizedPrivateFeedback(GameTestHelper helper) throws Exception {
		helper.setBlock(POS, ModBlocks.KINGDOM_OBELISK);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(POS);
		RecordingPlayer player = new RecordingPlayer(level, pos);
		Feedback feedback = new Feedback();
		CommandSourceStack source = player.createCommandSourceStack().withPermission(0).withSource(feedback);
		var dispatcher = level.getServer().getCommands().getDispatcher();
		KingdomService service = KingdomService.get(level.getServer());
		helper.assertTrue(dispatcher.execute("reigns", source) == 1 && hasKey(feedback.messages.getLast(),
				"message.regnum-nations.founding.help"), "Ordinary players must discover localized help");
		helper.assertTrue(dispatcher.execute("reigns found No Selection", source) == 0 && hasKey(feedback.messages.getLast(),
				"message.regnum-nations.founding.no_selection"), "Command must require an explicit selection");
		helper.getBlockState(POS).useWithoutItem(level, player,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		helper.assertTrue(player.interactions.size() == 1 && hasKey(player.interactions.getFirst(),
				"message.regnum-nations.founding.selected"), "Interaction must send one private localized selection prompt");
		ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(player.connection, level.getServer());
		helper.assertTrue(dispatcher.execute("reigns found Disconnected Vale", source) == 0 && hasKey(feedback.messages.getLast(),
				"message.regnum-nations.founding.no_selection"), "Actual disconnect hook must invalidate selection");
		service.select(player, pos);
		player.setPos(Vec3.atCenterOf(pos).add(KingdomService.MAX_DISTANCE + 1, 0, 0));
		CompoundTag before = service.data().save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(dispatcher.execute("reigns found Spoofed Position Vale", source.withPosition(Vec3.atCenterOf(pos))) == 0
				&& hasKey(feedback.messages.getLast(), "message.regnum-nations.founding.too_far"),
				"Command must validate the actual player position, not an execute/source position");
		helper.assertTrue(before.equals(service.data().save(new CompoundTag(), level.registryAccess())),
				"Rejected command must not change persistent registry state");
		player.setPos(Vec3.atCenterOf(pos).add(0, 0, 2));
		service.select(player, pos);
		String name = "Command Vale " + UUID.randomUUID().toString().substring(0, 8);
		helper.assertTrue(dispatcher.execute("reigns found " + name, source) == 1 && hasKey(feedback.messages.getLast(),
				"message.regnum-nations.founding.success"), "Non-OP founding must support unquoted names with spaces");
		Kingdom kingdom = service.data().byFounder(player.getUUID());
		helper.assertTrue(kingdom != null && kingdom.name().display().equals(name), "Command must use actual server player identity");
		helper.assertTrue(dispatcher.execute("reigns kingdom", source) == 1 && hasKey(feedback.messages.getLast(),
				"message.regnum-nations.capital.present"), "Kingdom command must show localized capital status");
		level.destroyBlock(pos, false);
		helper.assertTrue(service.data().byFounder(player.getUUID()).capitalStatus() == Kingdom.CapitalStatus.MISSING,
				"Actual block removal hook must mark the capital missing");
		helper.assertTrue(dispatcher.execute("reigns kingdom", source) == 1 && hasKey(feedback.messages.getLast(),
				"message.regnum-nations.capital.missing"), "Destruction must retain a viewable record");
		helper.setBlock(POS, ModBlocks.KINGDOM_OBELISK);
		helper.getBlockState(POS).useWithoutItem(level, player,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		helper.assertTrue(hasKey(player.interactions.getLast(), "message.regnum-nations.founding.already_bound")
				&& !hasKey(player.interactions.getLast(), "message.regnum-nations.kingdom_obelisk.bound"),
				"A replacement at a reserved location must not be described as the original capital");
		KingdomObeliskBlockEntity replacement = helper.getBlockEntity(POS);
		helper.assertTrue(replacement.getKingdomId() == null, "Interaction must not adopt a replacement capital");
		Feedback console = new Feedback();
		helper.assertTrue(dispatcher.execute("reigns found Console Vale", level.getServer().createCommandSourceStack()
				.withSource(console)) == 0 && hasKey(console.messages.getLast(), "message.regnum-nations.founding.invalid_player"),
				"Console founding must be safely rejected with localized feedback");
		helper.assertFalse(feedback.informedAdmins, "Founding messages must not broadcast to administrators");
		helper.succeed();
	}

	private static KingdomObeliskBlockEntity restoreCapital(Fixture f, CompoundTag tag) {
		KingdomObeliskBlockEntity restored = (KingdomObeliskBlockEntity) BlockEntity.loadStatic(
				f.pos, ModBlocks.KINGDOM_OBELISK.defaultBlockState(), tag, f.level.registryAccess());
		f.level.removeBlockEntity(f.pos);
		f.level.setBlockEntity(restored);
		restored.setLevel(f.level);
		return restored;
	}

	private interface Request { void run() throws FoundingException; }

	private static void rejected(Fixture f, FoundingException.Reason expected, Request request) throws Exception {
		rejected(f, f.data, f.obelisk, expected, request);
	}

	private static void rejected(Fixture f, KingdomSavedData data, KingdomObeliskBlockEntity obelisk,
			FoundingException.Reason expected, Request request) throws Exception {
		CompoundTag before = data.save(new CompoundTag(), f.level.registryAccess());
		CompoundTag blockBefore = obelisk.saveWithFullMetadata(f.level.registryAccess());
		data.setDirty(false);
		try {
			request.run();
			f.helper.fail("Expected rejection: " + expected);
		} catch (FoundingException exception) {
			f.helper.assertTrue(exception.reason() == expected, "Expected " + expected + ", got " + exception.reason());
		}
		f.helper.assertTrue(before.equals(data.save(new CompoundTag(), f.level.registryAccess())) && !data.isDirty(),
				"Rejected request must not mutate or dirty persistent registry state");
		f.helper.assertTrue(blockBefore.equals(obelisk.saveWithFullMetadata(f.level.registryAccess())),
				"Rejected request must not mutate persistent obelisk state");
	}

	private static boolean hasKey(Component component, String key) {
		if (component.getContents() instanceof TranslatableContents contents) {
			if (contents.getKey().equals(key)) return true;
			for (Object argument : contents.getArgs()) {
				if (argument instanceof Component child && hasKey(child, key)) return true;
			}
		}
		return component.getSiblings().stream().anyMatch(child -> hasKey(child, key));
	}

	private static final class Fixture {
		final GameTestHelper helper;
		final ServerLevel level;
		final BlockPos pos;
		final KingdomSavedData data = new KingdomSavedData();
		final KingdomService service;
		final ServerPlayer player;
		final KingdomObeliskBlockEntity obelisk;

		Fixture(GameTestHelper helper) {
			this.helper = helper;
			level = helper.getLevel();
			pos = helper.absolutePos(POS);
			helper.setBlock(POS, ModBlocks.KINGDOM_OBELISK);
			obelisk = helper.getBlockEntity(POS);
			service = new KingdomService(level.getServer(), data);
			player = anotherPlayer();
		}

		ServerPlayer anotherPlayer() {
			return new RecordingPlayer(level, pos);
		}
	}

	private static final class RecordingPlayer extends ServerPlayer {
		final List<Component> interactions = new ArrayList<>();

		RecordingPlayer(ServerLevel level, BlockPos pos) {
			super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "FoundingTest"), ClientInformation.createDefault());
			// Exercise real ServerPlayer rules without joining a ticking world or opening a socket.
			connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND),
					this, CommonListenerCookie.createInitial(getGameProfile(), false));
			setPos(Vec3.atCenterOf(pos).add(0, 0, 2));
		}

		@Override
		public void displayClientMessage(Component message, boolean actionBar) {
			interactions.add(message);
		}
	}

	private static final class Feedback implements CommandSource {
		final List<Component> messages = new ArrayList<>();
		boolean informedAdmins;

		@Override public void sendSystemMessage(Component message) { messages.add(message); }
		@Override public boolean acceptsSuccess() { return true; }
		@Override public boolean acceptsFailure() { return true; }
		@Override public boolean shouldInformAdmins() { informedAdmins = true; return false; }
	}
}
