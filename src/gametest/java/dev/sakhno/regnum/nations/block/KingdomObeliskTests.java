package dev.sakhno.regnum.nations.block;

import com.mojang.authlib.GameProfile;
import dev.sakhno.regnum.nations.RegnumNations;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class KingdomObeliskTests implements FabricGameTest {
	private static final BlockPos POS = new BlockPos(1, 1, 1);

	@GameTest(template = EMPTY_STRUCTURE)
	public void placementAssignsDistinctPersistentIds(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.KINGDOM_OBELISK);
		helper.setBlock(POS.offset(2, 0, 0), ModBlocks.KINGDOM_OBELISK);
		KingdomObeliskBlockEntity first = helper.getBlockEntity(POS);
		KingdomObeliskBlockEntity second = helper.getBlockEntity(POS.offset(2, 0, 0));
		CompoundTag saved = first.saveWithFullMetadata(helper.getLevel().registryAccess());
		helper.assertTrue(saved.hasUUID("ObeliskId"), "Placement must assign an ID before any interaction");
		helper.assertFalse(first.getObeliskId().equals(second.getObeliskId()), "Placements must have distinct IDs");
		helper.assertTrue(BuiltInRegistries.BLOCK.get(RegnumNations.legacyId("kingdom_obelisk"))
				== ModBlocks.KINGDOM_OBELISK, "Block must be registered under the mod namespace");
		helper.assertTrue(ModBlocks.KINGDOM_OBELISK_ITEM.getBlock() == ModBlocks.KINGDOM_OBELISK,
				"BlockItem must place the obelisk");
		CreativeModeTabs.tryRebuildTabContents(FeatureFlags.VANILLA_SET, false, helper.getLevel().registryAccess());
		helper.assertTrue(BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.FUNCTIONAL_BLOCKS.location())
				.getDisplayItems().stream().anyMatch(stack -> stack.is(ModBlocks.KINGDOM_OBELISK_ITEM)),
				"Obelisk must be available in the Functional Blocks Creative tab");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void identitySurvivesDiskSerializationAndReattachment(GameTestHelper helper) throws Exception {
		helper.setBlock(POS, ModBlocks.KINGDOM_OBELISK);
		ServerLevel level = helper.getLevel();
		KingdomObeliskBlockEntity original = helper.getBlockEntity(POS);
		UUID expected = original.getObeliskId();
		Path file = Files.createTempFile("regnum-nations-obelisk-", ".nbt");
		CompoundTag saved;
		try {
			NbtIo.write(original.saveWithFullMetadata(level.registryAccess()), file);
			saved = NbtIo.read(file);
		} finally {
			Files.deleteIfExists(file);
		}

		BlockPos absolutePos = helper.absolutePos(POS);
		BlockEntity restored = BlockEntity.loadStatic(absolutePos, original.getBlockState(), saved, level.registryAccess());
		helper.assertTrue(restored instanceof KingdomObeliskBlockEntity, "Saved block entity type must resolve");
		helper.assertTrue(expected.equals(restored.saveWithFullMetadata(level.registryAccess()).getUUID("ObeliskId")),
				"Loading NBT must restore the UUID before the entity joins a world");
		level.removeBlockEntity(absolutePos);
		level.setBlockEntity(restored);
		restored.setLevel(level);
		helper.assertTrue(expected.equals(((KingdomObeliskBlockEntity) restored).getObeliskId()),
				"Reattaching a loaded entity must never replace its identity");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void missingOrMalformedIdentityIsRepairedOnce(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.KINGDOM_OBELISK);
		KingdomObeliskBlockEntity original = helper.getBlockEntity(POS);
		ServerLevel level = helper.getLevel();
		for (int invalidKind = 0; invalidKind < 3; invalidKind++) {
			CompoundTag saved = original.saveWithFullMetadata(level.registryAccess());
			saved.remove("ObeliskId");
			if (invalidKind == 1) {
				saved.putString("ObeliskId", "invalid");
			} else if (invalidKind == 2) {
				saved.putIntArray("ObeliskId", new int[] {1, 2, 3});
			}
			KingdomObeliskBlockEntity restored = (KingdomObeliskBlockEntity) BlockEntity.loadStatic(
					helper.absolutePos(POS), original.getBlockState(), saved, level.registryAccess());
			helper.assertTrue(restored != null, "Malformed identity must not prevent loading the block entity");
			helper.assertFalse(restored.saveWithFullMetadata(level.registryAccess()).hasUUID("ObeliskId"),
					"Detached entities must not invent server identity");
			restored.setLevel(level);
			UUID repaired = restored.getObeliskId();
			restored.setLevel(level);
			helper.assertTrue(repaired.equals(restored.getObeliskId()), "A repaired identity must remain stable");
			helper.assertTrue(repaired.equals(restored.saveWithFullMetadata(level.registryAccess()).getUUID("ObeliskId")),
					"Repaired identities must be saved");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void breakingAndPlacingTheDropCreatesANewIdentity(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.KINGDOM_OBELISK);
		ServerLevel level = helper.getLevel();
		BlockPos absolutePos = helper.absolutePos(POS);
		KingdomObeliskBlockEntity original = helper.getBlockEntity(POS);
		UUID oldId = original.getObeliskId();
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		helper.assertTrue(pickaxe.isCorrectToolForDrops(original.getBlockState()), "Pickaxe tag must be loaded");
		helper.assertFalse(new ItemStack(Items.STICK).isCorrectToolForDrops(original.getBlockState()),
				"Incorrect tools must not qualify for drops");
		List<ItemStack> loot = Block.getDrops(original.getBlockState(), level, absolutePos, original);
		helper.assertTrue(loot.size() == 1 && loot.getFirst().is(ModBlocks.KINGDOM_OBELISK_ITEM),
				"Loot table must resolve to one obelisk item");
		level.destroyBlock(absolutePos, true);
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(absolutePos).inflate(1));
		helper.assertTrue(drops.size() == 1, "Breaking the block must spawn one item drop");
		ItemStack dropped = drops.getFirst().getItem().copy();
		helper.assertFalse(dropped.has(DataComponents.BLOCK_ENTITY_DATA), "Drop must not carry the old identity");
		Player player = new RecordingPlayer(level, helper.absolutePos(BlockPos.ZERO));
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolutePos), Direction.UP, absolutePos, false);
		helper.assertTrue(ModBlocks.KINGDOM_OBELISK_ITEM.place(new BlockPlaceContext(
				level, player, InteractionHand.MAIN_HAND, dropped, hit)).consumesAction(), "Dropped item must place successfully");
		KingdomObeliskBlockEntity replacement = helper.getBlockEntity(POS);
		helper.assertFalse(oldId.equals(replacement.getObeliskId()), "Replacement must receive a fresh identity");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void interactionSendsOneTranslatedMessageOnlyToTheInteractingPlayer(GameTestHelper helper) {
		helper.setBlock(POS, ModBlocks.KINGDOM_OBELISK);
		KingdomObeliskBlockEntity obelisk = helper.getBlockEntity(POS);
		UUID expected = obelisk.getObeliskId();
		RecordingPlayer first = new RecordingPlayer(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		RecordingPlayer second = new RecordingPlayer(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		BlockState state = helper.getBlockState(POS);
		BlockPos absolutePos = helper.absolutePos(POS);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolutePos), Direction.UP, absolutePos, false);
		helper.assertTrue(state.useWithoutItem(helper.getLevel(), first, hit).consumesAction(),
				"Interaction must be consumed to prevent offhand fallback");
		helper.assertTrue(first.messages.size() == 1 && second.messages.isEmpty(),
				"Only the interacting player must receive exactly one message");
		Component message = first.messages.getFirst();
		helper.assertTrue(message.getContents() instanceof TranslatableContents, "Status must use client localization");
		TranslatableContents contents = (TranslatableContents) message.getContents();
		helper.assertTrue(contents.getKey().equals("message.regnum-nations.kingdom_obelisk.status"), "Wrong translation key");
		helper.assertTrue(contents.getArgs().length == 1 && contents.getArgs()[0].equals(expected.toString()),
				"Message must contain the server's persisted UUID");
		state.useWithoutItem(helper.getLevel(), second, hit);
		helper.assertTrue(first.messages.size() == 1 && second.messages.size() == 1, "Messages must not be broadcast");
		helper.assertTrue(expected.equals(obelisk.getObeliskId()), "Interaction must not change identity");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void detachedEntityCannotGenerateIdentity(GameTestHelper helper) {
		KingdomObeliskBlockEntity detached = new KingdomObeliskBlockEntity(
				BlockPos.ZERO, ModBlocks.KINGDOM_OBELISK.defaultBlockState());
		boolean rejected = false;
		try {
			detached.getObeliskId();
		} catch (IllegalStateException expected) {
			rejected = true;
		}
		helper.assertTrue(rejected, "Identity creation requires an attached logical server world");
		helper.succeed();
	}

	private static final class RecordingPlayer extends Player {
		private final List<Component> messages = new ArrayList<>();

		private RecordingPlayer(ServerLevel level, BlockPos pos) {
			super(level, pos, 0, new GameProfile(UUID.randomUUID(), "ObeliskTest"));
		}

		@Override
		public void displayClientMessage(Component message, boolean actionBar) {
			messages.add(message);
		}

		@Override
		public boolean isSpectator() {
			return false;
		}

		@Override
		public boolean isCreative() {
			return false;
		}
	}
}
