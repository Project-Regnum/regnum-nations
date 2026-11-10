package dev.sakhno.reigns.kingdom;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.sakhno.reigns.block.*;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import static dev.sakhno.reigns.kingdom.FoundingException.Reason.*;

/** Focused sovereignty, persistence and dissolution regressions. */
public class SovereigntyTests implements FabricGameTest {
    @GameTest(template=EMPTY_STRUCTURE,batch="sovereignty")
    public void legacyMigrationRetainsIdsClaimsConflictsAndHistoricalDomains(GameTestHelper h) throws Exception {
        var data=new KingdomSavedData();
        Kingdom first=kingdom("Migration First",new BlockPos(8,64,8),UUID.randomUUID(),UUID.randomUUID()).withCapitalStatus(Kingdom.CapitalStatus.MISSING);
        Kingdom second=kingdom("Migration Second",new BlockPos(88,64,8),UUID.randomUUID(),UUID.randomUUID());
        Kingdom third=kingdom("Migration Third",new BlockPos(168,64,8),UUID.randomUUID(),UUID.randomUUID());
        data.add(first); data.add(second); data.add(third);
        ClaimPos extra=ClaimPos.at(first.dimension(),first.capitalPos()).offset(0,1); data.putClaim(extra,first.id());
        CompoundTag legacy=data.save(new CompoundTag(),h.getLevel().registryAccess());
        legacy.putInt("Version",2); legacy.remove("Settlements"); legacy.remove("DeletedStateAnchors");
        var records=legacy.getList("Kingdoms",Tag.TAG_COMPOUND);
        for (int i=0;i<records.size();i++) {
            var e=records.getCompound(i); e.remove("Color"); e.remove("Government"); e.remove("CapitalSettlement");
            if (e.getUUID("Id").equals(second.id())) e.putLong("Position",first.capitalPos().east().asLong());
            if (e.getUUID("Id").equals(third.id())) e.putUUID("Founder",first.founderId());
        }
        var claims=legacy.getList("Claims",Tag.TAG_COMPOUND);
        for (int i=0;i<claims.size();i++) { var e=claims.getCompound(i); e.putUUID("Kingdom",e.getUUID("Settlement")); e.remove("Settlement"); }
        var migrated=KingdomSavedData.load(legacy,h.getLevel().registryAccess()); migrated.requireAvailable();
        h.assertTrue(migrated.kingdoms().size()==3&&migrated.settlements().size()==3&&migrated.isDirty(),"Migration must retain every kingdom and create exactly one capital settlement each");
        h.assertTrue(first.id().equals(migrated.claimOwner(extra))&&migrated.claimCount(first.id())==2
                &&first.id().equals(migrated.claimOwner(ClaimPos.at(first.dimension(),first.capitalPos()))),"Migration must preserve claim identifiers and original conflict ownership");
        h.assertTrue(migrated.byId(first.id()).capitalObeliskId().equals(first.capitalObeliskId())
                &&migrated.byId(first.id()).capitalStatus()==Kingdom.CapitalStatus.MISSING
                &&migrated.byId(first.id()).color()==PoliticalColors.fallback(first.id()),"Missing capital history, obelisk UUID and deterministic political color must survive");
        h.assertTrue(migrated.ownedSettlements(first.founderId()).size()==2,"Historical multiple domains must be retained rather than discarded");
        var historical=player(h.getLevel(),first.capitalPos(),first.founderId());
        reject(h,migrated,DOMAIN_REPAIR,() -> new ClaimService(migrated).manage(historical,false));
        CompoundTag saved=migrated.save(new CompoundTag(),h.getLevel().registryAccess());
        h.assertTrue(saved.getInt("Version")==3,"Migrated data must write schema 3");
        var reloaded=KingdomSavedData.load(saved,h.getLevel().registryAccess()); reloaded.requireAvailable();
        h.assertTrue(reloaded.settlements().size()==3&&reloaded.kingdoms().size()==3&&!reloaded.isDirty()
                &&reloaded.save(new CompoundTag(),h.getLevel().registryAccess()).equals(saved),"Schema 3 reload must be idempotent with no duplicate cities or changed history");
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="sovereignty")
    public void oneDomainElevationColorsAndPrivateOwnershipRemainAuthoritative(GameTestHelper h) throws Exception {
        var level=h.getLevel(); var service=KingdomService.get(level.getServer()); var data=service.data();
        BlockPos pos=remote(700008); var owner=player(level,pos,UUID.randomUUID());
        level.setBlockAndUpdate(pos,ModBlocks.CITY_OBELISK.defaultBlockState());
        var block=(CityObeliskBlockEntity)level.getBlockEntity(pos);
        service.cities().select(owner,pos);
        reject(h,data,INVALID_COLOR,() -> service.cities().found(owner,block.getObeliskId(),"Palette City",0xff123456));
        service.cities().select(owner,pos); var city=service.cities().found(owner,block.getObeliskId(),"Palette City",1);
        h.assertTrue(city.sovereign()==null&&data.politicalColor(city)==1&&city.id().equals(data.claimOwner(ClaimPos.at(city.dimension(),pos))),"Independent city-state must own its capital and selected color");
        BlockPos second=pos.east(4); level.setBlockAndUpdate(second,ModBlocks.CITY_OBELISK.defaultBlockState()); owner.setPos(second.getX()+0.5,second.getY()+0.5,second.getZ()+0.5);
        reject(h,data,DOMAIN_LIMIT,() -> service.cities().select(owner,second));
        BlockPos state=pos.east(); level.setBlockAndUpdate(state,ModBlocks.KINGDOM_OBELISK.defaultBlockState()); owner.setPos(state.getX()+0.5,state.getY()+0.5,state.getZ()+0.5);
        var stateBlock=(KingdomObeliskBlockEntity)level.getBlockEntity(state); service.select(owner,state);
        Kingdom kingdom=service.found(owner,stateBlock.getObeliskId(),"Sovereign "+UUID.randomUUID().toString().substring(0,8),4);
        h.assertTrue(kingdom.capitalSettlementId().equals(city.id())&&data.ownedSettlements(owner.getUUID()).size()==1
                &&kingdom.color()==4&&data.politicalColor(data.settlement(city.id()))==4,"Elevation must preserve the domain UUID/slot while adopting the chosen kingdom color");
        BlockPos otherPos=pos.east(64); var other=player(level,otherPos,UUID.randomUUID()); level.setBlockAndUpdate(otherPos,ModBlocks.CITY_OBELISK.defaultBlockState());
        var otherBlock=(CityObeliskBlockEntity)level.getBlockEntity(otherPos); service.cities().select(other,otherPos);
        Settlement subordinate=service.cities().found(other,otherBlock.getObeliskId(),"Private City",6);
        reject(h,data,INVITATION_REQUIRED,() -> service.cities().join(other,kingdom.id()));
        service.cities().invite(owner,other); service.cities().join(other,kingdom.id());
        h.assertTrue(data.settlement(subordinate.id()).sovereign().equals(kingdom.id())&&data.politicalColor(data.settlement(subordinate.id()))==4
                &&data.settlement(subordinate.id()).color()==6,"Affiliation must retain independent color and personal ownership");
        owner.setPos(otherPos.getX()+0.5,otherPos.getY()+0.5,otherPos.getZ()+0.5);
        reject(h,data,NOT_OWNER,() -> new ClaimService(data).manage(owner,ClaimPos.at(level.dimension(),otherPos),subordinate.id(),true));
        h.assertFalse(ClaimService.canBuild(level,owner,otherPos),"A kingdom leader must not inherit a subordinate owner's building rights");
        h.assertTrue(ClaimService.canBuild(level,other,otherPos),"Subordinate owner must retain building rights");
        var restored=KingdomSavedData.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess()); restored.requireAvailable();
        h.assertTrue(restored.byId(kingdom.id()).color()==4&&restored.settlement(subordinate.id()).color()==6
                &&restored.politicalColor(restored.settlement(subordinate.id()))==4,"Chosen city and state colors must persist through reload");
        h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="sovereignty")
    public void confirmedDeletionExpiresCannotReplayAndPreservesAllCitiesAndClaims(GameTestHelper h) throws Exception {
        var level=h.getLevel(); var server=level.getServer(); var data=new KingdomSavedData(); var service=new KingdomService(server,data);
        var admin=server.createCommandSourceStack().withPermission(3).withSuppressedOutput(); BlockPos pos=remote(900008);
        level.setBlockAndUpdate(pos,ModBlocks.KINGDOM_OBELISK.defaultBlockState()); var block=(KingdomObeliskBlockEntity)level.getBlockEntity(pos);
        Kingdom target=kingdom("Deletion Target",pos,UUID.randomUUID(),block.getObeliskId()); data.add(target); block.bindKingdom(target.id());
        Kingdom unrelated=kingdom("Unrelated Kingdom",pos.east(128),UUID.randomUUID(),UUID.randomUUID()); data.add(unrelated);
        UUID privateOwner=UUID.randomUUID(); Settlement privateCity=new Settlement(UUID.randomUUID(),new KingdomName("Retained City"),privateOwner,privateOwner,
                Level.OVERWORLD,pos.east(64),UUID.randomUUID(),1,Kingdom.CapitalStatus.UNRESOLVED,null,7,false);
        data.addSettlement(privateCity); data.affiliate(privateCity,target.id()); var extra=ClaimPos.at(privateCity.dimension(),privateCity.obeliskPos()).offset(1,0); data.putClaim(extra,privateCity.id());
        UUID wrong=service.requestDeletion(admin,target.id());
        reject(h,data,null,() -> service.confirmDeletion(admin,target.id(),UUID.randomUUID()));
        reject(h,data,null,() -> service.confirmDeletion(admin,target.id(),wrong));
        UUID expired=service.requestDeletion(admin,target.id()); var clock=(ServerLevelData)server.overworld().getLevelData(); long before=clock.getGameTime();
        try { clock.setGameTime(before+KingdomService.SELECTION_TICKS); reject(h,data,null,() -> service.confirmDeletion(admin,target.id(),expired)); }
        finally { clock.setGameTime(before); }
        UUID revoked=service.requestDeletion(admin,target.id()); reject(h,data,null,() -> service.confirmDeletion(admin.withPermission(2),target.id(),revoked));
        reject(h,data,null,() -> service.confirmDeletion(admin,target.id(),revoked));
        UUID token=service.requestDeletion(admin,target.id()); CompoundTag savedBefore=data.save(new CompoundTag(),level.registryAccess());
        CompoundTag oldBinding=block.saveWithFullMetadata(level.registryAccess()); service.confirmDeletion(admin,target.id(),token);
        h.assertTrue(data.byId(target.id())==null&&data.byId(unrelated.id()).equals(unrelated)&&data.settlements().size()==3,
                "Deletion must remove only the selected political entity");
        h.assertTrue(data.settlement(target.capitalSettlementId()).sovereign()==null&&data.settlement(privateCity.id()).sovereign()==null
                &&data.settlement(privateCity.id()).ownerId().equals(privateOwner)&&data.politicalColor(data.settlement(privateCity.id()))==7,
                "All surviving cities must become independent without losing owners or their own colors");
        h.assertTrue(data.save(new CompoundTag(),level.registryAccess()).getList("Claims",Tag.TAG_COMPOUND).equals(savedBefore.getList("Claims",Tag.TAG_COMPOUND))
                &&level.getBlockState(pos).is(ModBlocks.KINGDOM_OBELISK)&&block.getKingdomId()==null,"Claims and physical blocks must survive while the exact state binding clears");
        reject(h,data,null,() -> service.confirmDeletion(admin,target.id(),token));
        var restored=KingdomSavedData.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess()); restored.requireAvailable();
        var reloadedService=new KingdomService(server,restored); block.loadWithComponents(oldBinding,level.registryAccess());
        reloadedService.cleanDeletedBinding(level,pos,block);
        h.assertTrue(block.getKingdomId()==null&&restored.claimOwner(extra).equals(privateCity.id()),"Persisted tombstone must clear a stale unloaded binding after registry/chunk reload");
        block.bindKingdom(unrelated.id()); reloadedService.cleanDeletedBinding(level,pos,block);
        h.assertTrue(block.getKingdomId().equals(unrelated.id()),"Tombstone cleanup must never clear an unrelated binding"); block.clearKingdom(unrelated.id());
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="sovereignty")
    public void deletedAffiliationRecoveryPreservesDomainsAndAllowsRefounding(GameTestHelper h) throws Exception {
        var level=h.getLevel(); var service=KingdomService.get(level.getServer()); var data=service.data();
        BlockPos pos=remote(1100008); var owner=player(level,pos,UUID.randomUUID());
        level.setBlockAndUpdate(pos,ModBlocks.CITY_OBELISK.defaultBlockState());
        var cityBlock=(CityObeliskBlockEntity)level.getBlockEntity(pos);
        service.cities().select(owner,pos);
        var city=service.cities().found(owner,cityBlock.getObeliskId(),"Dissolution City",3);
        BlockPos statePos=pos.east(); level.setBlockAndUpdate(statePos,ModBlocks.KINGDOM_OBELISK.defaultBlockState());
        var stateBlock=(KingdomObeliskBlockEntity)level.getBlockEntity(statePos);
        service.select(owner,statePos);
        var kingdom=service.found(owner,stateBlock.getObeliskId(),"Dissolution "+UUID.randomUUID().toString().substring(0,8),4);
        var oldBinding=stateBlock.saveWithFullMetadata(level.registryAccess());
        var capital=ClaimPos.at(city.dimension(),pos); var neighbor=capital.offset(1,0);
        data.putClaim(neighbor,city.id());
        var admin=level.getServer().createCommandSourceStack().withPermission(3).withSuppressedOutput();
        service.confirmDeletion(admin,kingdom.id(),service.requestDeletion(admin,kingdom.id()));
        h.assertTrue(data.byId(kingdom.id())==null&&data.byFounder(owner.getUUID())==null
                &&data.atCapital(kingdom.dimension(),statePos)==null&&data.byOwner(owner.getUUID()).id().equals(city.id())
                &&data.byOwner(owner.getUUID()).sovereign()==null,"Deletion must release state indexes while retaining the owner's independent city");
        var view=KingdomManagementServer.state(owner,1,0,city.id(),neighbor,"",false);
        h.assertTrue(view.ownKingdom()==null&&view.overview()==null&&view.ownSettlement().equals(city.id())
                &&view.city().sovereign()==null&&view.selection().sovereign()==null,
                "Fresh management and territory views must contain no deleted affiliation");
        reject(h,data,DOMAIN_LIMIT,() -> service.cities().select(owner,pos));

        // Reproduce old saved affiliations with an otherwise valid registry and deletion tombstone.
        var stale=data.save(new CompoundTag(),level.registryAccess());
        var cities=stale.getList("Settlements",Tag.TAG_COMPOUND);
        for (int i=0;i<cities.size();i++) if (cities.getCompound(i).getUUID("Id").equals(city.id()))
            cities.getCompound(i).putUUID("Sovereign",kingdom.id());
        var repaired=KingdomSavedData.load(stale,level.registryAccess()); repaired.requireAvailable();
        h.assertTrue(repaired.isDirty()&&repaired.byOwner(owner.getUUID()).sovereign()==null
                &&repaired.settlements().size()==data.settlements().size()
                &&repaired.save(new CompoundTag(),level.registryAccess()).getList("Claims",Tag.TAG_COMPOUND).equals(stale.getList("Claims",Tag.TAG_COMPOUND)),
                "Recovery must clear only stale sovereignty and preserve all settlements and claims");
        var saved=repaired.save(new CompoundTag(),level.registryAccess());
        var reloaded=KingdomSavedData.load(saved,level.registryAccess()); reloaded.requireAvailable();
        h.assertTrue(!reloaded.isDirty()&&saved.equals(reloaded.save(new CompoundTag(),level.registryAccess())),"Repaired schema 3 must reload idempotently");
        owner.setPos(neighbor.x()*16+8,pos.getY(),neighbor.z()*16+8);
        var claims=new ClaimService(reloaded); claims.manage(owner,neighbor,city.id(),true); claims.manage(owner,neighbor,city.id(),false);
        h.assertTrue(city.id().equals(reloaded.claimOwner(neighbor)),"Deleted membership must not block private claim management");
        owner.setPos(statePos.getX()+0.5,statePos.getY()+0.5,statePos.getZ()+0.5);
        stateBlock.loadWithComponents(oldBinding,level.registryAccess());
        var restoredService=new KingdomService(level.getServer(),reloaded);
        restoredService.select(owner,statePos);
        var successor=restoredService.found(owner,stateBlock.getObeliskId(),kingdom.name().display(),5);
        h.assertTrue(successor.capitalSettlementId().equals(city.id())&&reloaded.settlement(city.id()).ownerId().equals(owner.getUUID())
                &&city.id().equals(reloaded.claimOwner(capital)),"Exact stale binding cleanup must permit elevation of the surviving city");

        // Historical combined anchors can recover even when the old deletion wrote no tombstone.
        var legacyData=new KingdomSavedData();
        var legacy=kingdom("Absent Legacy State",pos.north(128),UUID.randomUUID(),UUID.randomUUID()); legacyData.add(legacy);
        var legacySaved=legacyData.save(new CompoundTag(),level.registryAccess()); legacySaved.put("Kingdoms",new net.minecraft.nbt.ListTag());
        var recoveredLegacy=KingdomSavedData.load(legacySaved,level.registryAccess()); recoveredLegacy.requireAvailable();
        h.assertTrue(recoveredLegacy.settlement(legacy.id()).sovereign()==null&&recoveredLegacy.deletedAnchor(legacy.id()).obelisk().equals(legacy.capitalObeliskId())
                &&legacy.id().equals(recoveredLegacy.claimOwner(ClaimPos.at(legacy.dimension(),legacy.capitalPos()))),
                "Missing legacy affiliation must retain its city and claim and recover only its exact historical anchor");
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="sovereignty")
    public void legacyMemberRecoveryDoesNotInventDeletedCapital(GameTestHelper h) throws Exception {
        var data=new KingdomSavedData(); var registries=h.getLevel().registryAccess();
        var former=kingdom("Former Combined State",new BlockPos(8,64,8),UUID.randomUUID(),UUID.randomUUID());
        data.add(former); data.deleteKingdom(former.id());
        UUID owner=UUID.randomUUID();
        var capital=new Settlement(UUID.randomUUID(),new KingdomName("Native Capital"),owner,owner,Level.OVERWORLD,
                new BlockPos(88,64,8),UUID.randomUUID(),1,Kingdom.CapitalStatus.UNRESOLVED,null,3,false);
        data.addSettlement(capital);
        var absent=new Kingdom(UUID.randomUUID(),new KingdomName("Later Sovereign"),owner,capital.dimension(),
                capital.obeliskPos().east(),UUID.randomUUID(),2,Kingdom.CapitalStatus.UNRESOLVED,capital.id(),4,"unspecified");
        data.elevate(absent,capital); data.affiliate(data.settlement(former.id()),absent.id());
        var stale=data.save(new CompoundTag(),registries);
        stale.put("Kingdoms",new net.minecraft.nbt.ListTag()); // Old deletion omitted detachment and the state's tombstone.
        var recovered=KingdomSavedData.load(stale,registries); recovered.requireAvailable();
        h.assertTrue(recovered.isDirty()&&recovered.settlements().size()==2&&recovered.kingdoms().isEmpty()
                &&recovered.byOwner(owner).sovereign()==null&&recovered.byOwner(former.founderId()).sovereign()==null,
                "Both former members must survive independently with their personal ownership");
        h.assertTrue(recovered.deletedAnchor(absent.id())==null&&recovered.deletedAnchor(former.id()).obelisk().equals(former.capitalObeliskId()),
                "A subordinate legacy anchor cannot prove the missing capital; the exact existing tombstone must remain intact");
        var saved=recovered.save(new CompoundTag(),registries);
        h.assertTrue(saved.getList("Claims",Tag.TAG_COMPOUND).equals(stale.getList("Claims",Tag.TAG_COMPOUND)),
                "Recovery must preserve claims for both the native capital and historical member");
        var reloaded=KingdomSavedData.load(saved,registries); reloaded.requireAvailable();
        h.assertTrue(!reloaded.isDirty()&&saved.equals(reloaded.save(new CompoundTag(),registries)),
                "Conservative recovery must remain stable across save and reload");
        h.succeed();
    }
    private static BlockPos remote(int base) { return new BlockPos(base+Math.abs(UUID.randomUUID().hashCode()%10000)*16,64,base); }
    private static Kingdom kingdom(String name,BlockPos pos,UUID founder,UUID obelisk) { return new Kingdom(UUID.randomUUID(),new KingdomName(name),founder,Level.OVERWORLD,pos,obelisk,1,Kingdom.CapitalStatus.PRESENT); }
    private static ServerPlayer player(ServerLevel level,BlockPos pos,UUID id) {
        var player=new ServerPlayer(level.getServer(),level,new GameProfile(id,"SovereignTest"),ClientInformation.createDefault()) {
            @Override public void displayClientMessage(Component message,boolean overlay) { }
        }; player.setPos(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5); return player;
    }
    private interface Attempt { void run() throws Exception; }
    private static void reject(GameTestHelper h,KingdomSavedData data,FoundingException.Reason reason,Attempt attempt) throws Exception {
        CompoundTag before=data.save(new CompoundTag(),h.getLevel().registryAccess()); data.setDirty(false); boolean rejected=false;
        try { attempt.run(); }
        catch (FoundingException e) { rejected=true; if (reason!=null) h.assertTrue(e.reason()==reason,"Wrong rejection: "+e.reason()); }
        catch (CommandSyntaxException e) { rejected=true; h.assertTrue(reason==null,"Unexpected command rejection"); }
        h.assertTrue(rejected&&before.equals(data.save(new CompoundTag(),h.getLevel().registryAccess()))&&!data.isDirty(),"Rejected action must preserve saved state and dirty flag");
    }
}
