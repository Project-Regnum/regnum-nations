package dev.sakhno.regnum.nations.kingdom;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.sakhno.regnum.nations.block.*;
import dev.sakhno.regnum.nations.kingdom.CitizenshipTests.Fixture;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.Vec3;
import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;

public class ObeliskRelocationTests implements FabricGameTest {
    private static final AtomicInteger NEXT=new AtomicInteger();

    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void successfulCityMovePreservesIdentityAndConsumesNoItem(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            UUID identity=f.city.obeliskId(); var inventory=f.owner.getInventory().save(new net.minecraft.nbt.ListTag());
            UUID token=f.select();
            h.assertTrue(f.kingdoms.cities().resolve(f.city)==Kingdom.CapitalStatus.PRESENT,"Preview leaves source operational");
            f.moves.commit(f.owner,token);
            var city=f.data.settlement(f.city.id()); var block=(CityObeliskBlockEntity)f.level().getBlockEntity(f.destination);
            h.assertTrue(city.obeliskPos().equals(f.destination)&&city.obeliskId().equals(identity)&&city.id().equals(block.getSettlementId())
                    &&identity.equals(block.getObeliskId())&&f.level().getBlockState(f.pos).isAir(),"Transfer must preserve exact identities and remove source");
            h.assertTrue(f.data.settlements().size()==1&&f.data.kingdoms().isEmpty()&&inventory.equals(f.owner.getInventory().save(new net.minecraft.nbt.ListTag())),"No second city or inventory item");
            h.assertTrue(f.kingdoms.cities().resolve(city)==Kingdom.CapitalStatus.PRESENT,"Destination is operational");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void outsiderCitizenOfficerAndCreativeCannotRelocate(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            var citizen=f.player(); f.base.join(f.owner,citizen,f.city); f.base.citizens.assignRole(f.owner,citizen.getUUID(),CityRole.OFFICER);
            for (var actor:new ServerPlayer[]{f.player(),citizen}) {
                actor.setPos(Vec3.atCenterOf(f.destination));
                reject(f,NOT_OWNER,() -> f.moves.select(actor,f.city.id(),false,f.level().dimension(),f.destination));
            }
            var creative=f.player(); changeMode(creative,GameType.CREATIVE); creative.setPos(Vec3.atCenterOf(f.destination));
            reject(f,NOT_OWNER,() -> f.moves.select(creative,f.city.id(),false,f.level().dimension(),f.destination));
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void invalidDestinationAndChangedDestinationPreserveOriginal(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            f.level().setBlockAndUpdate(f.destination,Blocks.STONE.defaultBlockState());
            reject(f,RELOCATION_CONFLICT,f::select);
            f.level().setBlockAndUpdate(f.destination,Blocks.AIR.defaultBlockState()); UUID token=f.select();
            f.level().setBlockAndUpdate(f.destination,Blocks.STONE.defaultBlockState());
            reject(f,RELOCATION_CONFLICT,() -> f.moves.commit(f.owner,token));
            h.assertTrue(f.kingdoms.cities().resolve(f.city)==Kingdom.CapitalStatus.PRESENT,"Failed commit leaves source operational");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void crossDimensionRequestsAndConfirmationsFail(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            reject(f,WRONG_DIMENSION,() -> f.moves.select(f.owner,f.city.id(),false,Level.NETHER,f.destination));
            UUID token=f.select(); f.owner.setServerLevel(f.level().getServer().getLevel(Level.NETHER));
            reject(f,WRONG_DIMENSION,() -> f.moves.commit(f.owner,token));
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void destinationObelisksAndDuplicateBindingsAreRejected(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            var original=(CityObeliskBlockEntity)f.level().getBlockEntity(f.pos);
            f.level().setBlockAndUpdate(f.destination,ModBlocks.CITY_OBELISK.defaultBlockState());
            var copy=(CityObeliskBlockEntity)f.level().getBlockEntity(f.destination);
            copy.loadWithComponents(original.saveWithoutMetadata(f.level().registryAccess()),f.level().registryAccess());
            reject(f,RELOCATION_CONFLICT,f::select);
            h.assertTrue(f.kingdoms.cities().resolve(f.city)==Kingdom.CapitalStatus.PRESENT,"Copy cannot replace canonical source");
            f.level().setBlockAndUpdate(f.destination,ModBlocks.KINGDOM_OBELISK.defaultBlockState());
            reject(f,RELOCATION_CONFLICT,f::select);
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void citizensRolesAndOutstandingCitizenshipInvitationsSurviveMove(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            var officer=f.player(); var invited=f.player(); f.base.join(f.owner,officer,f.city);
            f.base.citizens.assignRole(f.owner,officer.getUUID(),CityRole.OFFICER); f.base.citizens.invite(officer,invited);
            f.moves.commit(f.owner,f.select()); f.base.citizens.accept(invited,f.city.id());
            h.assertTrue(f.data.role(f.city.id(),officer.getUUID())==CityRole.OFFICER&&f.data.role(f.city.id(),invited.getUUID())==CityRole.CITIZEN
                    &&f.data.role(f.city.id(),f.owner.getUUID())==CityRole.OWNER,"Relocation preserves memberships and pending invitations by city UUID");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void moveToOwnedClaimPreservesAllClaims(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            var extra=ClaimPos.at(f.city.dimension(),f.pos).offset(1,0); f.data.putClaim(extra,f.city.id());
            var before=new HashSet<>(f.data.settlementClaims(f.city.id()));
            f.destination=f.pos.east(16); f.level().getChunkAt(f.destination); f.owner.setPos(Vec3.atCenterOf(f.destination));
            f.moves.commit(f.owner,f.select());
            h.assertTrue(before.equals(new HashSet<>(f.data.settlementClaims(f.city.id())))&&f.data.isCapital(extra),"All claims and destination protection survive");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void affiliationAndPendingStateInvitationSurviveMove(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            var leader=f.player(); Kingdom state=f.base.kingdom(leader); f.data.add(state);
            f.kingdoms.cities().invite(leader,f.owner);
            f.moves.commit(f.owner,f.select()); f.kingdoms.cities().join(f.owner,state.id());
            var affiliated=f.data.settlement(f.city.id());
            h.assertTrue(state.id().equals(affiliated.sovereign()),"Pending affiliation invite survives relocation");
            f.destination=f.pos.east(4); f.owner.setPos(Vec3.atCenterOf(f.destination)); f.moves.commit(f.owner,f.select());
            h.assertTrue(state.id().equals(f.data.settlement(f.city.id()).sovereign())&&f.data.byId(state.id()).equals(state),"Affiliation and kingdom identity remain unchanged");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void nativeStateObeliskMovesWithinCapitalWithoutMovingCity(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            Kingdom state=f.state(); var claims=new HashSet<>(f.data.settlementClaims(f.city.id())); f.destination=f.pos.east(4);
            UUID token=f.moves.select(f.owner,state.id(),true,f.level().dimension(),f.destination); f.moves.commit(f.owner,token);
            var moved=f.data.byId(state.id());
            h.assertTrue(moved.capitalPos().equals(f.destination)&&moved.capitalSettlementId().equals(f.city.id())&&moved.id().equals(state.id())
                    &&moved.capitalObeliskId().equals(state.capitalObeliskId())&&f.data.settlement(f.city.id()).obeliskPos().equals(f.pos),"Only state anchor position changes");
            h.assertTrue(f.kingdoms.resolveCapital(moved)==Kingdom.CapitalStatus.PRESENT&&claims.equals(new HashSet<>(f.data.settlementClaims(f.city.id()))),"State remains operational with original claims");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void legacyCombinedAnchorMovesBothReferences(GameTestHelper h) throws Exception {
        try (var f=new Move(h,false)) {
            f.level().setBlockAndUpdate(f.pos,ModBlocks.KINGDOM_OBELISK.defaultBlockState()); f.kingdoms.select(f.owner,f.pos);
            var state=f.kingdoms.found(f.owner,"Legacy "+UUID.randomUUID().toString().substring(0,8));
            f.moves.commit(f.owner,f.moves.select(f.owner,state.id(),true,f.level().dimension(),f.destination));
            h.assertTrue(f.data.byId(state.id()).capitalPos().equals(f.destination)&&f.data.settlement(state.capitalSettlementId()).obeliskPos().equals(f.destination),"Historical shared anchors move together");
            var reloaded=KingdomSavedData.load(f.saved(),f.level().registryAccess()); reloaded.requireAvailable();
            h.assertTrue(reloaded.settlement(state.capitalSettlementId()).obeliskPos().equals(f.destination),"Legacy reload retains coherent shared position");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void stateAndCapitalCityCannotMoveToAnotherClaim(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            Kingdom state=f.state(); var dest=f.pos.east(16); f.level().getChunkAt(dest); f.data.putClaim(ClaimPos.at(f.city.dimension(),dest),f.city.id()); f.owner.setPos(Vec3.atCenterOf(dest));
            reject(f,RELOCATION_CAPITAL,() -> f.moves.select(f.owner,state.id(),true,f.level().dimension(),dest));
            reject(f,RELOCATION_CAPITAL,() -> f.moves.select(f.owner,f.city.id(),false,f.level().dimension(),dest));
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void activeAnchorsResistSurvivalCreativeExplosionsAndPistons(GameTestHelper h) throws Exception {
        try (var base=new Fixture(h,true)) {
            var owner=base.player(); var level=h.getLevel(); var pos=location(); level.setBlockAndUpdate(pos,ModBlocks.KINGDOM_OBELISK.defaultBlockState()); owner.setPos(Vec3.atCenterOf(pos));
            base.kingdoms.select(owner,pos); var kingdom=base.kingdoms.found(owner,"Protected "+UUID.randomUUID().toString().substring(0,8));
            for (var mode:new GameType[]{GameType.SURVIVAL,GameType.CREATIVE}) {
                changeMode(owner,mode);
                h.assertFalse(PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level,owner,pos,level.getBlockState(pos),level.getBlockEntity(pos)),"Active state mining must reject in "+mode);
                h.assertFalse(owner.gameMode.destroyBlock(pos),"Actual player destruction path must reject");
            }
            var explosion=new Explosion(level,null,pos.getX(),pos.getY(),pos.getZ(),4,false,Explosion.BlockInteraction.DESTROY);
            level.getBlockState(pos).onExplosionHit(level,pos,explosion,(item,dropPos) -> h.fail("Active anchor must not drop"));
            h.assertTrue(level.getBlockState(pos).is(ModBlocks.KINGDOM_OBELISK)&&level.getBlockState(pos).getPistonPushReaction()==PushReaction.BLOCK
                    &&base.kingdoms.resolveCapital(kingdom)==Kingdom.CapitalStatus.PRESENT,"Explosion and piston cannot destroy or move active state");
            h.assertTrue(Block.getDrops(level.getBlockState(pos),level,pos,level.getBlockEntity(pos)).isEmpty(),"Administrative destruction produces no bound item");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void saveToDiskReloadRetainsMoveMembershipRolesAndClaims(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            var citizen=f.player(); f.base.join(f.owner,citizen,f.city); f.base.citizens.assignRole(f.owner,citizen.getUUID(),CityRole.OFFICER);
            Kingdom state=f.state(); f.moves.commit(f.owner,f.select());
            var directory=Files.createTempDirectory("regnum-relocation-"); var storage=new DimensionDataStorage(directory.toFile(),h.getLevel().getServer().getFixerUpper(),h.getLevel().registryAccess());
            storage.set(KingdomSavedData.FILE_ID,f.data); storage.save();
            var disk=new DimensionDataStorage(directory.toFile(),h.getLevel().getServer().getFixerUpper(),h.getLevel().registryAccess());
            var data=disk.computeIfAbsent(KingdomSavedData.FACTORY,KingdomSavedData.FILE_ID); data.requireAvailable();
            var restarted=new KingdomService(h.getLevel().getServer(),data);
            h.assertTrue(data.settlement(f.city.id()).obeliskPos().equals(f.destination)&&data.role(f.city.id(),citizen.getUUID())==CityRole.OFFICER
                    &&data.settlement(f.city.id()).sovereign().equals(state.id())&&data.claimOwner(ClaimPos.at(f.city.dimension(),f.destination)).equals(f.city.id()),"SavedData disk reload preserves every relationship");
            h.assertTrue(restarted.cities().resolve(data.settlement(f.city.id()))==Kingdom.CapitalStatus.PRESENT,"Reload resolves moved anchor");
            reject(f,RELOCATION_EXPIRED,() -> restarted.relocation().commit(f.owner,UUID.randomUUID()));
            var block=f.level().getBlockEntity(f.destination); var tag=block.saveWithoutMetadata(f.level().registryAccess());
            f.level().removeBlockEntity(f.destination); var restored=ModBlocks.CITY_OBELISK.newBlockEntity(f.destination,ModBlocks.CITY_OBELISK.defaultBlockState());
            restored.loadWithComponents(tag,f.level().registryAccess()); f.level().setBlockEntity(restored);
            h.assertTrue(restarted.cities().resolve(data.settlement(f.city.id()))==Kingdom.CapitalStatus.PRESENT,"Recreated chunk block entity agrees with SavedData");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void expiredReplayedForgedAndReconnectedRequestsFail(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            UUID expired=f.select(); f.now.addAndGet(ObeliskRelocationService.REQUEST_NANOS);
            reject(f,RELOCATION_EXPIRED,() -> f.moves.commit(f.owner,expired));
            UUID forged=f.select(); reject(f,RELOCATION_EXPIRED,() -> f.moves.commit(f.owner,UUID.randomUUID()));
            reject(f,RELOCATION_EXPIRED,() -> f.moves.commit(f.owner,forged));
            UUID disconnected=f.select(); f.moves.disconnect(f.owner.getUUID()); reject(f,RELOCATION_EXPIRED,() -> f.moves.commit(f.owner,disconnected));
            UUID moved=f.select(); f.moves.commit(f.owner,moved); reject(f,RELOCATION_EXPIRED,() -> f.moves.commit(f.owner,moved));
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void concurrentSourceAndDestinationReservationsCannotReplaceRequest(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            UUID token=f.select();
            reject(f,RELOCATION_PENDING,f::select);
            var sameOwnerNewSession=f.base.player(f.owner.getUUID()); sameOwnerNewSession.setPos(Vec3.atCenterOf(f.destination));
            reject(f,RELOCATION_PENDING,() -> f.moves.select(sameOwnerNewSession,f.city.id(),false,f.level().dimension(),f.destination));
            f.moves.commit(f.owner,token);
            reject(f,RELOCATION_EXPIRED,() -> f.moves.commit(sameOwnerNewSession,token));
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void cityFoundingStillRequiresUnboundUniqueObelisk(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            h.assertTrue(f.city.id().equals(((CityObeliskBlockEntity)f.level().getBlockEntity(f.pos)).getSettlementId())&&f.data.settlements().size()==1,"Native city founding remains functional");
            f.level().setBlockAndUpdate(f.destination,ModBlocks.CITY_OBELISK.defaultBlockState());
            reject(f,DOMAIN_LIMIT,() -> f.kingdoms.cities().select(f.owner,f.destination));
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void kingdomFoundingStillElevatesCityAndBindsState(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            Kingdom state=f.state();
            h.assertTrue(state.capitalSettlementId().equals(f.city.id())&&f.data.settlements().size()==1&&f.kingdoms.resolveCapital(state)==Kingdom.CapitalStatus.PRESENT,"Existing elevation remains functional");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void adminReplacementPreservesDataAndExplicitRecoveryRestoresMissingEntity(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            var citizen=f.player(); f.base.join(f.owner,citizen,f.city);
            f.level().setBlockAndUpdate(f.pos,Blocks.AIR.defaultBlockState());
            h.assertTrue(f.data.settlement(f.city.id())!=null&&f.data.isCitizen(f.city.id(),citizen.getUUID())&&f.data.settlementClaimCount(f.city.id())==1,"Removal never deletes settlement data");
            f.moves.recover(f.admin(),f.city.id(),false);
            h.assertTrue(f.kingdoms.cities().resolve(f.data.settlement(f.city.id()))==Kingdom.CapitalStatus.PRESENT,"Recovery restores original identity");
            f.level().removeBlockEntity(f.pos); f.moves.recover(f.admin(),f.city.id(),false);
            h.assertTrue(f.kingdoms.cities().resolve(f.data.settlement(f.city.id()))==Kingdom.CapitalStatus.PRESENT,"Missing entity can be explicitly recovered");
            var wrong=(CityObeliskBlockEntity)f.level().getBlockEntity(f.pos); var tag=wrong.saveWithoutMetadata(f.level().registryAccess()); tag.putUUID("SettlementId",UUID.randomUUID()); wrong.loadWithComponents(tag,f.level().registryAccess());
            reject(f,RELOCATION_CONFLICT,() -> f.moves.recover(f.admin(),f.city.id(),false));
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void adminRecoveryRejectsPermissionsOccupiedBlocksAndOwnershipConflicts(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            boolean denied=false;
            try { f.moves.recover(f.owner.createCommandSourceStack().withPermission(0),f.city.id(),false); } catch (CommandSyntaxException expected) { denied=true; }
            h.assertTrue(denied,"Recovery requires administrator level 3");
            f.level().setBlockAndUpdate(f.pos,Blocks.STONE.defaultBlockState());
            reject(f,RELOCATION_CONFLICT,() -> f.moves.recover(f.admin(),f.city.id(),false));
            f.level().setBlockAndUpdate(f.pos,Blocks.AIR.defaultBlockState());
            var other=f.base.city(f.player()); var claim=ClaimPos.at(f.city.dimension(),f.pos); f.data.removeClaim(claim); f.data.putClaim(claim,other.id());
            reject(f,CAPITAL_CONFLICT,() -> f.moves.recover(f.admin(),f.city.id(),false));
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void interruptedChunkSavesRequireCanonicalRecoveryAndCopiesStayInert(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            var oldBlock=f.level().getBlockEntity(f.pos).saveWithoutMetadata(f.level().registryAccess());
            f.moves.commit(f.owner,f.select());
            // Simulate an old source chunk persisted while the destination chunk write was lost.
            f.level().setBlockAndUpdate(f.pos,ModBlocks.CITY_OBELISK.defaultBlockState()); f.level().getBlockEntity(f.pos).loadWithComponents(oldBlock,f.level().registryAccess());
            f.level().setBlockAndUpdate(f.destination,Blocks.AIR.defaultBlockState());
            var restored=KingdomSavedData.load(f.saved(),f.level().registryAccess()); restored.requireAvailable(); var service=new KingdomService(f.level().getServer(),restored);
            h.assertTrue(service.cities().resolve(restored.settlement(f.city.id()))==Kingdom.CapitalStatus.MISSING,"No silent adoption of stale source");
            service.relocation().recover(f.admin(),f.city.id(),false);
            h.assertTrue(service.cities().resolve(restored.settlement(f.city.id()))==Kingdom.CapitalStatus.PRESENT,"Canonical recovery repairs missing destination");
            var outsider=f.player(); outsider.setPos(Vec3.atCenterOf(f.pos));
            boolean rejected=false; try { service.cities().select(outsider,f.pos); } catch (FoundingException expected) { rejected=expected.reason()==ALREADY_BOUND; }
            h.assertTrue(rejected&&restored.settlements().size()==1,"Stale copied source never creates another settlement");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void sourceChangesMissingEntitiesDistanceAndUnownedLandReject(GameTestHelper h) throws Exception {
        try (var f=new Move(h)) {
            f.owner.setPos(Vec3.atCenterOf(f.pos.east(64))); reject(f,TOO_FAR,f::select); f.owner.setPos(Vec3.atCenterOf(f.destination));
            BlockPos unowned=f.pos.east(16); f.level().getChunkAt(unowned); f.owner.setPos(Vec3.atCenterOf(unowned));
            reject(f,RELOCATION_DESTINATION,() -> f.moves.select(f.owner,f.city.id(),false,f.level().dimension(),unowned));
            f.owner.setPos(Vec3.atCenterOf(f.destination)); UUID token=f.select();
            var source=(CityObeliskBlockEntity)f.level().getBlockEntity(f.pos); var tag=source.saveWithoutMetadata(f.level().registryAccess()); tag.putUUID("ObeliskId",UUID.randomUUID()); source.loadWithComponents(tag,f.level().registryAccess());
            reject(f,RELOCATION_CONFLICT,() -> f.moves.commit(f.owner,token));
            f.level().setBlockAndUpdate(f.pos,Blocks.AIR.defaultBlockState()); reject(f,OBELISK_UNAVAILABLE,f::select);
        } h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="relocation")
    public void liveServiceCommitsCityAndStateThroughRemovalCallbacks(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h,true)) {
            var owner=f.player(); var level=h.getLevel(); var pos=location(); owner.setPos(Vec3.atCenterOf(pos));
            level.setBlockAndUpdate(pos,ModBlocks.CITY_OBELISK.defaultBlockState());
            var block=(CityObeliskBlockEntity)level.getBlockEntity(pos); f.kingdoms.cities().select(owner,pos);
            var city=f.kingdoms.cities().found(owner,block.getObeliskId(),"Live Relocation",3);
            var destination=pos.east(2);
            f.kingdoms.relocation().commit(owner,f.kingdoms.relocation().select(owner,city.id(),false,level.dimension(),destination));
            var moved=f.data.settlement(city.id());
            h.assertTrue(moved.obeliskPos().equals(destination)&&f.kingdoms.cities().resolve(moved)==Kingdom.CapitalStatus.PRESENT,"Live callbacks must not mark the moved city missing");
            var statePos=destination.east(); level.setBlockAndUpdate(statePos,ModBlocks.KINGDOM_OBELISK.defaultBlockState()); owner.setPos(Vec3.atCenterOf(statePos));
            f.kingdoms.select(owner,statePos); var state=f.kingdoms.found(owner,"Live "+UUID.randomUUID().toString().substring(0,8));
            var stateDestination=destination.east(3);
            f.kingdoms.relocation().commit(owner,f.kingdoms.relocation().select(owner,state.id(),true,level.dimension(),stateDestination));
            h.assertTrue(f.kingdoms.resolveCapital(f.data.byId(state.id()))==Kingdom.CapitalStatus.PRESENT
                    &&f.data.atCapital(level.dimension(),statePos)==null&&f.data.atCapital(level.dimension(),stateDestination).id().equals(state.id()),"Live state transfer updates capital indexes without removal damage");
        } h.succeed();
    }
    private static void changeMode(ServerPlayer player,GameType mode) {
        // Vanilla broadcasts mode changes to every synthetic player registered by this fixture.
        for (ServerPlayer online:player.serverLevel().getServer().getPlayerList().getPlayers()) connect(online);
        player.gameMode.changeGameModeForPlayer(mode);
    }
    private static void connect(ServerPlayer player) {
        if (player.connection==null) player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(player.serverLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false));
    }
    private static BlockPos location() { return new BlockPos(5_000_008+NEXT.getAndIncrement()*48,64,5_000_008); }
    private interface Attempt { void run() throws Exception; }
    private static void reject(Move f,FoundingException.Reason reason,Attempt attempt) throws Exception {
        var before=f.saved(); f.data.setDirty(false); boolean rejected=false;
        try { attempt.run(); } catch (FoundingException e) { rejected=true; f.h.assertTrue(e.reason()==reason,"Expected "+reason+", got "+e.reason()); }
        f.h.assertTrue(rejected&&before.equals(f.saved())&&!f.data.isDirty(),"Rejected action must preserve SavedData and dirty flag");
    }
    private static final class Move implements AutoCloseable {
        final GameTestHelper h; final Fixture base; final KingdomService kingdoms; final KingdomSavedData data;
        final ServerPlayer owner; final BlockPos pos; BlockPos destination; final Settlement city;
        final AtomicLong now=new AtomicLong(); final ObeliskRelocationService moves;
        Move(GameTestHelper h) throws Exception { this(h,true); }
        Move(GameTestHelper h,boolean found) throws Exception {
            this.h=h; base=new Fixture(h,false); kingdoms=base.kingdoms; data=base.data; owner=base.player(); pos=location(); destination=pos.east(2);
            owner.setPos(Vec3.atCenterOf(pos));
            level().setBlockAndUpdate(pos,ModBlocks.CITY_OBELISK.defaultBlockState()); level().setBlockAndUpdate(destination,Blocks.AIR.defaultBlockState());
            if (found) { kingdoms.cities().select(owner,pos); city=kingdoms.cities().found(owner,((CityObeliskBlockEntity)level().getBlockEntity(pos)).getObeliskId(),"Relocation City",2); }
            else city=null;
            moves=new ObeliskRelocationService(kingdoms,level().getServer(),now::get);
        }
        net.minecraft.server.level.ServerLevel level() { return h.getLevel(); }
        ServerPlayer player() { return base.player(); }
        net.minecraft.commands.CommandSourceStack admin() { return level().getServer().createCommandSourceStack().withPermission(3); }
        UUID select() throws FoundingException { return moves.select(owner,city.id(),false,level().dimension(),destination); }
        CompoundTag saved() { return base.saved(); }
        Kingdom state() throws Exception {
            var statePos=pos.east(); level().setBlockAndUpdate(statePos,ModBlocks.KINGDOM_OBELISK.defaultBlockState());
            owner.setPos(Vec3.atCenterOf(statePos)); kingdoms.select(owner,statePos);
            return kingdoms.found(owner,"State "+UUID.randomUUID().toString().substring(0,8));
        }
        @Override public void close() { base.close(); }
    }
}
