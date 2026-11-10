package dev.sakhno.reigns.kingdom;

import com.mojang.authlib.GameProfile;
import dev.sakhno.reigns.block.KingdomObeliskBlockEntity;
import dev.sakhno.reigns.block.ModBlocks;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static dev.sakhno.reigns.kingdom.FoundingException.Reason.*;

public class KingdomManagementTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, batch = "management")
    public void guiFoundingBindsSelectedIdentityAndRejectsReplay(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1,1,1));
        level.setBlockAndUpdate(pos,ModBlocks.KINGDOM_OBELISK.defaultBlockState());
        var data = new KingdomSavedData();
        var service = new KingdomService(level.getServer(),data);
        var player = player(level,pos);
        var obelisk = (KingdomObeliskBlockEntity)level.getBlockEntity(pos);
        service.select(player,pos);
        reject(h,data,NO_SELECTION,() -> service.found(player,UUID.randomUUID(),"Identity Test"));
        h.assertTrue(obelisk.getKingdomId() == null,"Forged GUI identity must not bind the block");
        service.select(player,pos);
        var kingdom = service.found(player,obelisk.getObeliskId(),"Identity Test");
        reject(h,data,NO_SELECTION,() -> service.found(player,obelisk.getObeliskId(),"Replay Test"));
        h.assertTrue(data.kingdoms().size() == 1 && data.claimCount(kingdom.id()) == 1
                && kingdom.id().equals(obelisk.getKingdomId()),"GUI confirmation must create exactly one kingdom and capital claim");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "management")
    public void selectedActionsRejectForgedPositionKingdomAndDuplicateRequests(GameTestHelper h) throws Exception {
        var data = new KingdomSavedData();
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1,1,1));
        var player = player(level,pos);
        var kingdom = new Kingdom(UUID.randomUUID(),new KingdomName("Selected Test"),player.getUUID(),level.dimension(),pos,
                UUID.randomUUID(),1,Kingdom.CapitalStatus.PRESENT);
        data.add(kingdom);
        var claims = new ClaimService(data);
        var capital = ClaimPos.at(level.dimension(),pos);
        var neighbor = capital.offset(1,0);
        reject(h,data,NOT_IN_CHUNK,() -> claims.manage(player,neighbor,kingdom.id(),false));
        reject(h,data,WRONG_DIMENSION,() -> claims.manage(player,new ClaimPos(Level.NETHER,neighbor.x(),neighbor.z()),kingdom.id(),false));
        reject(h,data,NOT_OWNER,() -> claims.manage(player,capital,UUID.randomUUID(),true));
        reject(h,data,CAPITAL_CHUNK,() -> claims.manage(player,capital,kingdom.id(),true));
        player.setPos(neighbor.x()*16+8,pos.getY(),neighbor.z()*16+8);
        h.assertTrue(KingdomManagementServer.gate(player,100,true) == null,"First action must pass the network gate");
        claims.manage(player,neighbor,kingdom.id(),false);
        reject(h,data,TERRITORY_OCCUPIED,() -> claims.manage(player,neighbor,kingdom.id(),false));
        claims.manage(player,neighbor,kingdom.id(),true);
        CompoundTag before = data.save(new CompoundTag(),level.registryAccess()); data.setDirty(false);
        h.assertTrue("ui.project-reigns.duplicate".equals(KingdomManagementServer.gate(player,100,true))
                && "ui.project-reigns.duplicate".equals(KingdomManagementServer.gate(player,99,true)),
                "Replayed/older requests must still be denied after an inverse action");
        h.assertTrue(before.equals(data.save(new CompoundTag(),level.registryAccess())) && !data.isDirty(),"Duplicate gate must not mutate persistent state");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "management")
    public void publicCapitalInspectionDoesNotGrantBuildRights(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        // Unique remote chunk keeps this test independent of existing persistent test-world records.
        BlockPos pos = new BlockPos(48008 + (Math.abs(UUID.randomUUID().hashCode()%10000)*16),64,48008);
        level.setBlockAndUpdate(pos,ModBlocks.KINGDOM_OBELISK.defaultBlockState());
        var founder = player(level,pos);
        var visitor = player(level,pos);
        var service = KingdomService.get(level.getServer());
        service.select(founder,pos);
        var kingdom = service.found(founder,"Public " + UUID.randomUUID().toString().substring(0,8));
        var hit = new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        h.assertTrue(UseBlockCallback.EVENT.invoker().interact(visitor,level,InteractionHand.MAIN_HAND,hit) == InteractionResult.PASS,
                "A visitor must be allowed to reach the verified capital's public interface");
        h.assertFalse(ClaimService.canBuild(level,visitor,pos),"Public inspection must not grant visitor build permission");
        h.assertTrue(ClaimService.canBuild(level,founder,pos),"Founder must retain existing build permission");
        reject(h,service.data(),NO_KINGDOM,() -> new ClaimService(service.data()).manage(visitor,ClaimPos.at(level.dimension(),pos),kingdom.id(),true));
        visitor.setShiftKeyDown(true);
        h.assertTrue(UseBlockCallback.EVENT.invoker().interact(visitor,level,InteractionHand.MAIN_HAND,hit) == InteractionResult.FAIL,
                "Sneaking use must still follow ordinary protection");
        h.succeed();
    }

    private static ServerPlayer player(ServerLevel level,BlockPos pos) {
        var player = new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"MenuTest"),ClientInformation.createDefault()) {
            @Override public void displayClientMessage(Component message,boolean overlay) { }
        };
        player.setPos(Vec3.atCenterOf(pos)); return player;
    }
    private interface Attempt { void run() throws Exception; }
    private static void reject(GameTestHelper h,KingdomSavedData data,FoundingException.Reason expected,Attempt attempt) throws Exception {
        CompoundTag before = data.save(new CompoundTag(),h.getLevel().registryAccess()); data.setDirty(false);
        try { attempt.run(); h.fail("Expected rejection: " + expected); }
        catch (FoundingException e) { h.assertTrue(e.reason() == expected,"Unexpected failure: " + e.reason()); }
        h.assertTrue(before.equals(data.save(new CompoundTag(),h.getLevel().registryAccess())) && !data.isDirty(),"Rejected request must preserve saved state");
    }
}
