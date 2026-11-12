package dev.sakhno.regnum.nations.kingdom;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;

/** Versioned fixture copies, real compressed disk I/O, and exports for dedicated-process restarts. */
public final class ReleaseMigrationTests implements FabricGameTest {
    private static CompoundTag fixture(String name) throws Exception {
        try (var input=ReleaseMigrationTests.class.getResourceAsStream("/fixtures/"+name+".snbt")) {
            if (input==null) throw new IllegalStateException("Missing fixture "+name);
            return TagParser.parseTag(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    private static Map<String,CompoundTag> inputs(GameTestHelper h) throws Exception {
        var cases=new LinkedHashMap<String,CompoundTag>();
        var three=fixture("project-reigns-schema3"); cases.put("schema3",three);
        for (int version:new int[]{1,2}) {
            var raw=three.copy(); raw.putInt("Version",version); raw.remove("Settlements"); raw.remove("DeletedStateAnchors");
            var kingdom=raw.getList("Kingdoms",Tag.TAG_COMPOUND).getCompound(0);
            kingdom.remove("CapitalSettlement"); kingdom.remove("Color"); kingdom.remove("Government");
            var claim=new CompoundTag(); claim.putString("Dimension",kingdom.getString("Dimension")); claim.putInt("X",0); claim.putInt("Z",0); claim.putUUID("Kingdom",kingdom.getUUID("Id"));
            var claims=new ListTag(); claims.add(claim); raw.put("Claims",claims); cases.put("schema"+version,raw);
        }
        for (int version:new int[]{4,5}) {
            var raw=fixture("kingdom-diplomacy-schema6"); raw.putInt("Version",version); raw.remove("Diplomacy");
            if (version==4) for (var citizen:raw.getList("Citizens",Tag.TAG_COMPOUND)) ((CompoundTag)citizen).remove("Role");
            cases.put("schema"+version,raw);
        }
        var admin=fixture("kingdom-administration-schema6"); var diplomacy=fixture("kingdom-diplomacy-schema6");
        cases.put("schema6-administration",admin); cases.put("schema6-diplomacy",diplomacy);
        var combined=admin.copy(); combined.put("Diplomacy",diplomacy.get("Diplomacy").copy()); cases.put("schema6-combined",combined);
        var seven=combined.copy(); seven.putInt("Version",7); cases.put("schema7",seven);
        var eight=KingdomSavedData.load(combined,h.getLevel().registryAccess());
        eight.bannerSecret(); eight.setHeraldry(new UUID(0,1),Heraldry.defaults(new UUID(0,977)));
        cases.put("schema8",eight.save(new CompoundTag(),h.getLevel().registryAccess())); return cases;
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="release-hardening")
    public void allSupportedSchemasReachALosslessStableDiskSave(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        var exported=level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolveSibling("release-migrations"); Files.createDirectories(exported);
        for (var entry:inputs(h).entrySet()) {
            var raw=entry.getValue().copy(); raw.putString("AuditExtension","preserve "+entry.getKey());
            var record=raw.getList("Kingdoms",Tag.TAG_COMPOUND).getCompound(0); record.putString("RecordExtension","preserve");
            var untouched=raw.copy(); var data=KingdomSavedData.load(raw,level.registryAccess()); data.requireAvailable();
            var before=data.save(new CompoundTag(),level.registryAccess());
            h.assertTrue(raw.equals(untouched)&&before.getInt("Version")==8&&data.byId(record.getUUID("Id")).capitalObeliskId().equals(record.getUUID("Obelisk")),"Fixture migration retains source, kingdom and obelisk identities: "+entry.getKey());
            var directory=Files.createTempDirectory("regnum-release-migration-");
            try {
                var writer=new DimensionDataStorage(directory.toFile(),level.getServer().getFixerUpper(),level.registryAccess());
                data.setDirty(); writer.set(KingdomSavedData.FILE_ID,data); writer.save();
                var reader=new DimensionDataStorage(directory.toFile(),level.getServer().getFixerUpper(),level.registryAccess());
                var restored=reader.computeIfAbsent(KingdomSavedData.FACTORY,KingdomSavedData.FILE_ID); restored.requireAvailable();
                h.assertTrue(!restored.isDirty()&&before.equals(restored.save(new CompoundTag(),level.registryAccess())),"Every field including citizens, roles, claims, diplomacy, arms, key and extensions survives disk reload: "+entry.getKey());
                // Export both raw upgrade input and expected canonical data, never a player world.
                var root=new CompoundTag(); root.put("data",raw); root.putInt("DataVersion",3955);
                NbtIo.writeCompressed(root,exported.resolve(entry.getKey()+"-input.dat"));
                Files.copy(directory.resolve(KingdomSavedData.FILE_ID+".dat"),exported.resolve(entry.getKey()+"-expected.dat"),StandardCopyOption.REPLACE_EXISTING);
            } finally { try (var files=Files.walk(directory)) { for (var file:files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file); } }
        }
        var multiplayer=new KingdomSavedData();
        UUID editor=UUID.nameUUIDFromBytes("OfflinePlayer:AuditOne".getBytes(StandardCharsets.UTF_8));
        UUID other=UUID.nameUUIDFromBytes("OfflinePlayer:AuditTwo".getBytes(StandardCharsets.UTF_8));
        for (int i=0;i<129;i++) {
            var id=new UUID(10,i+1); var founder=i==0?editor:new UUID(11,i+1);
            multiplayer.add(new Kingdom(id,new KingdomName("Audit Realm "+i),founder,net.minecraft.world.level.Level.OVERWORLD,
                    new net.minecraft.core.BlockPos(1000000+i*32,70,1000000),new UUID(12,i+1),1,Kingdom.CapitalStatus.UNRESOLVED));
        }
        multiplayer.addCitizen(new UUID(10,1),other); multiplayer.setKingdomRole(new UUID(10,1),other,KingdomRole.CHANCELLOR);
        var multiplayerRoot=new CompoundTag(); multiplayerRoot.putInt("DataVersion",3955);
        multiplayerRoot.put("data",multiplayer.save(new CompoundTag(),level.registryAccess()));
        NbtIo.writeCompressed(multiplayerRoot,exported.resolve("multiplayer.dat"));
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="release-hardening")
    public void malformedAndUnknownSchemasCannotBeOverwritten(GameTestHelper h) throws Exception {
        var raw=fixture("kingdom-administration-schema6"); var broken=raw.copy(); broken.getList("Kingdoms",Tag.TAG_COMPOUND).getCompound(0).remove("Ruler");
        var future=raw.copy(); future.putInt("Version",900); var wrongType=raw.copy(); wrongType.putString("Version","8");
        for (var input:List.of(broken,future,wrongType)) {
            var data=KingdomSavedData.load(input,h.getLevel().registryAccess());
            h.assertTrue(!data.available()&&input.equals(data.save(new CompoundTag(),h.getLevel().registryAccess())),"Invalid schema stays fail closed with exact original data");
        } h.succeed();
    }
}
