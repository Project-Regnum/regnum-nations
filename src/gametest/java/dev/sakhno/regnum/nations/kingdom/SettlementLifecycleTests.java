package dev.sakhno.regnum.nations.kingdom;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.sakhno.regnum.nations.api.v1.EconomyPermission;
import dev.sakhno.regnum.nations.api.v1.EntityRef;
import dev.sakhno.regnum.nations.api.v1.NationsApi;
import dev.sakhno.regnum.nations.api.v1.NationsEvents;
import dev.sakhno.regnum.nations.block.CityObeliskBlockEntity;
import dev.sakhno.regnum.nations.block.KingdomObeliskBlockEntity;
import dev.sakhno.regnum.nations.block.ModBlocks;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;

public class SettlementLifecycleTests implements FabricGameTest {
    @GameTest(template=EMPTY_STRUCTURE,batch="settlementLifecycle")
    public void directFoundingDissolutionAndExplicitDeletion(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        var server=level.getServer();
        var service=KingdomService.get(server);
        var data=service.data();
        var pos=new BlockPos(1900008,64,1900008);
        var owner=player(level,pos);
        var outsider=player(level,pos);
        level.setBlockAndUpdate(pos,ModBlocks.KINGDOM_OBELISK.defaultBlockState());
        var block=(KingdomObeliskBlockEntity)level.getBlockEntity(pos);
        var obelisk=block.getObeliskId();
        service.select(owner,pos);
        var realm=service.found(owner,obelisk,"Direct Lifecycle",3);
        var id=realm.capitalSettlementId();
        var cityRef=EntityRef.city(id);
        var stateRef=EntityRef.kingdom(realm.id());
        var api=NationsApi.get(server);
        var claim=ClaimPos.at(level.dimension(),pos);
        h.assertTrue(data.ownedSettlements(owner.getUUID()).size()==1
                &&KingdomSavedData.sharesAnchor(data.settlement(id),realm)&&realm.id().equals(block.getKingdomId())
                &&api.resolve(cityRef).valid()&&api.resolve(stateRef).valid()
                &&api.territoryAt(level.dimension(),pos).value().orElseThrow().ref().equals(cityRef),
                "One complete city must own the claim and expose a separate scoped identity to Economy");
        reject(h,ALREADY_FOUNDER,() -> service.found(owner,"Duplicate Lifecycle"));
        reject(h,ALREADY_BOUND,() -> service.select(outsider,pos));
        data.addCitizen(id,outsider.getUUID());
        data.assignRole(id,outsider.getUUID(),CityRole.OFFICER);
        var neighbor=claim.offset(1,0);
        data.putClaim(neighbor,id);
        var admin=server.createCommandSourceStack().withPermission(3).withSuppressedOutput();
        var dispatcher=server.getCommands().getDispatcher();
        String inspect="regnum admin settlement "+id;
        h.assertTrue(dispatcher.execute(inspect,admin)==1,"Administrator can inspect the combined city");
        rejectCommand(h,() -> dispatcher.execute(inspect+" delete",admin.withPermission(2)));
        reject(h,CITY_IS_CAPITAL,() -> service.prepareDeletion(admin,cityRef));
        UUID dissolve=service.requestDeletion(admin,realm.id());
        rejectCommand(h,() -> service.confirmDeletion(admin,cityRef,dissolve));
        service.confirmDeletion(admin,realm.id(),service.requestDeletion(admin,realm.id()));
        data.deleteKingdom(realm.id()); // Internal replay is harmless.

        var view=KingdomManagementServer.state(owner,1,0,id,neighbor,"",false);
        h.assertTrue(view.overview()==null&&view.city().id().equals(id)&&view.city().sovereign()==null
                &&view.selection().settlement().equals(id)&&block.getKingdomId()==null
                &&data.role(id,outsider.getUUID())==CityRole.OFFICER,
                "A dissolved capital must remain manageable with its city, claims and members");
        h.assertTrue(api.resolve(cityRef).valid()&&!api.resolve(stateRef).valid()
                &&api.membership(cityRef,owner.getUUID()).value().orElseThrow().permissions()
                        .contains(EconomyPermission.ECONOMY_VIEW_TREASURY),
                "Dissolution must preserve city treasury authority under its original scoped identity");
        h.assertTrue(dispatcher.execute(inspect+" delete",admin)==1,"City deletion has a registered confirmation command");
        UUID remove=service.prepareDeletion(admin,cityRef).token();
        rejectCommand(h,() -> service.confirmDeletion(admin.withPermission(2),cityRef,remove));
        UUID confirmed=service.prepareDeletion(admin,cityRef).token();
        h.assertTrue(dispatcher.execute(inspect+" delete confirm "+confirmed,admin)==1,"Administrator can confirm the exact city deletion");
        rejectCommand(h,() -> service.confirmDeletion(admin,cityRef,confirmed));
        data.deleteSettlement(id);
        h.assertTrue(data.settlement(id)==null&&data.citizenCity(owner.getUUID())==null
                &&data.citizenCity(outsider.getUUID())==null&&data.claimOwner(claim)==null&&data.claimOwner(neighbor)==null
                &&!api.resolve(cityRef).valid()&&level.getBlockState(pos).is(ModBlocks.KINGDOM_OBELISK),
                "City deletion must retire its authorization, release just its claims and preserve the block");
        service.select(owner,pos);
        var replacement=service.found(owner,"Replacement Lifecycle");
        h.assertTrue(!replacement.capitalSettlementId().equals(id)&&!api.resolve(cityRef).valid(),
                "Refounding must never expose a retired treasury under a replacement entity");
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="settlementLifecycle")
    public void historicalCityPromotionKeepsSeparateAnchorsAcrossReload(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        var data=new KingdomSavedData();
        var service=new KingdomService(level.getServer(),data);
        var cityPos=new BlockPos(1910008,64,1910008);
        var statePos=cityPos.east();
        var owner=player(level,cityPos);
        level.setBlockAndUpdate(cityPos,ModBlocks.KINGDOM_OBELISK.defaultBlockState());
        service.select(owner,cityPos);
        var original=service.found(owner,"Historical Lifecycle");
        var city=data.settlement(original.id());
        var admin=level.getServer().createCommandSourceStack().withPermission(3).withSuppressedOutput();
        service.confirmDeletion(admin,original.id(),service.requestDeletion(admin,original.id()));
        var restored=reload(h,data);
        var afterReload=new KingdomService(level.getServer(),restored);
        level.setBlockAndUpdate(statePos,ModBlocks.KINGDOM_OBELISK.defaultBlockState());
        afterReload.select(owner,statePos);
        var successor=afterReload.found(owner,"Promoted Lifecycle");
        h.assertTrue(successor.capitalSettlementId().equals(city.id())&&!successor.id().equals(city.id())
                &&restored.ownedSettlements(owner.getUUID()).size()==1,
                "Promoting a former combined city must reuse the city and create only the state");
        var promoted=reload(h,restored);
        var promotedService=new KingdomService(level.getServer(),promoted);
        var loadedCity=promoted.settlement(city.id());
        h.assertTrue(loadedCity.obeliskId().equals(city.obeliskId())&&loadedCity.obeliskPos().equals(cityPos)
                &&promotedService.cities().resolve(loadedCity)==Kingdom.CapitalStatus.PRESENT,
                "Reload must preserve the old city anchor and its city management path");
        level.setBlockAndUpdate(statePos,Blocks.AIR.defaultBlockState());
        h.assertTrue(promotedService.resolveCapital(promoted.byId(successor.id()))==Kingdom.CapitalStatus.MISSING
                &&promoted.settlement(city.id()).status()==Kingdom.CapitalStatus.PRESENT,
                "A separate missing state must not change the status of the city anchor");
        promotedService.relocation().recover(admin,successor.id(),true);
        h.assertTrue(promoted.settlement(city.id()).obeliskPos().equals(cityPos)
                &&((KingdomObeliskBlockEntity)level.getBlockEntity(statePos)).getObeliskId().equals(successor.capitalObeliskId()),
                "State recovery must restore only the separate state anchor");
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="settlementLifecycle")
    public void malformedCapitalDissolvesWithoutGuessingOwnership(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        var data=new KingdomSavedData();
        var service=new KingdomService(level.getServer(),data);
        var phantomPos=new BlockPos(1920008,64,1920008);
        var legitimatePos=phantomPos.east(32);
        var owner=player(level,phantomPos);
        var neighbor=player(level,legitimatePos);
        level.setBlockAndUpdate(phantomPos,ModBlocks.KINGDOM_OBELISK.defaultBlockState());
        service.select(owner,phantomPos);
        var realm=service.found(owner,"Malformed Lifecycle");
        var cityOwner=ClaimPos.at(level.dimension(),legitimatePos);
        var orphanedAnchor=ClaimPos.at(level.dimension(),phantomPos);
        level.setBlockAndUpdate(legitimatePos,ModBlocks.CITY_OBELISK.defaultBlockState());
        var block=(CityObeliskBlockEntity)level.getBlockEntity(legitimatePos);
        service.cities().select(neighbor,legitimatePos);
        var valid=service.cities().found(neighbor,block.getObeliskId(),"Valid Lifecycle",5);
        data.affiliate(valid,realm.id());

        var orphanedClaim=save(h,data);
        orphanedClaim.getList("Claims",Tag.TAG_COMPOUND).getCompound(0).putUUID("Settlement",UUID.randomUUID());
        var unreadable=KingdomSavedData.load(orphanedClaim,level.registryAccess());
        h.assertTrue(!unreadable.available()&&orphanedClaim.equals(save(h,unreadable)),
                "A dangling claim must fail closed and preserve its original NBT for review");

        // Simulate a parseable but malformed generated capital with a missing anchor and claim.
        var tag=save(h,data);
        tag.getList("Claims",Tag.TAG_COMPOUND).removeIf(entry -> ((CompoundTag)entry).getUUID("Settlement").equals(realm.id()));
        var loaded=KingdomSavedData.load(tag,level.registryAccess());
        loaded.requireAvailable();
        level.setBlockAndUpdate(phantomPos,Blocks.AIR.defaultBlockState());
        var recovered=new KingdomService(level.getServer(),loaded);
        var admin=level.getServer().createCommandSourceStack().withPermission(3).withSuppressedOutput();
        recovered.confirmDeletion(admin,realm.id(),recovered.requestDeletion(admin,realm.id()));
        h.assertTrue(loaded.claimOwner(orphanedAnchor)==null
                &&recovered.cities().resolve(loaded.settlement(realm.id()))==Kingdom.CapitalStatus.MISSING
                &&loaded.settlement(valid.id()).sovereign()==null&&valid.id().equals(loaded.claimOwner(cityOwner)),
                "Dissolution may detach a suspicious city but must neither invent its claim nor erase legitimate cities");
        var target=EntityRef.city(realm.id());
        recovered.confirmDeletion(admin,target,recovered.prepareDeletion(admin,target).token());
        var reloaded=reload(h,loaded);
        h.assertTrue(reloaded.settlement(realm.id())==null&&reloaded.deletedCityAnchor(realm.id())!=null
                &&reloaded.settlement(valid.id()).ownerId().equals(neighbor.getUUID())
                &&valid.id().equals(reloaded.claimOwner(cityOwner)),
                "Explicit removal must retire only the reviewed record across reload");
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="settlementLifecycle")
    public void nativeCityDeletionReloadAndFoundingFailureAreAtomic(GameTestHelper h) throws Exception {
        var level=h.getLevel();
        var data=new KingdomSavedData();
        var service=new KingdomService(level.getServer(),data);
        var pos=new BlockPos(1930008,64,1930008);
        var owner=player(level,pos);
        var other=player(level,pos.east());
        level.setBlockAndUpdate(pos,ModBlocks.CITY_OBELISK.defaultBlockState());
        var cityBlock=(CityObeliskBlockEntity)level.getBlockEntity(pos);
        service.cities().select(owner,pos);
        var city=service.cities().found(owner,cityBlock.getObeliskId(),"Native Lifecycle",5);
        var statePos=pos.east();
        level.setBlockAndUpdate(statePos,ModBlocks.KINGDOM_OBELISK.defaultBlockState());
        var stateBlock=(KingdomObeliskBlockEntity)level.getBlockEntity(statePos);
        service.select(other,statePos);
        var before=save(h,data);
        reject(h,TERRITORY_OCCUPIED,() -> service.found(other,"Occupied Lifecycle"));
        reject(h,NO_SELECTION,() -> service.found(other,"Occupied Lifecycle"));
        h.assertTrue(before.equals(save(h,data))&&stateBlock.getKingdomId()==null,"Rejected direct founding leaves no records or binding");

        var attempted=new Kingdom(UUID.randomUUID(),new KingdomName("Rollback Lifecycle"),owner.getUUID(),
                level.dimension(),statePos,stateBlock.getObeliskId(),1,Kingdom.CapitalStatus.PRESENT,city.id(),1,"unspecified");
        boolean[] event={false};
        data.onLifecycleChange(change -> event[0]=true);
        try {
            data.elevate(attempted,city,() -> { throw new IllegalStateException("Simulated bind failure"); });
            throw new AssertionError("Expected bind failure");
        } catch (IllegalStateException expected) {
            h.assertTrue(expected.getMessage().equals("Simulated bind failure"),"Unexpected failure");
        }
        h.assertTrue(before.equals(save(h,data))&&!event[0],"Binding failure leaves the registry and lifecycle stream unchanged");
        data.onLifecycleChange(change -> {
            if (change instanceof NationsEvents.EntityChanged entity&&entity.kind()==NationsEvents.Kind.KINGDOM_CREATED)
                h.assertTrue(attempted.id().equals(stateBlock.getKingdomId())&&data.byId(attempted.id())!=null,
                        "Founding events observe both committed data and the block binding");
        });
        data.elevate(attempted,city,() -> stateBlock.bindKingdom(attempted.id()));
        data.deleteKingdom(attempted.id());
        var stale=cityBlock.saveWithFullMetadata(level.registryAccess());
        var admin=level.getServer().createCommandSourceStack().withPermission(3).withSuppressedOutput();
        var ref=EntityRef.city(city.id());
        service.confirmDeletion(admin,ref,service.prepareDeletion(admin,ref).token());
        h.assertTrue(cityBlock.getSettlementId()==null,"Loaded matching block is released");
        var reloaded=reload(h,data);
        var reloadedService=new KingdomService(level.getServer(),reloaded);
        cityBlock.loadWithComponents(stale,level.registryAccess());
        reloadedService.cleanDeletedBinding(level,pos.east(),cityBlock);
        h.assertTrue(city.id().equals(cityBlock.getSettlementId()),"Copied bindings outside the exact retired position are not cleared");
        reloadedService.cleanDeletedBinding(level,pos,cityBlock);
        h.assertTrue(cityBlock.getSettlementId()==null&&reloaded.entityIdUsed(city.id()),"Late chunk saves cannot revive a retired city or treasury UUID");
        h.succeed();
    }

    /** The verify phase starts a new test server on the written, disposable GameTest world. */
    public static class Restart implements FabricGameTest {
        private static final BlockPos ANCHOR=new BlockPos(1940008,64,1940008);
        private static final UUID OWNER=UUID.fromString("61bbed12-23b8-4c26-a378-7ceee70a98ef");
        private static final UUID CITIZEN=UUID.fromString("4e21dc85-7bc0-4fd9-8d65-4055d374df9c");

        @GameTest(template=EMPTY_STRUCTURE,batch="settlementRestart")
        public void combinedCitySurvivesDedicatedRestart(GameTestHelper h) throws Exception {
            var level=h.getLevel();
            var server=level.getServer();
            var service=KingdomService.get(server);
            var data=service.data();
            if (Boolean.getBoolean("regnum.gametest.verifySettlementRestart")) {
                level.getChunkAt(ANCHOR);
                var city=data.byOwner(OWNER);
                h.assertTrue(city!=null&&city.sovereign()==null&&city.obeliskPos().equals(ANCHOR)
                        &&data.byId(city.id())==null&&data.deletedAnchor(city.id())!=null
                        &&city.id().equals(data.claimOwner(ClaimPos.at(level.dimension(),ANCHOR)))
                        &&data.role(city.id(),CITIZEN)==CityRole.OFFICER,
                        "The restarted server must load the same city, tombstone, claim and role from the world");
                h.assertTrue(service.cities().resolve(city)==Kingdom.CapitalStatus.PRESENT
                        &&level.getBlockEntity(ANCHOR) instanceof KingdomObeliskBlockEntity block
                        &&block.getKingdomId()==null&&block.getObeliskId().equals(city.obeliskId()),
                        "The restarted server must resolve the same city obelisk and clear the stale state binding");
            } else {
                level.setBlockAndUpdate(ANCHOR,ModBlocks.KINGDOM_OBELISK.defaultBlockState());
                var owner=player(level,ANCHOR,OWNER);
                service.select(owner,ANCHOR);
                var realm=service.found(owner,"Restart Lifecycle");
                data.addCitizen(realm.capitalSettlementId(),CITIZEN);
                data.assignRole(realm.capitalSettlementId(),CITIZEN,CityRole.OFFICER);
                var admin=server.createCommandSourceStack().withPermission(3).withSuppressedOutput();
                service.confirmDeletion(admin,realm.id(),service.requestDeletion(admin,realm.id()));
                ((KingdomObeliskBlockEntity)level.getBlockEntity(ANCHOR)).bindKingdom(realm.id());
            }
            h.succeed();
        }
    }

    private static CompoundTag save(GameTestHelper h,KingdomSavedData data) {
        return data.save(new CompoundTag(),h.getLevel().registryAccess());
    }
    private static KingdomSavedData reload(GameTestHelper h,KingdomSavedData data) throws FoundingException {
        var reloaded=KingdomSavedData.load(save(h,data),h.getLevel().registryAccess());
        reloaded.requireAvailable();
        return reloaded;
    }
    private static ServerPlayer player(ServerLevel level,BlockPos pos) {
        return player(level,pos,UUID.randomUUID());
    }
    private static ServerPlayer player(ServerLevel level,BlockPos pos,UUID id) {
        var player=new ServerPlayer(level.getServer(),level,new GameProfile(id,"LifecycleTest"),ClientInformation.createDefault());
        player.setPos(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);
        return player;
    }
    private interface Attempt { void run() throws Exception; }
    private static void reject(GameTestHelper h,FoundingException.Reason reason,Attempt attempt) throws Exception {
        try { attempt.run(); }
        catch (FoundingException e) {
            h.assertTrue(e.reason()==reason,"Unexpected rejection "+e.reason());
            return;
        }
        throw new AssertionError("Expected founding rule to reject action: "+reason);
    }
    private static void rejectCommand(GameTestHelper h,Attempt attempt) throws Exception {
        try { attempt.run(); }
        catch (CommandSyntaxException expected) { return; }
        h.assertTrue(false,"Expected command to reject action");
    }
}
