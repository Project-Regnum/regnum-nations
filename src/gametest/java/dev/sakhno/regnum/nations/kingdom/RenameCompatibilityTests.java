package dev.sakhno.regnum.nations.kingdom;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.sakhno.regnum.nations.block.CityObeliskBlockEntity;
import dev.sakhno.regnum.nations.block.KingdomObeliskBlockEntity;
import dev.sakhno.regnum.nations.block.ModBlocks;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.DimensionDataStorage;

/** Literal legacy inputs intentionally do not derive names from current mod constants. */
public final class RenameCompatibilityTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, batch = "rename_compatibility")
    public void oldObeliskAndInventoryNbtResolvesWithoutConversion(GameTestHelper h) throws Exception {
        var registries = h.getLevel().registryAccess();
        for (String name : new String[] {"kingdom_obelisk", "city_obelisk"}) {
            String id = "project-reigns:" + name;
            var block = name.equals("kingdom_obelisk") ? ModBlocks.KINGDOM_OBELISK : ModBlocks.CITY_OBELISK;
            var item = name.equals("kingdom_obelisk") ? ModBlocks.KINGDOM_OBELISK_ITEM : ModBlocks.CITY_OBELISK_ITEM;
            var type = name.equals("kingdom_obelisk") ? ModBlocks.KINGDOM_OBELISK_ENTITY : ModBlocks.CITY_OBELISK_ENTITY;
            h.assertTrue(BuiltInRegistries.BLOCK.getKey(block).toString().equals(id)
                    && BuiltInRegistries.ITEM.getKey(item).toString().equals(id)
                    && BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type).toString().equals(id),
                    "All three persistent registry IDs must remain " + id);
            String binding = name.equals("kingdom_obelisk") ? "KingdomId" : "SettlementId";
            CompoundTag oldNbt = TagParser.parseTag("{id:'" + id
                    + "',ObeliskId:[I;0,0,0,4]," + binding + ":[I;0,0,0,1]}");
            var restored = BlockEntity.loadStatic(BlockPos.ZERO, block.defaultBlockState(), oldNbt, registries);
            h.assertTrue(restored instanceof KingdomObeliskBlockEntity
                    && (name.equals("city_obelisk") == (restored instanceof CityObeliskBlockEntity)),
                    "Legacy block entity NBT must load the correct obelisk class");
            var saved = restored.saveWithFullMetadata(registries);
            h.assertTrue(saved.getString("id").equals(id) && saved.getUUID("ObeliskId").equals(new UUID(0, 4))
                    && saved.getUUID(binding).equals(new UUID(0, 1)), "NBT identity and binding must survive unchanged");
            ItemStack stack = ItemStack.parse(registries,
                    TagParser.parseTag("{id:'" + id + "',count:3}")).orElseThrow();
            h.assertTrue(stack.is(item) && stack.getCount() == 3, "Existing inventory stacks must retain item and count");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rename_compatibility")
    public void oldSavedDataFilenameKeepsCitiesClaimsAndPoliticalIdentity(GameTestHelper h) throws Exception {
        CompoundTag fixture;
        try (var input = getClass().getResourceAsStream("/fixtures/project-reigns-schema3.snbt")) {
            if (input == null) throw new IllegalStateException("Missing handwritten legacy schema fixture");
            fixture = TagParser.parseTag(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        var directory = Files.createTempDirectory("regnum-nations-legacy-save-");
        try {
            CompoundTag root = new CompoundTag();
            root.putInt("DataVersion", 3955);
            root.put("data", fixture);
            NbtIo.writeCompressed(root, directory.resolve("project-reigns-kingdoms.dat"));
            var level = h.getLevel();
            var storage = new DimensionDataStorage(directory.toFile(), level.getServer().getFixerUpper(), level.registryAccess());
            var data = storage.computeIfAbsent(KingdomSavedData.FACTORY, KingdomSavedData.FILE_ID);
            data.requireAvailable();
            h.assertTrue(data.kingdoms().size() == 1 && data.settlements().size() == 2,
                    "The existing filename must load all records instead of creating an empty registry");
            var kingdom = data.byId(new UUID(0, 1));
            var capital = data.settlement(new UUID(0, 3));
            var city = data.settlement(new UUID(0, 6));
            h.assertTrue(kingdom.capitalSettlementId().equals(capital.id()) && kingdom.capitalObeliskId().equals(new UUID(0, 4))
                    && kingdom.founderId().equals(new UUID(0, 2)) && kingdom.name().display().equals("Legacy Kingdom")
                    && kingdom.capitalPos().equals(new BlockPos(1, 64, 0)) && kingdom.createdAt() == 100 && kingdom.color() == 1,
                    "Kingdom identity, capital, founder, history and political color must survive");
            h.assertTrue(capital.ownerId().equals(new UUID(0, 2)) && capital.sovereign().equals(kingdom.id())
                    && capital.obeliskId().equals(new UUID(0, 5)) && capital.color() == 2
                    && city.ownerId().equals(new UUID(0, 7)) && city.sovereign() == null && city.color() == 3,
                    "Affiliated and independent cities must preserve personal ownership and political identity");
            h.assertTrue(data.settlementClaimCount(capital.id()) == 2 && data.settlementClaimCount(city.id()) == 1
                    && data.claimOwner(new ClaimPos(Level.OVERWORLD, 1, 0)).equals(capital.id())
                    && data.claimOwner(new ClaimPos(Level.OVERWORLD, 2, 0)).equals(city.id())
                    && data.deletedAnchor(new UUID(0, 9)).obelisk().equals(new UUID(0, 10)),
                    "Claims and deletion history must survive without reassignment");
            var reloaded = KingdomSavedData.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
            reloaded.requireAvailable();
            h.assertTrue(reloaded.byId(kingdom.id()).equals(kingdom) && reloaded.settlement(capital.id()).equals(capital)
                    && reloaded.settlement(city.id()).equals(city) && !reloaded.isDirty(), "A second save/load must retain every record");
        } finally {
            try (var files = Files.list(directory)) { for (var file : files.toList()) Files.delete(file); }
            Files.delete(directory);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "rename_compatibility")
    public void modIdentityAndCommandAliasKeepPermissions(GameTestHelper h) throws Exception {
        var metadata = FabricLoader.getInstance().getModContainer("regnum-nations").orElseThrow().getMetadata();
        h.assertTrue(metadata.getName().equals("Regnum Nations") && metadata.getProvides().contains("project-reigns"),
                "Renamed mod must expose its identity and legacy dependency alias");
        h.assertTrue(dev.sakhno.regnum.nations.networking.PoliticalMapPayloads.Request.TYPE.id().toString().equals("project-reigns:map_request")
                && dev.sakhno.regnum.nations.networking.PoliticalMapPayloads.Snapshot.TYPE.id().toString().equals("project-reigns:map_snapshot")
                && dev.sakhno.regnum.nations.networking.KingdomManagementPayloads.Found.TYPE.id().toString().equals("project-reigns:kingdom_found")
                && dev.sakhno.regnum.nations.networking.KingdomManagementPayloads.Request.TYPE.id().toString().equals("project-reigns:kingdom_request")
                && dev.sakhno.regnum.nations.networking.KingdomManagementPayloads.State.TYPE.id().toString().equals("project-reigns:kingdom_state"),
                "Existing wire channel IDs must remain stable");
        var dispatcher = h.getLevel().getServer().getCommands().getDispatcher();
        var root = dispatcher.getRoot();
        h.assertTrue(root.getChild("reigns").getRedirect() == root.getChild("nations"),
                "Legacy commands must redirect to the same permission-checked command tree");
        var source = h.getLevel().getServer().createCommandSourceStack().withSuppressedOutput();
        for (String command : new String[] {"nations", "reigns"}) {
            h.assertTrue(dispatcher.execute(command, source.withPermission(0)) == 1,
                    "Both command roots must retain public help");
            h.assertTrue(dispatcher.execute(command + " admin help", source.withPermission(3)) == 1,
                    "Both command roots must retain authorized administration");
            try {
                dispatcher.execute(command + " admin help", source.withPermission(2));
                h.fail("Command alias must not bypass administrator permissions");
            } catch (CommandSyntaxException expected) { }
        }
        h.succeed();
    }
}
