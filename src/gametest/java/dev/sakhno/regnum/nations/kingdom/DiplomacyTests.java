package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.kingdom.DiplomacyData.*;
import dev.sakhno.regnum.nations.networking.DiplomacyPayloads.*;
import dev.sakhno.regnum.nations.networking.DiplomacyPayloads.Entry;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.DimensionDataStorage;

public final class DiplomacyTests implements FabricGameTest {
    @GameTest(template=EMPTY_STRUCTURE,batch="diplomacy")
    public void invitationAcceptanceCreatesOneBilateralAlliance(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h)) {
            h.assertTrue(f.relation()==Relation.NEUTRAL,"Default relation is neutral in both directions");
            f.run(f.a,f.kb,Action.INVITE);
            var incoming=f.view(f.b,Tab.INCOMING);
            h.assertTrue(incoming.entries().size()==1&&incoming.entries().getFirst().incoming(),"Recipient sees incoming invitation");
            h.assertTrue(f.view(f.a,Tab.OUTGOING).entries().getFirst().invitation().equals(incoming.entries().getFirst().invitation()),"Both views reference the same request");
            f.run(f.b,f.ka,Action.ACCEPT);
            h.assertTrue(f.relation()==Relation.ALLIED&&f.view(f.a,Tab.OUTGOING).entries().isEmpty()&&f.view(f.b,Tab.INCOMING).entries().isEmpty(),"Accept creates a symmetric alliance and consumes invitation");
            var saved=f.base.saved().getList("Diplomacy",Tag.TAG_COMPOUND);
            h.assertTrue(saved.size()==1&&saved.getCompound(0).hasUUID("Alliance")&&!saved.getCompound(0).contains("Invitation"),"Exactly one bilateral agreement persists");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="diplomacy")
    public void rejectionAndCancellationPreserveNeutralityAndInvalidateOldRequests(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h)) {
            f.run(f.a,f.kb,Action.INVITE); var stale=f.request(f.b,f.ka,Action.ACCEPT);
            f.reject(f.a,f.request(f.a,f.kb,Action.ACCEPT),"permission");
            f.reject(f.b,f.request(f.b,f.ka,Action.CANCEL),"permission");
            f.run(f.b,f.ka,Action.REJECT); f.reject(f.b,stale,"stale");
            f.run(f.a,f.kb,Action.INVITE); var replacement=f.request(f.b,f.ka,Action.ACCEPT);
            h.assertTrue(!replacement.invitation().equals(stale.invitation()),"Replacement request has a new identity");
            f.reject(f.b,stale,"stale"); f.run(f.a,f.kb,Action.CANCEL); f.reject(f.b,replacement,"stale");
            h.assertTrue(f.relation()==Relation.NEUTRAL,"Reject/cancel never change relation");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="diplomacy")
    public void duplicateCrossedAndReplayedRequestsCannotCreateContradictoryRelations(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h)) {
            var first=f.request(f.a,f.kb,Action.INVITE); var crossed=f.request(f.b,f.ka,Action.INVITE);
            f.service.act(f.a,first); f.reject(f.a,first,"stale"); f.reject(f.b,crossed,"stale");
            f.reject(f.a,f.request(f.a,f.kb,Action.INVITE),"conflict"); f.reject(f.b,f.request(f.b,f.ka,Action.INVITE),"conflict");
            var accept=f.request(f.b,f.ka,Action.ACCEPT); f.service.act(f.b,accept); f.reject(f.b,accept,"stale");
            h.assertTrue(f.relation()==Relation.ALLIED,"Only explicit single acceptance creates alliance");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="diplomacy")
    public void cityRolesAndAffiliationDoNotGrantDiplomacy(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h)) {
            var officer=f.base.player(); var citizen=f.base.player(); var owner=f.base.player(); var outsider=f.base.player();
            f.base.data.addCitizen(f.ka.capitalSettlementId(),officer.getUUID());
            f.base.data.assignRole(f.ka.capitalSettlementId(),officer.getUUID(),CityRole.OFFICER);
            f.base.data.addCitizen(f.ka.capitalSettlementId(),citizen.getUUID());
            var city=f.base.city(owner); f.base.data.affiliate(city,f.ka.id());
            for (var actor:List.of(officer,citizen,owner,outsider)) {
                var forged=new Request(1,Action.HOSTILE,Tab.KINGDOMS,0,f.ka.id(),f.kb.id(),null,null);
                f.reject(actor,forged,"permission");
                h.assertTrue(!f.view(actor,Tab.KINGDOMS).manage(),"City roles and visitors have no diplomatic capability");
            }
            h.assertTrue(CityPermissions.allows(f.base.data,f.base.data.settlement(f.ka.id()),officer.getUUID(),CityPermission.INVITE)
                    &&!CityPermissions.allows(f.base.data,f.base.data.settlement(f.ka.id()),officer.getUUID(),CityPermission.MANAGE_MEMBERS),"Existing officer permissions remain intact");
            f.run(f.a,f.kb,Action.INVITE);
            h.assertTrue(f.view(officer,Tab.INCOMING).entries().stream().noneMatch(e -> e.invitation()!=null),"Nonrepresentatives never receive pending requests");
            h.assertTrue(f.view(f.a,Tab.KINGDOMS).manage(),"Founder retains diplomacy capability");
            f.reject(f.a,new Request(2,Action.HOSTILE,Tab.KINGDOMS,0,f.kb.id(),f.ka.id(),null,null),"stale");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="diplomacy")
    public void hostilityEndsAllianceAndRequiresBothDeclarersToWithdraw(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h)) {
            f.ally(); var before=f.base.saved(); f.run(f.a,f.kb,Action.HOSTILE);
            h.assertTrue(f.relation()==Relation.HOSTILE,"One declaration appears hostile to both parties");
            f.reject(f.a,f.request(f.a,f.kb,Action.HOSTILE),"conflict");
            f.reject(f.b,f.request(f.b,f.ka,Action.INVITE),"conflict");
            f.reject(f.b,f.request(f.b,f.ka,Action.NEUTRAL),"conflict");
            f.run(f.b,f.ka,Action.HOSTILE); f.run(f.a,f.kb,Action.NEUTRAL);
            h.assertTrue(f.relation()==Relation.HOSTILE,"Cannot erase another kingdom's declaration");
            f.run(f.b,f.ka,Action.NEUTRAL);
            h.assertTrue(f.relation()==Relation.NEUTRAL,"Both withdrawals restore neutrality without reviving alliance");
            var after=f.base.saved(); before.remove("Diplomacy"); after.remove("Diplomacy");
            h.assertTrue(before.equals(after),"Diplomatic transitions leave all cities, claims and kingdom data unchanged");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="diplomacy")
    public void allianceTerminationIsNeutralAndOldConfirmationsCannotEndNewAlliance(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h)) {
            f.ally(); var old=f.request(f.a,f.kb,Action.TERMINATE); var simultaneous=f.request(f.b,f.ka,Action.TERMINATE);
            f.service.act(f.a,old); f.reject(f.b,simultaneous,"stale");
            h.assertTrue(f.relation()==Relation.NEUTRAL,"Either party can terminate alliance to neutrality");
            f.reject(f.a,f.request(f.a,f.kb,Action.TERMINATE),"conflict");
            f.ally(); f.reject(f.a,old,"stale");
            h.assertTrue(f.relation()==Relation.ALLIED,"An old confirmation cannot terminate a newly negotiated alliance");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="diplomacy")
    public void hostilityInvalidatesPendingAcceptanceAndConflictingConfirmations(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h)) {
            f.run(f.a,f.kb,Action.INVITE); var accept=f.request(f.b,f.ka,Action.ACCEPT);
            var hostile=f.request(f.a,f.kb,Action.HOSTILE); f.service.act(f.a,hostile);
            f.reject(f.b,accept,"stale");
            h.assertTrue(f.view(f.b,Tab.INCOMING).entries().isEmpty()&&f.relation()==Relation.HOSTILE,"Declaration clears pending invitation");
            f.run(f.a,f.kb,Action.NEUTRAL); f.ally();
            var terminate=f.request(f.b,f.ka,Action.TERMINATE); f.run(f.a,f.kb,Action.HOSTILE); f.reject(f.b,terminate,"stale");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="diplomacy")
    public void selfMissingKingdomAndInvalidPlayersAreRejected(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h)) {
            for (UUID target:Arrays.asList(f.ka.id(),UUID.randomUUID(),null))
                f.reject(f.a,new Request(1,Action.INVITE,Tab.KINGDOMS,0,f.ka.id(),target,null,null),"target");
            var valid=f.request(f.a,f.kb,Action.HOSTILE); f.a.setHealth(0);
            var result=f.service.handle(f.a,valid);
            h.assertTrue(!result.success()&&result.entries().isEmpty()&&result.result().equals(new FoundingException(FoundingException.Reason.INVALID_PLAYER).translationKey()),"Dead players cannot act or read snapshots");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="diplomacy")
    public void deletedKingdomRemovesOnlyItsDiplomaticContracts(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h)) {
            var c=f.base.player(); var kc=f.add(c); f.run(f.a,f.kb,Action.INVITE); f.run(f.b,kc,Action.HOSTILE); f.run(f.a,kc,Action.HOSTILE);
            var stale=f.request(f.a,f.kb,Action.CANCEL); long claims=f.base.data.claimCount(f.kb.id());
            f.base.data.deleteKingdom(f.kb.id()); f.reject(f.a,stale,"target");
            h.assertTrue(f.base.data.settlement(f.kb.capitalSettlementId()).sovereign()==null&&f.base.data.settlementClaimCount(f.kb.capitalSettlementId())==claims,"Deletion preserves independent city and claims");
            h.assertTrue(f.base.saved().getList("Diplomacy",Tag.TAG_COMPOUND).size()==1&&f.service.relation(f.ka.id(),kc.id())==Relation.HOSTILE,"Unrelated relation survives deletion");
            f.base.data.deleteKingdom(f.ka.id()); h.assertTrue(f.base.saved().getList("Diplomacy",Tag.TAG_COMPOUND).isEmpty(),"No orphaned contracts remain");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="diplomacy")
    public void diplomacyAndPendingInvitationsSurviveDiskReloadAndRename(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h)) {
            var c=f.base.player(); var kc=f.add(c); f.ally(); f.run(f.a,kc,Action.INVITE); f.run(f.b,kc,Action.HOSTILE);
            var acceptance=f.request(c,f.ka,Action.ACCEPT); var requestId=acceptance.invitation();
            f.base.data.rename(f.ka.id(),new KingdomName("Renamed Diplomatic State"));
            var directory=Files.createTempDirectory("regnum-diplomacy-");
            try {
                var level=h.getLevel();
                var writer=new DimensionDataStorage(directory.toFile(),level.getServer().getFixerUpper(),level.registryAccess());
                writer.set(KingdomSavedData.FILE_ID,f.base.data); writer.save();
                var reader=new DimensionDataStorage(directory.toFile(),level.getServer().getFixerUpper(),level.registryAccess());
                var loaded=reader.computeIfAbsent(KingdomSavedData.FACTORY,KingdomSavedData.FILE_ID); loaded.requireAvailable();
                var service=new DiplomacyService(new KingdomService(level.getServer(),loaded),f.now::get,() -> f.lifetime);
                h.assertTrue(!loaded.isDirty()&&service.relation(f.ka.id(),f.kb.id())==Relation.ALLIED&&service.relation(f.kb.id(),kc.id())==Relation.HOSTILE,"Agreements and declarations survive disk reload without dirty writes");
                h.assertTrue(loaded.diplomacy.get(new Pair(f.ka.id(),kc.id())).invitation().id().equals(requestId),"Invitation identity and deadline survive");
                service.act(c,acceptance);
                h.assertTrue(service.relation(f.ka.id(),kc.id())==Relation.ALLIED&&loaded.byId(f.ka.id()).name().display().equals("Renamed Diplomatic State"),"Restored request accepts with stable IDs after rename");
                h.assertTrue(Set.copyOf(loaded.settlements()).equals(Set.copyOf(f.base.data.settlements()))&&loaded.citizens(f.ka.capitalSettlementId()).equals(f.base.data.citizens(f.ka.capitalSettlementId())),"Existing city records and membership survive");
            } finally {
                try (var files=Files.list(directory)) { for (var file:files.toList()) Files.delete(file); }
                Files.delete(directory);
            }
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="diplomacy")
    public void configuredDeadlinesExpireAcrossReloadAndStaleRequestsCannotRevive(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h)) {
            f.lifetime=60_000; f.run(f.a,f.kb,Action.INVITE); var accept=f.request(f.b,f.ka,Action.ACCEPT);
            h.assertTrue(f.entry(f.a,f.kb).expiresAt()==f.now.get()+60_000,"Configured lifetime controls new deadline");
            f.lifetime=120_000; f.now.addAndGet(60_000);
            var loaded=KingdomSavedData.load(f.base.saved(),h.getLevel().registryAccess());
            var service=new DiplomacyService(new KingdomService(h.getLevel().getServer(),loaded),f.now::get,() -> f.lifetime);
            var result=service.handle(f.b,accept);
            h.assertTrue(!result.success()&&result.result().equals("diplomacy.regnum-nations.stale")&&result.entries().stream().noneMatch(e -> e.invitation()!=null),"Deadline is exclusive, survives reload and ignores later config changes");
            f.service.handle(f.b,accept); f.run(f.a,f.kb,Action.INVITE);
            h.assertTrue(f.entry(f.a,f.kb).expiresAt()==f.now.get()+120_000,"New requests use changed lifetime");
            f.reject(f.b,accept,"stale");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="diplomacy")
    public void snapshotsAndInvalidActionsAvoidDirtyWritesAndWireCodecsAreBounded(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h)) {
            var before=f.base.saved(); f.base.data.setDirty(false); var state=f.view(f.a,Tab.KINGDOMS);
            h.assertTrue(!f.base.data.isDirty()&&before.equals(f.base.saved()),"Ordinary view never dirties persistent data");
            var request=f.request(f.a,f.kb,Action.INVITE);
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
            try {
                Request.CODEC.encode(buffer,request); h.assertTrue(request.equals(Request.CODEC.decode(buffer)),"Request wire codec retains revision and stable identifiers");
                State.CODEC.encode(buffer,state); h.assertTrue(state.equals(State.CODEC.decode(buffer)),"State wire codec round trips");
            } finally { buffer.release(); }
            f.run(f.a,f.kb,Action.INVITE); f.reject(f.a,f.request(f.a,f.kb,Action.INVITE),"conflict");
            var again=KingdomSavedData.load(f.base.saved(),h.getLevel().registryAccess());
            h.assertTrue(!again.isDirty()&&f.base.saved().equals(again.save(new CompoundTag(),h.getLevel().registryAccess())),"Schema 7 round trip is idempotent");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="diplomacy")
    public void migrationAndMalformedDiplomacyPreserveKingdomHistory(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h)) {
            var old=f.base.saved(); old.putInt("Version",5); old.remove("Diplomacy");
            var migrated=KingdomSavedData.load(old,h.getLevel().registryAccess()); migrated.requireAvailable();
            h.assertTrue(migrated.isDirty()&&migrated.byId(f.ka.id()).equals(f.ka)&&migrated.settlementClaimCount(f.ka.id())==1,"Schema 5 upgrades with history intact");
            f.ally(); var valid=f.base.saved();
            for (int corruption=0;corruption<5;corruption++) {
                var broken=valid.copy(); var list=broken.getList("Diplomacy",Tag.TAG_COMPOUND); var entry=list.getCompound(0);
                switch (corruption) {
                    case 0 -> list.add(entry.copy());
                    case 1 -> entry.putUUID("Second",entry.getUUID("First"));
                    case 2 -> entry.putString("Alliance","invalid UUID");
                    case 3 -> broken.putString("Diplomacy","retain this unreadable section");
                    case 4 -> { var declarations=new ListTag(); var d=new CompoundTag(); d.putUUID("Declarer",f.ka.id()); declarations.add(d); entry.put("Hostility",declarations); }
                }
                var loaded=KingdomSavedData.load(broken,h.getLevel().registryAccess()); loaded.requireAvailable();
                h.assertTrue(loaded.byId(f.ka.id()).equals(f.ka)&&!loaded.diplomacy.available(),"Malformed diplomacy fails closed without disabling kingdoms");
                h.assertTrue(loaded.save(new CompoundTag(),h.getLevel().registryAccess()).get("Diplomacy").equals(broken.get("Diplomacy")),"Exact corrupted diplomacy NBT is preserved");
                var denied=new DiplomacyService(new KingdomService(h.getLevel().getServer(),loaded),f.now::get,() -> f.lifetime).handle(f.a,f.request(f.a,f.kb,Action.TERMINATE));
                h.assertTrue(!denied.success()&&denied.result().equals("diplomacy.regnum-nations.unavailable"),"Unreadable diplomacy cannot mutate");
            }
            var orphan=valid.copy(); orphan.getList("Diplomacy",Tag.TAG_COMPOUND).getCompound(0).putUUID("Second",UUID.randomUUID());
            var loaded=KingdomSavedData.load(orphan,h.getLevel().registryAccess());
            h.assertTrue(loaded.diplomacy.available()&&loaded.isDirty()&&loaded.save(new CompoundTag(),h.getLevel().registryAccess()).getList("Diplomacy",Tag.TAG_COMPOUND).isEmpty(),"Orphaned pairs are safely pruned");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="diplomacy")
    public void directoryPaginationAndRequestTabsReachEveryKingdom(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h)) {
            var expected=new HashSet<UUID>(); expected.add(f.kb.id());
            for (int i=0;i<15;i++) expected.add(f.add(f.base.player()).id());
            var actual=new HashSet<UUID>();
            for (int page=0;page<3;page++) {
                var state=f.service.handle(f.a,new Request(++f.sequence,Action.VIEW,Tab.KINGDOMS,page,null,null,null,null));
                h.assertTrue(state.matches()==16&&state.entries().size()<=6,"Server bounds each page"); state.entries().forEach(e -> actual.add(e.kingdom()));
            }
            h.assertTrue(actual.equals(expected),"All known kingdoms are reachable without duplicates");
            var clamped=f.service.handle(f.a,new Request(++f.sequence,Action.VIEW,Tab.KINGDOMS,Integer.MAX_VALUE,null,null,null,null));
            h.assertTrue(clamped.page()==2,"Untrusted page indexes are clamped safely");
        } h.succeed();
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final CitizenshipTests.Fixture base;
        final ServerPlayer a,b; final Kingdom ka,kb; final AtomicLong now=new AtomicLong(1_000_000);
        final DiplomacyService service; long lifetime=604_800_000; int sequence;
        Fixture(GameTestHelper h) throws Exception {
            this.h=h; base=new CitizenshipTests.Fixture(h,false); a=base.player(); b=base.player(); ka=add(a); kb=add(b);
            service=new DiplomacyService(base.kingdoms,now::get,() -> lifetime);
        }
        Kingdom add(ServerPlayer player) throws Exception { var k=base.kingdom(player); base.data.add(k); return k; }
        State view(ServerPlayer actor,Tab tab) { return service.handle(actor,new Request(++sequence,Action.VIEW,tab,0,null,null,null,null)); }
        Entry entry(ServerPlayer actor,Kingdom target) {
            // Tests with >6 states find the target through the same bounded server API.
            for (int page=0;;page++) {
                State state=service.handle(actor,new Request(++sequence,Action.VIEW,Tab.KINGDOMS,page,null,null,null,null));
                var entry=state.entries().stream().filter(e -> e.kingdom().equals(target.id())).findFirst();
                if (entry.isPresent()) return entry.get();
                if ((page+1)*6>=state.matches()) throw new AssertionError("Missing target");
            }
        }
        Request request(ServerPlayer actor,Kingdom target,Action action) {
            var entry=entry(actor,target); var own=base.data.byFounder(actor.getUUID());
            return new Request(++sequence,action,Tab.KINGDOMS,0,own==null?null:own.id(),target.id(),entry.revision(),entry.invitation());
        }
        void run(ServerPlayer actor,Kingdom target,Action action) throws Exception {
            var reply=service.handle(actor,request(actor,target,action)); h.assertTrue(reply.success(),"Expected success for "+action+", got "+reply.result());
        }
        void ally() throws Exception { run(a,kb,Action.INVITE); run(b,ka,Action.ACCEPT); }
        Relation relation() throws Exception {
            var relation=service.relation(ka.id(),kb.id()); h.assertTrue(relation==service.relation(kb.id(),ka.id()),"Relation is symmetric"); return relation;
        }
        void reject(ServerPlayer actor,Request request,String reason) throws Exception {
            var before=base.saved(); base.data.setDirty(false);
            try { service.act(actor,request); throw new AssertionError("Expected rejection: "+reason); }
            catch (DiplomacyService.Failure e) { h.assertTrue(e.translationKey().equals("diplomacy.regnum-nations."+reason),"Expected "+reason+", got "+e.translationKey()); }
            h.assertTrue(before.equals(base.saved())&&!base.data.isDirty(),"Invalid action leaves persistent data untouched");
        }
        @Override public void close() { base.close(); }
    }
}
