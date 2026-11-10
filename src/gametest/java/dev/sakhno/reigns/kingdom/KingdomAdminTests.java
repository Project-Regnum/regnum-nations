package dev.sakhno.reigns.kingdom;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class KingdomAdminTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, batch = "administration")
    public void renamePreservesIdentityClaimsIndexesAndReload(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var data = new KingdomSavedData();
        var service = new KingdomService(level.getServer(),data);
        var admin = level.getServer().createCommandSourceStack().withPermission(3);
        var first = kingdom("Original Vale",new BlockPos(8,64,8));
        var other = kingdom("Reserved Vale",new BlockPos(88,64,8));
        data.add(first); data.add(other);
        var neighbor = ClaimPos.at(first.dimension(),first.capitalPos()).offset(1,0);
        data.putClaim(neighbor,first.id());
        reject(h,data,() -> service.rename(admin.withPermission(2),first.id(),"Forbidden Vale"));
        reject(h,data,() -> service.rename(admin,first.id(),"  RESERVED   vale  "));
        reject(h,data,() -> service.rename(admin,first.id(),"ab"));
        CompoundTag expected = data.save(new CompoundTag(),level.registryAccess());
        var entries = expected.getList("Kingdoms",Tag.TAG_COMPOUND);
        for (int i=0;i<entries.size();i++) if (entries.getCompound(i).getUUID("Id").equals(first.id())) entries.getCompound(i).putString("Name","Золота Долина");
        var renamed = service.rename(admin,first.id(),"  Золота   Долина  ");
        h.assertTrue(expected.equals(data.save(new CompoundTag(),level.registryAccess())),"Rename may change only the saved name");
        h.assertTrue(renamed.id().equals(first.id()) && service.identify(first.id().toString()).equals(renamed)
                && service.identify("золота долина").equals(renamed),"Exact UUID and normalized full name must identify the renamed kingdom");
        reject(h,data,() -> service.identify("Золота"));
        var restored = KingdomSavedData.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
        h.assertTrue(restored.byFounder(first.founderId()).name().equals(renamed.name())
                && restored.atCapital(first.dimension(),first.capitalPos()).id().equals(first.id())
                && first.id().equals(restored.claimOwner(neighbor)) && restored.claimCount(first.id()) == 2,
                "Reload must preserve renamed founder/capital indexes and UUID-based claims");
        // Old name is released, new name stays reserved after reload.
        restored.add(kingdom("Original Vale",new BlockPos(168,64,8)));
        reject(h,restored,() -> restored.add(kingdom("ЗОЛОТА ДОЛИНА",new BlockPos(248,64,8))));
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "administration")
    public void forcedUnclaimChecksPermissionDimensionOwnerAndEveryCapital(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var data = new KingdomSavedData();
        var admin = level.getServer().createCommandSourceStack().withPermission(3);
        var first = kingdom("Claim Owner",new BlockPos(8,64,8));
        var other = kingdom("Other Capital",new BlockPos(88,64,8));
        data.add(first); data.add(other);
        var capital = ClaimPos.at(first.dimension(),first.capitalPos());
        var neighbor = capital.offset(1,0);
        data.putClaim(neighbor,first.id());
        var claims = new ClaimService(data);
        reject(h,data,() -> claims.forceUnclaim(admin.withPermission(2),first.id(),neighbor));
        reject(h,data,() -> claims.forceUnclaim(admin,UUID.randomUUID(),neighbor));
        var missing = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                dev.sakhno.reigns.ProjectReigns.id("missing_test_world"));
        reject(h,data,() -> claims.forceUnclaim(admin,first.id(),new ClaimPos(missing,neighbor.x(),neighbor.z())));
        reject(h,data,() -> claims.forceUnclaim(admin,other.id(),neighbor));
        reject(h,data,() -> claims.forceUnclaim(admin,first.id(),capital));
        // Simulate a legacy capital overlap while retaining the original claim owner.
        CompoundTag saved = data.save(new CompoundTag(),level.registryAccess());
        var entries = saved.getList("Kingdoms",Tag.TAG_COMPOUND);
        for (int i=0;i<entries.size();i++) if (entries.getCompound(i).getUUID("Id").equals(other.id())) {
            entries.getCompound(i).putLong("Position",new BlockPos(neighbor.x()*16+8,64,neighbor.z()*16+8).asLong());
        }
        var legacy = KingdomSavedData.load(saved,level.registryAccess());
        reject(h,legacy,() -> new ClaimService(legacy).forceUnclaim(admin,first.id(),neighbor));
        claims.forceUnclaim(admin,first.id(),neighbor);
        h.assertTrue(data.claimOwner(neighbor) == null && data.claimCount(first.id()) == 1
                && first.id().equals(data.claimOwner(capital)),"Forced unclaim may remove only the selected non-capital claim");
        reject(h,data,() -> claims.forceUnclaim(admin,first.id(),neighbor));
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "administration")
    public void adminRoutingRejectsLowPermissionAndTeleportFindsOnlySafeGround(GameTestHelper h) throws Exception {
        var server = h.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var service = KingdomService.get(server);
        var source = server.createCommandSourceStack().withSuppressedOutput();
        BlockPos pos = new BlockPos(400008+Math.abs(UUID.randomUUID().hashCode()%10000)*16,64,400008);
        var kingdom = kingdom("Admin " + UUID.randomUUID().toString().substring(0,8),pos);
        service.data().add(kingdom);
        for (String command : new String[]{"help","list","info "+kingdom.id(),"tp "+kingdom.id(),
                "rename "+kingdom.id()+" Denied Name","unclaim "+kingdom.id()+" 0 0"}) {
            reject(h,service.data(),() -> dispatcher.execute("reigns admin "+command,source.withPermission(2)));
        }
        h.assertTrue(dispatcher.execute("reigns admin help",source.withPermission(3)) == 1
                && dispatcher.execute("reigns admin list 1",source.withPermission(3)) == 1,
                "Permission level 3 must have administration access");
        var parsed = dispatcher.parse("reigns admin rename "+kingdom.id()+" Denied Name",source.withPermission(3));
        var revoked = parsed.getContext().withSource(source.withPermission(2)).build(parsed.getReader().getString());
        CompoundTag before = service.data().save(new CompoundTag(),h.getLevel().registryAccess()); service.data().setDirty(false);
        h.assertTrue(revoked.getCommand().run(revoked) == 0 && before.equals(service.data().save(new CompoundTag(),h.getLevel().registryAccess()))
                && !service.data().isDirty(),"A cached command callback must repeat authorization after permission revocation");
        reject(h,service.data(),() -> service.teleportAdministrator(source.withPermission(2),kingdom.id()));
        String newName = "Changed " + UUID.randomUUID().toString().substring(0,8);
        h.assertTrue(dispatcher.execute("reigns admin rename \""+kingdom.name().display()+"\" \""+newName+"\"",source.withPermission(3)) == 1
                && service.data().byId(kingdom.id()).name().display().equals(newName),"Quoted full names must work through authorized rename routing");
        var claim = ClaimPos.at(kingdom.dimension(),pos).offset(1,0); service.data().putClaim(claim,kingdom.id());
        h.assertTrue(dispatcher.execute("reigns admin unclaim "+kingdom.id()+" "+claim.x()+" "+claim.z()+" minecraft:overworld",source.withPermission(3)) == 1
                && service.data().claimOwner(claim) == null,"Dimension-qualified unclaim must route through the protected service");
        var player = new ServerPlayer(server,h.getLevel(),new GameProfile(UUID.randomUUID(),"LandingTest"),ClientInformation.createDefault());
        player.setPos(pos.getX()+0.5,pos.getY()+4,pos.getZ()+0.5);
        for (BlockPos block : BlockPos.betweenClosed(pos.offset(-5,-7,-5),pos.offset(5,9,5))) h.getLevel().setBlockAndUpdate(block,Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        h.assertTrue(pos.above().equals(KingdomService.safeCapitalPosition(h.getLevel(),pos,player)),"Teleport should find supported air above the capital");
        h.getLevel().setBlockAndUpdate(pos,Blocks.MAGMA_BLOCK.defaultBlockState());
        h.assertTrue(KingdomService.safeCapitalPosition(h.getLevel(),pos,player) == null,"Teleport must refuse a hazardous unsupported area without editing it");
        h.succeed();
    }

    private static Kingdom kingdom(String name,BlockPos pos) {
        return new Kingdom(UUID.randomUUID(),new KingdomName(name),UUID.randomUUID(),Level.OVERWORLD,pos,
                UUID.randomUUID(),1,Kingdom.CapitalStatus.PRESENT);
    }
    private interface Attempt { void run() throws Exception; }
    private static void reject(GameTestHelper h,KingdomSavedData data,Attempt attempt) throws Exception {
        CompoundTag before = data.save(new CompoundTag(),h.getLevel().registryAccess()); data.setDirty(false);
        boolean rejected = false;
        try { attempt.run(); } catch (FoundingException | CommandSyntaxException expected) { rejected = true; }
        h.assertTrue(rejected,"Expected administrative operation to reject");
        h.assertTrue(before.equals(data.save(new CompoundTag(),h.getLevel().registryAccess())) && !data.isDirty(),
                "Rejected administrative operation must preserve saved state and dirty flag");
    }
}
