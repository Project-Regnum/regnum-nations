package dev.sakhno.regnum.nations.kingdom;

import com.mojang.authlib.GameProfile;
import dev.sakhno.regnum.nations.networking.KingdomAdministrationPayloads.*;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.DimensionDataStorage;
import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;
import static dev.sakhno.regnum.nations.kingdom.KingdomPermission.*;
import static dev.sakhno.regnum.nations.kingdom.KingdomRole.*;

public class KingdomAdministrationTests implements FabricGameTest {
    private static final AtomicLong LOCATIONS=new AtomicLong(1800000);

    @GameTest(template=EMPTY_STRUCTURE,batch="kingdom_administration")
    public void governmentRolesAndCapabilitiesAreExplicit(GameTestHelper h) throws Exception {
        var f=new Fixture(h); var chancellor=f.member(); var officer=f.member(); var member=f.member();
        f.admin.assignRole(f.ruler,f.realm.id(),chancellor.getUUID(),CHANCELLOR);
        f.admin.assignRole(f.ruler,f.realm.id(),officer.getUUID(),OFFICER);
        for (KingdomPermission permission:KingdomPermission.values()) {
            h.assertTrue(KingdomPermissions.allows(f.data,f.realm.id(),f.ruler.getUUID(),permission),"Ruler has every political permission");
            h.assertTrue(KingdomPermissions.allows(f.data,f.realm.id(),chancellor.getUUID(),permission),"Chancellor has every administrative permission");
            h.assertTrue(KingdomPermissions.allows(f.data,f.realm.id(),officer.getUUID(),permission)==(permission==INVITE_CITY),"Officer may only invite cities");
            h.assertFalse(KingdomPermissions.allows(f.data,f.realm.id(),member.getUUID(),permission),"Ordinary members have no administration permissions");
        }
        h.assertFalse(KingdomPermissions.allows(f.data,f.realm.id(),UUID.randomUUID(),INVITE_CITY),"Outsiders have no authority");
        h.assertTrue(f.data.kingdomRole(f.realm.id(),chancellor.getUUID())==CHANCELLOR,"Role assigned by stable UUID");
        f.admin.rename(chancellor,f.realm.id(),"Admin Renamed");
        h.assertTrue(f.data.byId(f.realm.id()).name().display().equals("Admin Renamed"),"MANAGE_KINGDOM permits naming");
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="kingdom_administration")
    public void lowerRanksCannotEscalateOrAlterPeersOrRuler(GameTestHelper h) throws Exception {
        var f=new Fixture(h); var chancellor=f.member(); var peer=f.member(); var officer=f.member(); var member=f.member();
        f.admin.assignRole(f.ruler,f.realm.id(),chancellor.getUUID(),CHANCELLOR); f.admin.assignRole(f.ruler,f.realm.id(),peer.getUUID(),CHANCELLOR);
        f.admin.assignRole(f.ruler,f.realm.id(),officer.getUUID(),OFFICER);
        f.reject(KINGDOM_ROLE_FORBIDDEN,() -> f.admin.assignRole(chancellor,f.realm.id(),member.getUUID(),CHANCELLOR));
        f.reject(KINGDOM_ROLE_FORBIDDEN,() -> f.admin.assignRole(chancellor,f.realm.id(),peer.getUUID(),MEMBER));
        f.reject(KINGDOM_ROLE_FORBIDDEN,() -> f.admin.assignRole(chancellor,f.realm.id(),chancellor.getUUID(),RULER));
        f.reject(KINGDOM_ROLE_FORBIDDEN,() -> f.admin.assignRole(f.ruler,f.realm.id(),f.ruler.getUUID(),MEMBER));
        f.reject(KINGDOM_ROLE_FORBIDDEN,() -> f.admin.assignRole(officer,f.realm.id(),member.getUUID(),OFFICER));
        f.reject(KINGDOM_ROLE_FORBIDDEN,() -> f.admin.assignRole(member,f.realm.id(),member.getUUID(),CHANCELLOR));
        f.admin.assignRole(chancellor,f.realm.id(),member.getUUID(),OFFICER);
        f.admin.assignRole(chancellor,f.realm.id(),member.getUUID(),MEMBER);
        h.assertTrue(f.data.kingdomRole(f.realm.id(),member.getUUID())==MEMBER,"Chancellor may manage lower roles"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="kingdom_administration")
    public void invitationsRequireKingdomAuthorityAndCityOwnerAcceptance(GameTestHelper h) throws Exception {
        var f=new Fixture(h); var mayor=f.player(); var city=f.city(mayor); var municipalOfficer=f.player();
        f.data.addCitizen(city.id(),municipalOfficer.getUUID()); f.data.assignRole(city.id(),municipalOfficer.getUUID(),CityRole.OFFICER);
        var ordinary=f.member();
        f.reject(KINGDOM_ROLE_FORBIDDEN,() -> f.admin.inviteCity(ordinary,f.realm.id(),city.id()));
        var invitation=f.admin.inviteCity(f.ruler,f.realm.id(),city.id());
        f.reject(NOT_OWNER,() -> f.admin.answer(municipalOfficer,invitation.id(),true));
        f.reject(NOT_OWNER,() -> f.admin.answer(f.player(),invitation.id(),false));
        f.admin.answer(mayor,invitation.id(),true);
        h.assertTrue(f.data.settlement(city.id()).sovereign().equals(f.realm.id())&&f.data.kingdomRole(f.realm.id(),municipalOfficer.getUUID())==MEMBER,"City citizenship supplies ordinary political membership only");
        h.assertTrue(f.data.cityInvitations().isEmpty(),"Accepting consumes invitations for the city"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="kingdom_administration")
    public void rejectionCancellationDuplicatesAndRevocationAreSafe(GameTestHelper h) throws Exception {
        var f=new Fixture(h); var mayor=f.player(); var city=f.city(mayor);
        var invitation=f.admin.inviteCity(f.ruler,f.realm.id(),city.id());
        f.reject(KINGDOM_INVITATION_DUPLICATE,() -> f.admin.inviteCity(f.ruler,f.realm.id(),city.id()));
        f.admin.answer(mayor,invitation.id(),false);
        f.reject(INVITATION_REQUIRED,() -> f.admin.answer(mayor,invitation.id(),true));
        var cancelled=f.admin.inviteCity(f.ruler,f.realm.id(),city.id()); f.admin.cancel(f.ruler,cancelled.id());
        f.reject(INVITATION_REQUIRED,() -> f.admin.answer(mayor,cancelled.id(),true));
        var officer=f.member(); f.admin.assignRole(f.ruler,f.realm.id(),officer.getUUID(),OFFICER);
        f.admin.inviteCity(officer,f.realm.id(),city.id()); f.admin.assignRole(f.ruler,f.realm.id(),officer.getUUID(),MEMBER);
        h.assertTrue(f.data.cityInvitations().isEmpty()&&f.data.settlement(city.id()).sovereign()==null,"Revoking invitation authority clears issued pending invitations without affiliation"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="kingdom_administration")
    public void cityCannotJoinMultipleKingdomsAndOldOffersCannotReplay(GameTestHelper h) throws Exception {
        var f=new Fixture(h); var otherRuler=f.player(); var other=f.kingdom(otherRuler); var mayor=f.player(); var city=f.city(mayor);
        var first=f.admin.inviteCity(f.ruler,f.realm.id(),city.id()); var second=f.admin.inviteCity(otherRuler,other.id(),city.id());
        f.admin.answer(mayor,second.id(),true);
        f.reject(INVITATION_REQUIRED,() -> f.admin.answer(mayor,first.id(),true));
        f.reject(ALREADY_AFFILIATED,() -> f.admin.inviteCity(f.ruler,f.realm.id(),city.id()));
        h.assertTrue(f.data.settlement(city.id()).sovereign().equals(other.id()),"Exactly one kingdom can become sovereign"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="kingdom_administration")
    public void leavingAndRemovalPreserveMunicipalStateAndClaims(GameTestHelper h) throws Exception {
        var f=new Fixture(h); var mayor=f.player(); var city=f.city(mayor); var citizen=f.player(); f.data.addCitizen(city.id(),citizen.getUUID()); f.data.assignRole(city.id(),citizen.getUUID(),CityRole.OFFICER);
        var extra=ClaimPos.at(city.dimension(),city.obeliskPos()).offset(1,0); f.data.putClaim(extra,city.id());
        var original=f.data.settlementClaims(city.id()); var invitation=f.admin.inviteCity(f.ruler,f.realm.id(),city.id()); f.admin.answer(mayor,invitation.id(),true);
        f.admin.assignRole(f.ruler,f.realm.id(),citizen.getUUID(),CHANCELLOR);
        f.reject(NOT_OWNER,() -> f.admin.leaveCity(citizen,city.id()));
        f.admin.leaveCity(mayor,city.id()); f.assertCity(city,citizen.getUUID(),original);
        h.assertTrue(f.data.kingdomRole(f.realm.id(),citizen.getUUID())==null,"Leaving drops kingdom powers");
        f.admin.answer(mayor,f.admin.inviteCity(f.ruler,f.realm.id(),city.id()).id(),true);
        h.assertTrue(f.data.kingdomRole(f.realm.id(),citizen.getUUID())==MEMBER,"Rejoining does not resurrect old administrative roles");
        f.admin.removeCity(f.ruler,f.realm.id(),city.id()); f.assertCity(city,citizen.getUUID(),original);
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="kingdom_administration")
    public void cityAndKingdomDeletionCleanReferencesWithoutCorruptingClaims(GameTestHelper h) throws Exception {
        var f=new Fixture(h); var mayor=f.player(); var city=f.city(mayor); f.admin.inviteCity(f.ruler,f.realm.id(),city.id());
        f.data.deleteSettlement(city.id()); h.assertTrue(f.data.cityInvitations().isEmpty(),"Deleted city invitations are removed");
        var survivor=f.city(f.player()); var citizen=f.player(); f.data.addCitizen(survivor.id(),citizen.getUUID()); f.data.assignRole(survivor.id(),citizen.getUUID(),CityRole.OFFICER);
        f.data.affiliate(survivor,f.realm.id()); f.admin.assignRole(f.ruler,f.realm.id(),citizen.getUUID(),OFFICER);
        var other=f.city(f.player()); f.admin.inviteCity(f.ruler,f.realm.id(),other.id());
        var claims=f.data.settlementClaims(survivor.id()); f.data.deleteKingdom(f.realm.id());
        h.assertTrue(f.data.byId(f.realm.id())==null&&f.data.cityInvitations().isEmpty()&&f.data.kingdomMembers(f.realm.id()).isEmpty(),"Kingdom deletion clears government and invitations");
        f.assertCity(survivor,citizen.getUUID(),claims);
        h.assertTrue(f.data.settlement(f.realm.capitalSettlementId()).sovereign()==null,"Capital remains independent after kingdom dissolution");
        f.reload().requireAvailable(); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="kingdom_administration")
    public void capitalAndLastRulerCannotLeaveOrBeRemoved(GameTestHelper h) throws Exception {
        var f=new Fixture(h);
        f.reject(CITY_IS_CAPITAL,() -> f.admin.leaveCity(f.ruler,f.realm.capitalSettlementId()));
        f.reject(CITY_IS_CAPITAL,() -> f.admin.removeCity(f.ruler,f.realm.id(),f.realm.capitalSettlementId()));
        f.reject(CITY_IS_CAPITAL,() -> f.data.deleteSettlement(f.realm.capitalSettlementId()));
        f.reject(KINGDOM_ROLE_FORBIDDEN,() -> f.admin.assignRole(f.ruler,f.realm.id(),f.ruler.getUUID(),MEMBER));
        f.kingdoms.cities().disconnect(f.ruler.getUUID()); f.kingdoms.administrationView().disconnect(f.ruler.getUUID());
        h.assertTrue(f.data.byId(f.realm.id())!=null&&f.data.kingdomRole(f.realm.id(),f.ruler.getUUID())==RULER,"Disconnect never dissolves kingdom or removes the last administrator"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="kingdom_administration")
    public void transferRequiresExplicitConfirmationAndPreservesFounderAndOwnership(GameTestHelper h) throws Exception {
        var f=new Fixture(h); var target=f.member(); var view=f.view(f.ruler,Tab.GOVERNMENT);
        f.uiReject(f.ruler,view,Action.TRANSFER,target.getUUID(),KINGDOM_CONFIRMATION_REQUIRED);
        var prepared=f.action(f.ruler,f.view(f.ruler,Tab.GOVERNMENT),Action.PREPARE_TRANSFER,target.getUUID());
        h.assertTrue(prepared.success()&&target.getUUID().equals(prepared.transferTarget()),"Prepare returns a confirmation bound to target");
        var confirmed=f.action(f.ruler,prepared,Action.TRANSFER,target.getUUID()); h.assertTrue(confirmed.success(),"Explicit confirmation transfers leadership");
        h.assertTrue(f.data.byId(f.realm.id()).rulerId().equals(target.getUUID())&&f.data.byId(f.realm.id()).founderId().equals(f.ruler.getUUID())
                &&f.data.settlement(f.realm.capitalSettlementId()).ownerId().equals(f.ruler.getUUID()),"Historical founder and capital city owner do not transfer");
        h.assertTrue(f.data.kingdomRole(f.realm.id(),f.ruler.getUUID())==CHANCELLOR,"Previous ruler becomes chancellor");
        f.uiReject(f.ruler,prepared,Action.TRANSFER,target.getUUID(),KINGDOM_UI_STALE);
        h.assertTrue(f.reload().byId(f.realm.id()).rulerId().equals(target.getUUID()),"Transferred leadership persists"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="kingdom_administration")
    public void rulerCityAndCitizenshipDeletionRequirePriorTransfer(GameTestHelper h) throws Exception {
        var f=new Fixture(h); var mayor=f.player(); var city=f.city(mayor); var ruler=f.player(); f.data.addCitizen(city.id(),ruler.getUUID());
        f.data.affiliate(city,f.realm.id()); f.admin.transfer(f.ruler,f.realm.id(),ruler.getUUID());
        f.reject(KINGDOM_RULER_MUST_TRANSFER,() -> f.data.removeCitizen(city.id(),ruler.getUUID()));
        f.reject(KINGDOM_RULER_MUST_TRANSFER,() -> f.data.deleteSettlement(city.id()));
        f.reject(KINGDOM_RULER_MUST_TRANSFER,() -> f.admin.leaveCity(mayor,city.id()));
        f.reject(KINGDOM_RULER_MUST_TRANSFER,() -> f.admin.removeCity(f.ruler,f.realm.id(),city.id()));
        f.admin.transfer(ruler,f.realm.id(),f.ruler.getUUID()); f.admin.leaveCity(mayor,city.id());
        h.assertTrue(f.data.byId(f.realm.id()).rulerId().equals(f.ruler.getUUID())&&f.data.settlement(city.id()).sovereign()==null,"Prior transfer permits safe departure"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="kingdom_administration")
    public void offlineRolesInvitationsAndLeadershipSurviveDiskReload(GameTestHelper h) throws Exception {
        var f=new Fixture(h); var officer=f.member(); var target=f.member(); var city=f.city(f.player());
        f.admin.assignRole(f.ruler,f.realm.id(),officer.getUUID(),OFFICER); var invitation=f.admin.inviteCity(officer,f.realm.id(),city.id());
        f.admin.transfer(f.ruler,f.realm.id(),target.getUUID());
        var directory=Files.createTempDirectory("regnum-kingdom-administration-");
        try {
            var level=h.getLevel(); var writer=new DimensionDataStorage(directory.toFile(),level.getServer().getFixerUpper(),level.registryAccess());
            writer.set(KingdomSavedData.FILE_ID,f.data); writer.save();
            var reader=new DimensionDataStorage(directory.toFile(),level.getServer().getFixerUpper(),level.registryAccess());
            var loaded=reader.computeIfAbsent(KingdomSavedData.FACTORY,KingdomSavedData.FILE_ID); loaded.requireAvailable();
            h.assertTrue(loaded.byId(f.realm.id()).rulerId().equals(target.getUUID())&&loaded.kingdomRole(f.realm.id(),officer.getUUID())==OFFICER
                    &&loaded.cityInvitation(invitation.id())!=null&&!loaded.isDirty(),"Offline ruler, roles, and city invitations survive actual saved-data disk reload");
            var fresh=new KingdomService(level.getServer(),loaded); fresh.cities().join(f.player(city.ownerId()),f.realm.id());
            h.assertTrue(loaded.settlement(city.id()).sovereign().equals(f.realm.id()),"Fresh player session can accept invitation after reload");
            h.assertTrue(fresh.administrationView().handle(target,new Request(1,Action.VIEW,Tab.GOVERNMENT,"",0,null,null,null)).entries().stream()
                    .allMatch(e -> !e.online()),"Offline roster works without live player entities");
        } finally { try(var files=Files.list(directory)) { for(var file:files.toList()) Files.delete(file); } Files.delete(directory); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="kingdom_administration")
    public void oldWorldMigrationAndMalformedAdministrationPreserveHistory(GameTestHelper h) throws Exception {
        var f=new Fixture(h); var member=f.member(); f.data.assignRole(f.realm.capitalSettlementId(),member.getUUID(),CityRole.OFFICER);
        var legacy=f.saved(); legacy.putInt("Version",5); legacy.remove("KingdomRoles"); legacy.remove("CityInvitations");
        for (Tag entry:legacy.getList("Kingdoms",Tag.TAG_COMPOUND)) ((CompoundTag)entry).remove("Ruler");
        var migrated=KingdomSavedData.load(legacy,h.getLevel().registryAccess()); migrated.requireAvailable();
        h.assertTrue(migrated.byId(f.realm.id()).rulerId().equals(f.ruler.getUUID())&&migrated.kingdomRole(f.realm.id(),member.getUUID())==MEMBER
                &&migrated.role(f.realm.capitalSettlementId(),member.getUUID())==CityRole.OFFICER&&migrated.isDirty(),"Legacy founder migrates to ruler; city officer gains no kingdom authority");
        var saved=migrated.save(new CompoundTag(),h.getLevel().registryAccess()); var again=KingdomSavedData.load(saved,h.getLevel().registryAccess()); again.requireAvailable();
        h.assertTrue(saved.equals(again.save(new CompoundTag(),h.getLevel().registryAccess()))&&!again.isDirty(),"Schema 7 is idempotent");
        for (int variant=0;variant<5;variant++) {
            var broken=saved.copy();
            if (variant==0) broken.getList("Kingdoms",Tag.TAG_COMPOUND).getCompound(0).remove("Ruler");
            else if (variant==1) broken.putString("KingdomRoles","invalid");
            else if (variant==2) { var entries=new ListTag(); var role=new CompoundTag(); role.putUUID("Kingdom",f.realm.id());role.putUUID("Player",member.getUUID());role.putString("Role","RULER");entries.add(role);broken.put("KingdomRoles",entries); }
            else if (variant==3) broken.putString("CityInvitations","invalid");
            else broken.putInt("Version",99);
            var loaded=KingdomSavedData.load(broken,h.getLevel().registryAccess());
            try { loaded.requireAvailable(); h.fail("Malformed political state must disable mutation"); }
            catch (FoundingException e) { h.assertTrue(e.reason()==DATA_UNAVAILABLE,"Malformed state fails closed"); }
            h.assertTrue(broken.equals(loaded.save(new CompoundTag(),h.getLevel().registryAccess())),"Malformed NBT must survive verbatim");
        }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="kingdom_administration")
    public void missingCitiesKingdomsAndOrphanRolesArePrunedOnLoad(GameTestHelper h) throws Exception {
        var f=new Fixture(h); var mayor=f.player(); var city=f.city(mayor); f.admin.inviteCity(f.ruler,f.realm.id(),city.id());
        var stale=f.saved(); var cities=stale.getList("Settlements",Tag.TAG_COMPOUND); cities.removeIf(t -> ((CompoundTag)t).getUUID("Id").equals(city.id()));
        var claims=stale.getList("Claims",Tag.TAG_COMPOUND); claims.removeIf(t -> ((CompoundTag)t).getUUID("Settlement").equals(city.id()));
        var loaded=KingdomSavedData.load(stale,h.getLevel().registryAccess()); loaded.requireAvailable(); h.assertTrue(loaded.cityInvitations().isEmpty()&&loaded.isDirty(),"Absent city invitation pruned");
        var absent=f.saved(); absent.put("Kingdoms",new ListTag());
        var repaired=KingdomSavedData.load(absent,h.getLevel().registryAccess()); repaired.requireAvailable();
        h.assertTrue(repaired.cityInvitations().isEmpty()&&repaired.settlement(f.realm.capitalSettlementId()).sovereign()==null,"Missing kingdom detaches cities and drops its invitations"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="kingdom_administration")
    public void snapshotPermissionsPaginationAndWireBoundsAreAuthoritative(GameTestHelper h) throws Exception {
        var f=new Fixture(h); var member=f.member(); for(int i=0;i<10;i++) f.member();
        var view=f.view(member,Tab.GOVERNMENT); h.assertTrue(view.entries().size()==6&&view.matches()==12&&view.entries().stream().allMatch(e -> e.actions().isEmpty()),"Members see bounded roster without privileged actions");
        UUID target=view.entries().stream().filter(e -> !e.id().equals(member.getUUID())&&e.role()!=RULER).findFirst().orElseThrow().id();
        f.uiReject(member,view,Action.CHANCELLOR,target,KINGDOM_ROLE_FORBIDDEN);
        var rulerView=f.view(f.ruler,Tab.GOVERNMENT);
        UUID outside=f.data.kingdomMembers(f.realm.id()).stream().filter(id -> rulerView.entries().stream().noneMatch(e -> e.id().equals(id))).findFirst().orElseThrow();
        f.uiReject(f.ruler,rulerView,Action.OFFICER,outside,KINGDOM_UI_STALE);
        var req=new Request(4,Action.VIEW,Tab.GOVERNMENT,"name",Integer.MAX_VALUE,f.realm.id(),target,UUID.randomUUID());
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            Request.CODEC.encode(buffer,req); h.assertTrue(req.equals(Request.CODEC.decode(buffer)),"Request UUID and bounds round-trip"); buffer.clear();
            State.CODEC.encode(buffer,rulerView); h.assertTrue(rulerView.equals(State.CODEC.decode(buffer)),"Snapshot role/actions round-trip");
        } finally { buffer.release(); }
        var capped=f.ui.handle(f.ruler,new Request(5,Action.VIEW,Tab.GOVERNMENT,"",Integer.MAX_VALUE,f.realm.id(),null,null)); h.assertTrue(capped.page()==1&&capped.entries().size()==6,"Hostile page is clamped without overflow"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="kingdom_administration")
    public void staleExpiredDisconnectedAndChangedLeadershipConfirmationsCannotReplay(GameTestHelper h) throws Exception {
        var f=new Fixture(h); var target=f.member();
        var prepared=f.action(f.ruler,f.view(f.ruler,Tab.GOVERNMENT),Action.PREPARE_TRANSFER,target.getUUID()); f.now.addAndGet(KingdomAdministrationView.SESSION_NANOS);
        f.uiReject(f.ruler,prepared,Action.TRANSFER,target.getUUID(),KINGDOM_UI_STALE);
        var disconnected=f.view(f.ruler,Tab.GOVERNMENT); f.ui.disconnect(f.ruler.getUUID()); f.uiReject(f.ruler,disconnected,Action.OFFICER,target.getUUID(),KINGDOM_UI_STALE);
        var old=f.view(f.ruler,Tab.GOVERNMENT); f.uiReject(f.player(f.ruler.getUUID()),old,Action.OFFICER,target.getUUID(),KINGDOM_UI_STALE);
        var pending=f.action(f.ruler,f.view(f.ruler,Tab.GOVERNMENT),Action.PREPARE_TRANSFER,target.getUUID()); f.admin.transfer(f.ruler,f.realm.id(),target.getUUID());
        f.uiReject(f.ruler,pending,Action.TRANSFER,target.getUUID(),KINGDOM_UI_STALE); h.succeed();
    }

    @GameTest(template=EMPTY_STRUCTURE,batch="kingdom_administration")
    public void politicalAndMunicipalRoleChangesNeverGrantEachOthersPermissions(GameTestHelper h) throws Exception {
        var f=new Fixture(h); var official=f.member(); var mayor=f.player(); var city=f.city(mayor); var citizen=f.player();
        f.data.addCitizen(city.id(),citizen.getUUID()); f.data.assignRole(city.id(),citizen.getUUID(),CityRole.OFFICER); f.data.affiliate(city,f.realm.id());
        f.admin.assignRole(f.ruler,f.realm.id(),official.getUUID(),CHANCELLOR);
        for (CityPermission permission:CityPermission.values()) {
            h.assertFalse(CityPermissions.allows(f.data,f.data.settlement(city.id()),official.getUUID(),permission),"Kingdom administration grants no foreign municipal rights");
            h.assertTrue(CityPermissions.allows(f.data,f.data.settlement(city.id()),mayor.getUUID(),permission),"Affiliation retains owner's existing municipal rights");
        }
        h.assertTrue(f.data.kingdomRole(f.realm.id(),citizen.getUUID())==MEMBER,"City officer gains no political role");
        f.admin.assignRole(f.ruler,f.realm.id(),citizen.getUUID(),OFFICER); f.data.assignRole(city.id(),citizen.getUUID(),CityRole.CITIZEN);
        h.assertTrue(f.data.kingdomRole(f.realm.id(),citizen.getUUID())==OFFICER&&!CityPermissions.allows(f.data,city,citizen.getUUID(),CityPermission.INVITE),"Municipal demotion leaves political assignment and municipal restrictions independent");
        f.data.removeCitizen(city.id(),citizen.getUUID());
        h.assertTrue(f.data.kingdomRole(f.realm.id(),citizen.getUUID())==null,"Citizenship departure revokes political membership");
        f.data.addCitizen(city.id(),citizen.getUUID()); h.assertTrue(f.data.kingdomRole(f.realm.id(),citizen.getUUID())==MEMBER,"Citizenship rejoining cannot resurrect authority");
        h.succeed();
    }

    private interface Attempt { void run() throws Exception; }
    private static final class Fixture {
        final GameTestHelper h; final KingdomSavedData data=new KingdomSavedData(); final KingdomService kingdoms;
        final KingdomAdministration admin; final KingdomAdministrationView ui; final AtomicLong now=new AtomicLong(1);
        final ServerPlayer ruler; final Kingdom realm; int sequence;
        Fixture(GameTestHelper h) throws Exception {
            this.h=h; kingdoms=new KingdomService(h.getLevel().getServer(),data); admin=kingdoms.administration(); ui=new KingdomAdministrationView(kingdoms,now::get);
            ruler=player(); realm=kingdom(ruler);
        }
        ServerPlayer player() { return player(UUID.randomUUID()); }
        ServerPlayer player(UUID id) { return new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(id,"AdminTest"),ClientInformation.createDefault()); }
        BlockPos pos() { return new BlockPos((int)LOCATIONS.addAndGet(64),64,1800000); }
        Kingdom kingdom(ServerPlayer owner) throws Exception {
            var kingdom=new Kingdom(UUID.randomUUID(),new KingdomName("Realm "+UUID.randomUUID().toString().substring(0,8)),owner.getUUID(),Level.OVERWORLD,pos(),UUID.randomUUID(),1,Kingdom.CapitalStatus.UNRESOLVED);
            data.add(kingdom); return kingdom;
        }
        Settlement city(ServerPlayer owner) throws Exception {
            var city=new Settlement(UUID.randomUUID(),new KingdomName("City "+UUID.randomUUID().toString().substring(0,8)),owner.getUUID(),owner.getUUID(),Level.OVERWORLD,pos(),UUID.randomUUID(),1,Kingdom.CapitalStatus.UNRESOLVED,null,5,false);
            data.addSettlement(city); return city;
        }
        ServerPlayer member() throws Exception { var player=player(); data.addCitizen(realm.capitalSettlementId(),player.getUUID()); return player; }
        CompoundTag saved() { return data.save(new CompoundTag(),h.getLevel().registryAccess()); }
        KingdomSavedData reload() { return KingdomSavedData.load(saved(),h.getLevel().registryAccess()); }
        void reject(FoundingException.Reason reason,Attempt attempt) throws Exception {
            var before=saved(); data.setDirty(false); boolean rejected=false;
            try { attempt.run(); } catch (FoundingException e) { rejected=true; h.assertTrue(e.reason()==reason,"Expected "+reason+", got "+e.reason()); }
            h.assertTrue(rejected&&before.equals(saved())&&!data.isDirty(),"Rejected action preserves saved state and dirty flag");
        }
        State view(ServerPlayer actor,Tab tab) { return ui.handle(actor,new Request(++sequence,Action.VIEW,tab,"",0,null,null,null)); }
        State action(ServerPlayer actor,State view,Action action,UUID target) { return ui.handle(actor,new Request(++sequence,action,view.tab(),"",view.page(),view.kingdom(),target,view.token())); }
        void uiReject(ServerPlayer actor,State view,Action action,UUID target,FoundingException.Reason reason) {
            var before=saved(); data.setDirty(false); var reply=action(actor,view,action,target);
            h.assertTrue(!reply.success()&&reply.result().equals(new FoundingException(reason).translationKey()),"Expected "+reason+", got "+reply.result());
            h.assertTrue(before.equals(saved())&&!data.isDirty(),"Rejected UI action cannot mutate saved state");
        }
        void assertCity(Settlement original,UUID officer,List<ClaimPos> claims) {
            var current=data.settlement(original.id());
            h.assertTrue(current.equals(original)&&data.role(original.id(),officer)==CityRole.OFFICER
                    &&Set.copyOf(claims).equals(Set.copyOf(data.settlementClaims(original.id()))),"City owner, citizenship, roles, obelisk identity, position and claims remain unchanged");
        }
    }
}
