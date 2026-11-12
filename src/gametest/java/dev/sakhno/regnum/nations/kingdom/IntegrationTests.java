package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.kingdom.DiplomacyData.*;
import dev.sakhno.regnum.nations.networking.DiplomacyPayloads;
import dev.sakhno.regnum.nations.networking.DiplomacyPayloads.*;
import dev.sakhno.regnum.nations.networking.KingdomAdministrationPayloads;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.DimensionDataStorage;
import static dev.sakhno.regnum.nations.kingdom.KingdomRole.*;

/** Cross-feature contracts; branch-specific fixtures deliberately do not use the v7 writer. */
public final class IntegrationTests implements FabricGameTest {
    private static UUID id(int value) { return new UUID(0,value); }
    private static CompoundTag fixture(String name) throws Exception {
        try (var stream=IntegrationTests.class.getResourceAsStream("/fixtures/"+name+".snbt")) {
            if (stream==null) throw new IllegalStateException("Missing fixture "+name);
            return TagParser.parseTag(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    private static CompoundTag combined() throws Exception {
        var tag=fixture("kingdom-administration-schema6");
        tag.put("Diplomacy",fixture("kingdom-diplomacy-schema6").get("Diplomacy").copy()); return tag;
    }
    private static KingdomSavedData load(GameTestHelper h,CompoundTag tag) throws Exception {
        var data=KingdomSavedData.load(tag,h.getLevel().registryAccess()); data.requireAvailable(); return data;
    }
    private static CompoundTag save(GameTestHelper h,KingdomSavedData data) { return data.save(new CompoundTag(),h.getLevel().registryAccess()); }

    @GameTest(template=EMPTY_STRUCTURE,batch="integration")
    public void administrationSixPreservesTransferredRulerRolesAndCityInvitations(GameTestHelper h) throws Exception {
        var data=load(h,fixture("kingdom-administration-schema6"));
        h.assertTrue(data.byId(id(1)).founderId().equals(id(2))&&data.byId(id(1)).rulerId().equals(id(11)),"Historical founder and transferred ruler remain distinct");
        h.assertTrue(data.kingdomRole(id(1),id(12))==CHANCELLOR&&data.role(id(3),id(11))==CityRole.OFFICER
                &&data.cityInvitation(id(20))!=null&&data.settlementClaimCount(id(3))==2&&data.deletedAnchor(id(9))!=null,"Both role systems, invitations, claims and tombstones survive");
        h.assertTrue(save(h,data).getInt("Version")==KingdomSchema.CURRENT&&data.isDirty()&&data.diplomacy.available(),"Administration schema 6 upgrades to schema 7 with empty diplomacy");
        var historical=fixture("kingdom-administration-schema6");
        historical.getList("Citizens",Tag.TAG_COMPOUND).getCompound(0).putUUID("Settlement",id(16));
        var retained=load(h,historical);
        h.assertTrue(retained.playerKingdom(id(11)).id().equals(id(1))&&retained.role(id(16),id(11))==CityRole.OFFICER,
                "A retained ruler seat takes priority over foreign municipal citizenship without changing municipal roles"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="integration")
    public void diplomacySixPreservesPendingRequestAndMigratesFounderToRuler(GameTestHelper h) throws Exception {
        var data=load(h,fixture("kingdom-diplomacy-schema6")); var entry=data.diplomacy.get(new Pair(id(1),id(15)));
        h.assertTrue(data.byId(id(1)).rulerId().equals(id(2))&&data.kingdomRole(id(1),id(11))==MEMBER,"Diplomacy-only save gains founder ruler without promoting city officers");
        h.assertTrue(entry.revision().equals(id(22))&&entry.invitation().id().equals(id(21))&&entry.invitation().inviter().equals(id(2))
                &&entry.invitation().expiresAt()==10000000000000L,"Legacy invitation identity, revision and deadline survive with founder attribution"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="integration")
    public void bothSixFormatsAndCombinedStateMigrateDeterministicallyAndIdempotently(GameTestHelper h) throws Exception {
        for (var raw:List.of(fixture("project-reigns-schema3"),fixture("kingdom-administration-schema6"),fixture("kingdom-diplomacy-schema6"),combined())) {
            var untouched=raw.copy(); var first=save(h,load(h,raw)); var second=save(h,load(h,raw)); var reloaded=load(h,first);
            h.assertTrue(raw.equals(untouched)&&first.equals(second)&&first.equals(save(h,reloaded))&&!reloaded.isDirty(),"Migration is deterministic, does not mutate input, and reaches a stable v7 save");
        }
        var data=load(h,combined());
        h.assertTrue(data.cityInvitation(id(20))!=null&&data.kingdomRole(id(1),id(12))==CHANCELLOR
                &&data.diplomacy.get(new Pair(id(1),id(15))).invitation().inviter().equals(id(11)),"Combined v6 fields retain both features and current ruler attribution"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="integration")
    public void schemasFourAndFivePreserveMunicipalCitizenshipAndRoles(GameTestHelper h) throws Exception {
        for (int version:List.of(4,5)) {
            var raw=fixture("kingdom-diplomacy-schema6"); raw.putInt("Version",version); raw.remove("Diplomacy");
            if (version==4) for (var item:raw.getList("Citizens",Tag.TAG_COMPOUND)) ((CompoundTag)item).remove("Role");
            var data=load(h,raw);
            h.assertTrue(data.role(id(3),id(11))==(version==4?CityRole.CITIZEN:CityRole.OFFICER)
                    &&data.byId(id(1)).rulerId().equals(id(2))&&data.settlement(id(3)).obeliskId().equals(id(5)),"Legacy roles, founder, and obelisk identity are preserved");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="integration")
    public void optionalCollectionsDefaultEmptyButMalformedPresentCollectionsFailClosed(GameTestHelper h) throws Exception {
        var raw=combined(); raw.remove("KingdomRoles");raw.remove("CityInvitations");raw.remove("Diplomacy");
        var data=load(h,raw); h.assertTrue(data.kingdomRole(id(1),id(12))==MEMBER&&data.cityInvitations().isEmpty()&&data.diplomacy.available(),"Missing optional collections are empty");
        for (String key:List.of("KingdomRoles","CityInvitations")) {
            var broken=raw.copy(); broken.putString(key,"corrupt"); var unreadable=KingdomSavedData.load(broken,h.getLevel().registryAccess());
            h.assertTrue(!unreadable.available()&&save(h,unreadable).equals(broken),"Present invalid administration is retained verbatim");
        }
        raw.putString("Diplomacy","corrupt"); data=load(h,raw);
        h.assertTrue(!data.diplomacy.available()&&save(h,data).getString("Diplomacy").equals("corrupt"),"Diplomacy-only corruption leaves cities available and retains original section"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="integration")
    public void unknownExtensionsSurviveWithoutResurrectingRemovedKnownFields(GameTestHelper h) throws Exception {
        var raw=combined(); raw.putString("ExtensionRoot","root");
        raw.getList("Kingdoms",Tag.TAG_COMPOUND).getCompound(0).putString("ExtensionKingdom","kingdom");
        raw.getList("Settlements",Tag.TAG_COMPOUND).getCompound(1).putString("ExtensionCity","city");
        var pair=raw.getList("Diplomacy",Tag.TAG_COMPOUND).getCompound(0); pair.putString("ExtensionPair","pair"); pair.getCompound("Invitation").putString("ExtensionRequest","request");
        var data=load(h,raw); var saved=save(h,data);
        h.assertTrue(saved.getString("ExtensionRoot").equals("root")&&saved.getList("Diplomacy",Tag.TAG_COMPOUND).getCompound(0).getCompound("Invitation").getString("ExtensionRequest").equals("request"),"Root and nested unknown fields survive");
        data.rename(id(1),new KingdomName("Renamed Extension State")); data.removeCityInvitation(id(20));
        data.diplomacy.put(new Pair(id(1),id(15)),null,Set.of(),null); saved=save(h,data);
        h.assertTrue(saved.getList("Kingdoms",Tag.TAG_COMPOUND).stream().anyMatch(t -> ((CompoundTag)t).getString("ExtensionKingdom").equals("kingdom"))
                &&saved.getList("Settlements",Tag.TAG_COMPOUND).stream().anyMatch(t -> ((CompoundTag)t).getString("ExtensionCity").equals("city")),"Record extensions survive rename and save");
        h.assertTrue(saved.getList("CityInvitations",Tag.TAG_COMPOUND).isEmpty()&&!saved.getList("Diplomacy",Tag.TAG_COMPOUND).getCompound(0).contains("Invitation"),"Removed invitations cannot resurrect from extension retention"); h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="integration")
    public void combinedGovernmentDiplomacyAndClaimsSurviveActualDiskReload(GameTestHelper h) throws Exception {
        var data=load(h,combined()); var directory=Files.createTempDirectory("regnum-integration-");
        try {
            var level=h.getLevel(); var writer=new DimensionDataStorage(directory.toFile(),level.getServer().getFixerUpper(),level.registryAccess());
            writer.set(KingdomSavedData.FILE_ID,data); writer.save();
            // Reusable compressed fixture for a normal dedicated-server process restart.
            var exported=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .toAbsolutePath().normalize().resolveSibling("integration-fixture.dat");
            Files.copy(directory.resolve(KingdomSavedData.FILE_ID+".dat"),exported,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            var reader=new DimensionDataStorage(directory.toFile(),level.getServer().getFixerUpper(),level.registryAccess());
            var restored=reader.computeIfAbsent(KingdomSavedData.FACTORY,KingdomSavedData.FILE_ID); restored.requireAvailable();
            h.assertTrue(save(h,data).equals(save(h,restored))&&!restored.isDirty(),"Actual compressed saved-data reload restores all merged fields");
            var service=new KingdomService(level.getServer(),restored); var recipient=new ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(id(17),"RestoredRuler"),net.minecraft.server.level.ClientInformation.createDefault());
            var reply=service.diplomacy().handle(recipient,new Request(1,Action.ACCEPT,Tab.INCOMING,0,id(15),id(1),id(22),id(21)));
            h.assertTrue(reply.success()&&service.diplomacy().relation(id(1),id(15))==Relation.ALLIED&&restored.cityInvitation(id(20))!=null,"Restored government can accept persisted diplomacy without losing city invitation");
        } finally { try (var files=Files.list(directory)) { for (var file:files.toList()) Files.delete(file); } Files.delete(directory); }
        h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="integration")
    public void transferredRulerAndChancellorUseOneCapabilityModel(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h,false)) {
            var successor=f.member(); f.base.kingdoms.administration().transfer(f.a,f.ka.id(),successor.getUUID());
            f.run(successor,f.kb,Action.INVITE); f.run(f.b,f.ka,Action.ACCEPT);
            h.assertTrue(f.service.relation(f.ka.id(),f.kb.id())==Relation.ALLIED&&f.data.kingdomRole(f.ka.id(),f.a.getUUID())==CHANCELLOR,"Current ruler can negotiate; previous ruler follows explicit chancellor role");
            f.base.kingdoms.administration().assignRole(successor,f.ka.id(),f.a.getUUID(),MEMBER);
            f.deny(f.a,f.request(f.a,f.kb,Action.HOSTILE),"permission"); f.run(successor,f.kb,Action.HOSTILE);
            h.assertTrue(f.service.relation(f.ka.id(),f.kb.id())==Relation.HOSTILE,"Historical founder has no residual authority after demotion");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="integration")
    public void demotionRevokesPendingAllianceAndPromotionCannotReviveIt(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h,false)) {
            var official=f.member(); f.data.setKingdomRole(f.ka.id(),official.getUUID(),CHANCELLOR);
            f.run(official,f.kb,Action.INVITE); var stale=f.request(f.b,f.ka,Action.ACCEPT);
            f.base.kingdoms.administration().assignRole(f.a,f.ka.id(),official.getUUID(),OFFICER);
            f.deny(official,f.request(official,f.kb,Action.HOSTILE),"permission"); f.deny(f.b,stale,"stale");
            f.data.setKingdomRole(f.ka.id(),official.getUUID(),CHANCELLOR); f.deny(f.b,stale,"stale");
            f.run(official,f.kb,Action.INVITE); f.deny(f.b,stale,"stale"); f.run(f.b,f.ka,Action.ACCEPT);
            h.assertTrue(f.service.relation(f.ka.id(),f.kb.id())==Relation.ALLIED,"Only a replacement invitation can be accepted after revocation");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="integration")
    public void cityDepartureRevokesGovernmentAndInvitationsWithoutChangingMunicipalData(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h,false)) {
            var mayor=f.base.player(); var city=f.base.city(mayor); f.data.affiliate(city,f.ka.id()); f.data.setKingdomRole(f.ka.id(),mayor.getUUID(),CHANCELLOR);
            f.run(mayor,f.kb,Action.INVITE); var stale=f.request(f.b,f.ka,Action.ACCEPT); var claims=f.data.settlementClaims(city.id());
            f.base.kingdoms.administration().leaveCity(mayor,city.id());
            h.assertTrue(f.data.kingdomRole(f.ka.id(),mayor.getUUID())==null&&f.data.settlementClaims(city.id()).equals(claims)
                    &&f.data.settlement(city.id()).ownerId().equals(mayor.getUUID())&&f.data.settlement(city.id()).obeliskId().equals(city.obeliskId()),"City departure retains private domain and revokes political membership");
            f.deny(f.b,stale,"stale"); f.data.affiliate(f.data.settlement(city.id()),f.ka.id());
            h.assertTrue(f.data.kingdomRole(f.ka.id(),mayor.getUUID())==MEMBER,"Rejoining cannot restore removed government role");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="integration")
    public void alliancesAndLeadershipNeverGrantForeignBuildOrMachineRights(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h,true)) {
            f.run(f.a,f.kb,Action.INVITE); f.run(f.b,f.ka,Action.ACCEPT);
            var successor=f.member(); f.base.kingdoms.administration().transfer(f.a,f.ka.id(),successor.getUUID());
            var level=h.getLevel(); var own=f.ka.capitalPos(); var foreign=own.offset(16,0,0); var wilderness=own.offset(0,0,32);
            f.data.putClaim(ClaimPos.at(level.dimension(),foreign),f.kb.capitalSettlementId());
            h.assertTrue(ClaimService.canBuild(level,successor,own)&&!ClaimService.canBuild(level,successor,foreign)
                    &&!ClaimService.sameTerritory(level,own,foreign)&&ClaimService.sameTerritory(level,own,own.east())&&ClaimService.canBuild(level,successor,wilderness),"Allied foreign claims stay isolated; same-city and wilderness positive controls remain");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="integration")
    public void allianceAndRoleChangesDoNotWeakenActualExplosionFiltering(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h,true)) {
            f.run(f.a,f.kb,Action.INVITE); f.run(f.b,f.ka,Action.ACCEPT); var member=f.member(); f.data.setKingdomRole(f.ka.id(),member.getUUID(),CHANCELLOR);
            f.data.setKingdomRole(f.ka.id(),member.getUUID(),MEMBER); f.data.removeCitizen(f.ka.capitalSettlementId(),member.getUUID());
            var level=h.getLevel(); var claimed=f.ka.capitalPos(); var wild=claimed.offset(0,0,32); level.getChunkAt(claimed);level.getChunkAt(wild);
            level.setBlock(claimed,Blocks.STONE.defaultBlockState(),18); level.setBlock(wild,Blocks.STONE.defaultBlockState(),18);
            var explosion=new Explosion(level,null,claimed.getX(),claimed.getY(),claimed.getZ(),4,false,Explosion.BlockInteraction.DESTROY);
            explosion.getToBlow().add(claimed);explosion.getToBlow().add(wild);explosion.finalizeExplosion(false);
            h.assertTrue(level.getBlockState(claimed).is(Blocks.STONE)&&level.getBlockState(wild).isAir()
                    &&!ClaimService.canBuild(level,member,claimed),"Vanilla explosion preserves allied claims, destroys wilderness and citizenship removal revokes access");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="integration")
    public void interleavedGovernmentRequestsRejectStaleTargetRoles(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h,false)) {
            var official=f.member();var target=f.member();f.data.setKingdomRole(f.ka.id(),official.getUUID(),CHANCELLOR);
            var ui=f.base.kingdoms.administrationView(); var tab=KingdomAdministrationPayloads.Tab.GOVERNMENT;
            var first=ui.handle(f.a,new KingdomAdministrationPayloads.Request(1,KingdomAdministrationPayloads.Action.VIEW,tab,"",0,null,null,null));
            var second=ui.handle(official,new KingdomAdministrationPayloads.Request(2,KingdomAdministrationPayloads.Action.VIEW,tab,"",0,null,null,null));
            var accepted=ui.handle(f.a,new KingdomAdministrationPayloads.Request(3,KingdomAdministrationPayloads.Action.OFFICER,tab,"",0,f.ka.id(),target.getUUID(),first.token()));
            var denied=ui.handle(official,new KingdomAdministrationPayloads.Request(4,KingdomAdministrationPayloads.Action.OFFICER,tab,"",0,f.ka.id(),target.getUUID(),second.token()));
            h.assertTrue(accepted.success()&&!denied.success()&&denied.result().endsWith("kingdom_ui_stale")&&f.data.kingdomRole(f.ka.id(),target.getUUID())==OFFICER,"Server serialization plus snapshot role comparison resolves concurrent administrative intent");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="integration")
    public void revokedPermissionsRefreshSnapshotsAndEmitInvalidationWithoutDirtyReads(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h,false)) {
            var official=f.member(); var events=new AtomicInteger(); f.data.onPoliticalChange(events::incrementAndGet);
            f.data.setKingdomRole(f.ka.id(),official.getUUID(),CHANCELLOR);f.run(official,f.kb,Action.INVITE);
            var before=f.view(official); int emitted=events.get(); f.data.setDirty(false); f.view(official);
            h.assertTrue(before.manage()&&events.get()==emitted&&!f.data.isDirty(),"Reads do not broadcast or dirty persistent state");
            f.data.setKingdomRole(f.ka.id(),official.getUUID(),MEMBER);var after=f.view(official);
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
            try { State.CODEC.encode(buffer,after);after=State.CODEC.decode(buffer); } finally { buffer.release(); }
            h.assertTrue(!after.manage()&&after.entries().stream().noneMatch(e -> e.invitation()!=null)&&events.get()>emitted,"Demotion emits invalidation and refreshed wire snapshot clears private requests and management rights");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="integration")
    public void forgedWireRequestsAndSharedReplayGateCannotBorrowRulerAuthority(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h,false)) {
            var outsider=f.base.player();var official=f.member();f.data.setKingdomRole(f.ka.id(),official.getUUID(),OFFICER);
            var request=new Request(1,Action.HOSTILE,Tab.KINGDOMS,0,f.ka.id(),f.kb.id(),null,null);
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
            try { Request.CODEC.encode(buffer,request);request=Request.CODEC.decode(buffer); } finally { buffer.release(); }
            f.deny(outsider,request,"permission"); f.deny(official,request,"permission");
            h.assertTrue(KingdomManagementServer.gate(official,1,true)==null&&KingdomManagementServer.gate(official,1,true).endsWith("duplicate"),"All UI channels share server replay protection");
        } h.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final CitizenshipTests.Fixture base;final KingdomSavedData data;final ServerPlayer a,b;final Kingdom ka,kb;final DiplomacyService service;final GameTestHelper h;final boolean live;int sequence;
        Fixture(GameTestHelper h,boolean live) throws Exception {
            this.h=h;this.live=live;base=new CitizenshipTests.Fixture(h,live);data=base.data;a=base.player();b=base.player();
            ka=base.kingdom(a);kb=base.kingdom(b);data.add(ka);data.add(kb);service=base.kingdoms.diplomacy();
        }
        ServerPlayer member() throws Exception { var player=base.player();data.addCitizen(ka.capitalSettlementId(),player.getUUID());return player; }
        State view(ServerPlayer player) { return service.handle(player,new Request(++sequence,Action.VIEW,Tab.KINGDOMS,0,null,null,null,null)); }
        Request request(ServerPlayer player,Kingdom target,Action action) throws Exception {
            // Live-world fixtures contain kingdoms from every earlier suite. Pair state is
            // authoritative regardless of the directory page containing this target.
            var own=data.playerKingdom(player.getUUID());
            var entry=own==null?null:data.diplomacy.get(new Pair(own.id(),target.id()));
            return new Request(++sequence,action,Tab.KINGDOMS,0,own==null?null:own.id(),target.id(),entry==null?null:entry.revision(),entry==null||entry.invitation()==null?null:entry.invitation().id());
        }
        void run(ServerPlayer player,Kingdom target,Action action) throws Exception {
            var result=service.handle(player,request(player,target,action));
            h.assertTrue(result.success(),"Expected "+action+" to succeed: "+result.result());
        }
        void deny(ServerPlayer player,Request request,String key) {
            var before=save(h,data);data.setDirty(false);var result=service.handle(player,request);
            h.assertTrue(!result.success()&&result.result().equals("diplomacy.regnum-nations."+key)&&before.equals(save(h,data))&&!data.isDirty(),"Rejected diplomatic request must leave persistent state unchanged: "+result.result());
        }
        @Override public void close() throws Exception {
            base.close();
            if (live) for (var kingdom:List.of(ka,kb)) { data.deleteKingdom(kingdom.id());data.deleteSettlement(kingdom.capitalSettlementId()); }
        }
    }
}
