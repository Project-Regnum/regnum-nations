package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.networking.AtlasPayloads;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.DimensionDataStorage;

public final class AtlasWorldTests implements FabricGameTest {
    @GameTest(template=EMPTY_STRUCTURE,batch="atlas")
    public void separateWorldIdentityPersistsWithoutChangingNations(GameTestHelper h) throws Exception {
        var level=h.getLevel(); var server=level.getServer();
        var nations=KingdomService.get(server).data();
        var before=nations.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
        UUID first=AtlasWorldIdentity.get(server);
        h.assertTrue(first.equals(AtlasWorldIdentity.get(server)),"One save must have one cache identity across calls");
        var directory=Files.createTempDirectory("regnum-atlas-identity-");
        try {
            var writer=new DimensionDataStorage(directory.toFile(),server.getFixerUpper(),level.registryAccess());
            var identity=writer.computeIfAbsent(AtlasWorldIdentity.FACTORY,AtlasWorldIdentity.FILE_ID);
            var encoded=identity.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
            writer.save();
            var reader=new DimensionDataStorage(directory.toFile(),server.getFixerUpper(),level.registryAccess());
            h.assertTrue(reader.computeIfAbsent(AtlasWorldIdentity.FACTORY,AtlasWorldIdentity.FILE_ID)
                    .save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()).equals(encoded),"Disk reload must retain the world UUID");
            h.assertTrue(!encoded.getUUID("World").equals(first),"Independent saves must not share cached terrain");
            h.assertTrue(Files.exists(directory.resolve("regnum-nations-atlas-world.dat"))
                    && !Files.exists(directory.resolve("project-reigns-kingdoms.dat")),"Cache identity must use its own file");
            h.assertTrue(before.equals(nations.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess())),"World cache identity must not alter nations schema/data");
        } finally {
            try (var paths=Files.walk(directory)) {
                for (var path:paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="atlas")
    public void additiveMarkerCodecPreservesNegativePositionAndRejectsOversizedInput(GameTestHelper h) {
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            var marker=new AtlasPayloads.CityMarker(UUID.randomUUID(),new BlockPos(-17,123,-1),true);
            var payload=new AtlasPayloads.Markers(91,Level.NETHER.location(),List.of(marker));
            AtlasPayloads.Markers.CODEC.encode(buffer,payload);
            h.assertTrue(payload.equals(AtlasPayloads.Markers.CODEC.decode(buffer)),"Marker coordinates, capital and dimension must round trip exactly");
            buffer.clear(); buffer.writeInt(91); buffer.writeResourceLocation(Level.OVERWORLD.location()); buffer.writeVarInt(Integer.MAX_VALUE);
            boolean rejected=false;
            try { AtlasPayloads.Markers.CODEC.decode(buffer); } catch (IllegalArgumentException expected) { rejected=true; }
            h.assertTrue(rejected,"Oversized marker lists must be rejected before allocation");
        } finally { buffer.release(); }
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="atlas")
    public void dungeonMarkersIncludeFarawayRecordsAcrossBatches(GameTestHelper h) {
        var records = new ListTag();
        for (int i = 0; i < 129; i++) {
            var tag = new CompoundTag();
            tag.putString("Dimension", Level.OVERWORLD.location().toString());
            tag.putString("Type", "mine");
            tag.putString("Name", "Forgotten Mine");
            tag.putIntArray("Entrances", new int[]{-8024 - 48 * i, 72, -9008, -8024 - 48 * i, 65, -8961});
            records.add(tag);
        }
        var otherDimension = new CompoundTag();
        otherDimension.putString("Dimension", Level.NETHER.location().toString());
        otherDimension.putString("Type", "bastion");
        records.add(otherDimension);
        var batches = PoliticalMapServer.dungeonBatches(records, Level.OVERWORLD.location(), 91);
        h.assertTrue(batches.size() == 2 && batches.getFirst().entries().size() == 128
                && !batches.getFirst().last() && batches.getLast().last(), "Large atlases must split into bounded batches");
        var marker = batches.getLast().entries().getFirst();
        h.assertTrue(marker.entrance().equals(new BlockPos(-14168, 72, -9008)), "Retain the exact entrance of a remote dungeon");

        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            for (var batch : batches) {
                AtlasPayloads.DungeonMarkers.CODEC.encode(buffer, batch);
                h.assertTrue(batch.equals(AtlasPayloads.DungeonMarkers.CODEC.decode(buffer)), "Each batch must round trip");
                buffer.clear();
            }
        } finally {
            buffer.release();
        }
        var empty = PoliticalMapServer.dungeonBatches(records, Level.END.location(), 92).getFirst();
        h.assertTrue(empty.last() && empty.entries().isEmpty(), "An empty dimension needs a completed empty snapshot");
        h.succeed();
    }

}
