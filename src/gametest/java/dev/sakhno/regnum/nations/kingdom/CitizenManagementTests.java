package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.kingdom.CitizenshipTests.Fixture;
import dev.sakhno.regnum.nations.networking.CitizenManagementPayloads.*;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;
import static dev.sakhno.regnum.nations.networking.CitizenManagementPayloads.PAGE_SIZE;

/** Tests the same authenticated adapter and DTO codecs used by the network receiver, without a client renderer. */
public class CitizenManagementTests implements FabricGameTest {
    @GameTest(template=EMPTY_STRUCTURE,batch="citizen_ui")
    public void memberSnapshotsSynchronizeNamesRolesCountsAndOnlineStatus(GameTestHelper h) throws Exception {
        try (var f=new UI(h)) {
            var owner=f.player(); var officer=f.player(); var offline=f.player(); var city=f.base.city(owner);
            f.base.join(owner,officer,city); f.base.advance(); f.base.join(owner,offline,city);
            f.base.citizens.assignRole(owner,officer.getUUID(),CityRole.OFFICER); f.offline(offline);
            var state=f.view(owner,Tab.MEMBERS);
            h.assertTrue(state.city().equals(city.id())&&state.citizens()==3&&state.entries().size()==3&&state.cityName().equals(city.name().display()),"Roster includes implicit owner and every member");
            h.assertTrue(state.invite()&&state.manage()&&!state.leave(),"Owner action flags derive from current policy");
            var first=state.entries().getFirst(); h.assertTrue(first.player().equals(owner.getUUID())&&first.role()==CityRole.OWNER&&first.online()&&first.name().equals(owner.getGameProfile().getName()),"Owner is labeled and sorted first");
            var member=state.entries().stream().filter(e -> e.player().equals(offline.getUUID())).findFirst().orElseThrow();
            h.assertTrue(!member.online()&&member.name().isEmpty()&&member.role()==CityRole.CITIZEN,"Unknown offline names keep UUID identity and reliable offline status");
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
            try { State.CODEC.encode(buffer,state); h.assertTrue(state.equals(State.CODEC.decode(buffer)),"Member snapshot must round-trip over its actual wire codec"); } finally { buffer.release(); }
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="citizen_ui")
    public void paginationAndSearchReturnBoundedCompleteStableRoster(GameTestHelper h) throws Exception {
        try (var f=new UI(h)) {
            var owner=f.player(); var city=f.base.city(owner); var expected=new HashSet<UUID>(); expected.add(owner.getUUID());
            for (int i=0;i<17;i++) { var member=f.player(); f.base.data.addCitizen(city.id(),member.getUUID()); expected.add(member.getUUID()); }
            var actual=new HashSet<UUID>();
            for (int page=0;page<3;page++) {
                var state=f.ui.handle(owner,new Request(++f.sequence,Action.VIEW,Tab.MEMBERS,"",page,city.id(),null,null));
                h.assertTrue(state.page()==page&&state.matches()==18&&state.entries().size()<=PAGE_SIZE,"Page remains bounded and reports total matches");
                state.entries().forEach(e -> actual.add(e.player()));
            }
            h.assertTrue(actual.equals(expected),"Every UUID is reachable across pages");
            var search=f.ui.handle(owner,new Request(++f.sequence,Action.VIEW,Tab.MEMBERS,owner.getUUID().toString(),Integer.MAX_VALUE,city.id(),null,null));
            h.assertTrue(search.page()==0&&search.entries().size()==1&&search.entries().getFirst().player().equals(owner.getUUID()),"Search uses stored UUIDs and clamps stale page indexes");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="citizen_ui")
    public void ownerRoleActionsRefreshMembershipState(GameTestHelper h) throws Exception {
        try (var f=new UI(h)) {
            var owner=f.player(); var member=f.player(); var city=f.base.city(owner); f.base.join(owner,member,city);
            var initial=f.view(owner,Tab.MEMBERS);
            var promoted=f.action(owner,initial,Action.PROMOTE,member.getUUID());
            h.assertTrue(promoted.success()&&role(promoted,member.getUUID())==CityRole.OFFICER,"Promotion returns refreshed officer row");
            var demoted=f.action(owner,promoted,Action.DEMOTE,member.getUUID());
            h.assertTrue(demoted.success()&&role(demoted,member.getUUID())==CityRole.CITIZEN,"Demotion returns refreshed citizen row");
            f.reject(owner,demoted,Action.PROMOTE,owner.getUUID(),CITIZEN_UI_TARGET);
            var current=f.view(owner,Tab.MEMBERS); f.reject(owner,current,Action.KICK,owner.getUUID(),OWNER_CANNOT_LEAVE);
            f.reject(owner,f.view(owner,Tab.MEMBERS),Action.LEAVE,null,OWNER_CANNOT_LEAVE);
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="citizen_ui")
    public void officerInvitesOnlinePlayerAndBothInvitationListsSynchronize(GameTestHelper h) throws Exception {
        try (var f=new UI(h)) {
            var owner=f.player(); var officer=f.player(); var recipient=f.player(); var city=f.base.city(owner);
            f.base.join(owner,officer,city); f.base.citizens.assignRole(owner,officer.getUUID(),CityRole.OFFICER);
            var members=f.view(officer,Tab.MEMBERS); h.assertTrue(members.invite()&&!members.manage()&&members.leave(),"Officer flags permit invitations only");
            var online=f.view(officer,Tab.ONLINE); h.assertTrue(online.entries().stream().anyMatch(e -> e.player().equals(recipient.getUUID())),"Eligible online player is listed");
            var invited=f.action(officer,online,Action.INVITE,recipient.getUUID()); h.assertTrue(invited.success(),"Officer invitation delegates to the existing policy");
            var outgoing=f.view(officer,Tab.OUTGOING); var inbox=f.view(recipient,Tab.INBOX);
            h.assertTrue(outgoing.entries().size()==1&&inbox.entries().size()==1&&outgoing.entries().getFirst().id().equals(inbox.entries().getFirst().id())
                    &&inbox.entries().getFirst().city().equals(city.id())&&inbox.entries().getFirst().seconds()==60,"Sent/received pages describe the same existing invitation");
            var accepted=f.action(recipient,inbox,Action.ACCEPT,inbox.entries().getFirst().id());
            h.assertTrue(accepted.success()&&accepted.city().equals(city.id())&&accepted.citizens()==3&&accepted.leave()&&!accepted.manage()&&!accepted.invite(),"Acceptance refreshes membership and citizen permissions");
            h.assertTrue(f.view(officer,Tab.OUTGOING).entries().isEmpty(),"Accepted invitation leaves the sent list");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="citizen_ui")
    public void citizenCanReadAndLeaveButCannotManageMembersOrInvite(GameTestHelper h) throws Exception {
        try (var f=new UI(h)) {
            var owner=f.player(); var member=f.player(); var city=f.base.city(owner); f.base.join(owner,member,city);
            var state=f.view(member,Tab.MEMBERS);
            h.assertTrue(!state.invite()&&!state.manage()&&state.leave()&&state.entries().size()==2,"Citizen can view own city and leave");
            f.reject(member,state,Action.KICK,owner.getUUID(),NOT_OWNER);
            f.reject(member,f.view(member,Tab.MEMBERS),Action.PROMOTE,owner.getUUID(),NOT_OWNER);
            var inviteDenied=f.view(member,Tab.ONLINE); h.assertTrue(!inviteDenied.success()&&inviteDenied.result().equals(new FoundingException(NOT_OWNER).translationKey())&&inviteDenied.tab()==Tab.MEMBERS,"No online invitation page for ordinary citizens");
            var left=f.action(member,f.view(member,Tab.MEMBERS),Action.LEAVE,null);
            h.assertTrue(left.success()&&left.city()==null&&left.entries().isEmpty()&&!left.leave()&&f.view(owner,Tab.MEMBERS).citizens()==1,"Leave refreshes both membership and subsequent owner roster");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="citizen_ui")
    public void outsiderCannotReadForeignRosterOrMutateUsingOwnerToken(GameTestHelper h) throws Exception {
        try (var f=new UI(h)) {
            var owner=f.player(); var outsider=f.player(); var city=f.base.city(owner); var ownerState=f.view(owner,Tab.MEMBERS);
            var response=f.ui.handle(outsider,new Request(++f.sequence,Action.VIEW,Tab.MEMBERS,"",0,city.id(),null,null));
            h.assertTrue(response.city()==null&&response.entries().isEmpty()&&!response.manage()&&!response.invite()&&!response.leave()&&!response.result().isEmpty(),"Outsiders receive no private roster");
            f.reject(outsider,ownerState,Action.KICK,owner.getUUID(),CITIZEN_UI_STALE);
            h.assertTrue(f.view(owner,Tab.MEMBERS).citizens()==1,"Forged actor cannot affect the owner");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="citizen_ui")
    public void offlineMembersCanBePromotedDemotedAndRemovedByStoredUuid(GameTestHelper h) throws Exception {
        try (var f=new UI(h)) {
            var owner=f.player(); var member=f.player(); var city=f.base.city(owner); f.base.join(owner,member,city); f.offline(member);
            var promoted=f.action(owner,f.view(owner,Tab.MEMBERS),Action.PROMOTE,member.getUUID());
            h.assertTrue(promoted.success()&&role(promoted,member.getUUID())==CityRole.OFFICER,"Offline promotion uses UUID");
            var demoted=f.action(owner,promoted,Action.DEMOTE,member.getUUID()); h.assertTrue(demoted.success(),"Offline demotion uses UUID");
            var kicked=f.action(owner,demoted,Action.KICK,member.getUUID());
            h.assertTrue(kicked.success()&&kicked.citizens()==1&&!f.base.data.isCitizen(city.id(),member.getUUID()),"Offline removal refreshes the list");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="citizen_ui")
    public void concurrentRoleChangeAndRemoveRejoinInvalidateOldConfirmation(GameTestHelper h) throws Exception {
        try (var f=new UI(h)) {
            var owner=f.player(); var member=f.player(); var city=f.base.city(owner); f.base.join(owner,member,city);
            var stale=f.view(owner,Tab.MEMBERS); f.base.citizens.assignRole(owner,member.getUUID(),CityRole.OFFICER);
            f.reject(owner,stale,Action.KICK,member.getUUID(),CITIZEN_UI_STALE);
            var beforeRejoin=f.view(owner,Tab.MEMBERS); f.base.data.removeCitizen(city.id(),member.getUUID()); f.base.data.addCitizen(city.id(),member.getUUID()); f.base.data.assignRole(city.id(),member.getUUID(),CityRole.OFFICER);
            f.reject(owner,beforeRejoin,Action.KICK,member.getUUID(),CITIZEN_UI_STALE);
            h.assertTrue(f.base.data.isCitizen(city.id(),member.getUUID()),"Identical-looking rejoined membership must survive an old confirmation");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="citizen_ui")
    public void kickedViewerLosesRosterAndOldActionsAreRejected(GameTestHelper h) throws Exception {
        try (var f=new UI(h)) {
            var owner=f.player(); var member=f.player(); var city=f.base.city(owner); f.base.join(owner,member,city);
            var state=f.view(member,Tab.MEMBERS); f.base.citizens.kick(owner,member.getUUID());
            var rejected=f.reject(member,state,Action.LEAVE,null,CITIZEN_UI_STALE);
            h.assertTrue(rejected.city()==null&&rejected.entries().isEmpty()&&!rejected.leave(),"Error response clears private cached roster");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="citizen_ui")
    public void invalidTargetAndTargetsOutsideDisplayedPageAreRejected(GameTestHelper h) throws Exception {
        try (var f=new UI(h)) {
            var owner=f.player(); var city=f.base.city(owner);
            f.reject(owner,f.view(owner,Tab.MEMBERS),Action.PROMOTE,UUID.randomUUID(),CITIZEN_UI_TARGET);
            f.reject(owner,f.view(owner,Tab.MEMBERS),Action.KICK,null,CITIZEN_UI_TARGET);
            var targets=new ArrayList<UUID>(); for (int i=0;i<8;i++) { var target=f.player(); targets.add(target.getUUID()); f.base.data.addCitizen(city.id(),target.getUUID()); }
            var page=f.view(owner,Tab.MEMBERS); UUID outside=targets.stream().filter(id -> page.entries().stream().noneMatch(e -> e.id().equals(id))).findFirst().orElseThrow();
            f.reject(owner,page,Action.KICK,outside,CITIZEN_UI_TARGET);
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="citizen_ui")
    public void expiredReplayedDisconnectedAndWrongSessionRequestsFail(GameTestHelper h) throws Exception {
        try (var f=new UI(h)) {
            var owner=f.player(); var member=f.player(); var city=f.base.city(owner); f.base.join(owner,member,city);
            var expired=f.view(owner,Tab.MEMBERS); f.base.now.addAndGet(CitizenManagement.SNAPSHOT_NANOS);
            f.reject(owner,expired,Action.KICK,member.getUUID(),CITIZEN_UI_STALE);
            var token=f.view(owner,Tab.MEMBERS); var promoted=f.action(owner,token,Action.PROMOTE,member.getUUID()); h.assertTrue(promoted.success(),"Initial action succeeds");
            f.reject(owner,token,Action.PROMOTE,member.getUUID(),CITIZEN_UI_STALE);
            var disconnected=f.view(owner,Tab.MEMBERS); f.ui.disconnect(owner.getUUID()); f.reject(owner,disconnected,Action.KICK,member.getUUID(),CITIZEN_UI_STALE);
            var old=f.view(owner,Tab.MEMBERS); var reconnected=f.base.player(owner.getUUID());
            f.reject(reconnected,old,Action.KICK,member.getUUID(),CITIZEN_UI_STALE);
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="citizen_ui")
    public void replacedExpiredAndDeclinedInvitationsCannotBeAcceptedFromOldPage(GameTestHelper h) throws Exception {
        try (var f=new UI(h)) {
            var owner=f.player(); var recipient=f.player(); var city=f.base.city(owner); f.base.citizens.invite(owner,recipient);
            var old=f.view(recipient,Tab.INBOX); UUID id=old.entries().getFirst().id(); f.base.advance(); f.base.citizens.invite(owner,recipient);
            var replaced=f.reject(recipient,old,Action.ACCEPT,id,CITIZEN_UI_STALE); h.assertTrue(replaced.entries().size()==1&&!replaced.entries().getFirst().id().equals(id),"Replacement has fresh invitation identity");
            var declined=f.action(recipient,replaced,Action.DECLINE,replaced.entries().getFirst().id()); h.assertTrue(declined.success()&&declined.entries().isEmpty(),"Decline refreshes inbox");
            f.base.advance(); f.base.citizens.invite(owner,recipient); var expiring=f.view(recipient,Tab.INBOX); f.base.now.addAndGet(CitizenshipService.INVITATION_NANOS);
            f.reject(recipient,expiring,Action.ACCEPT,expiring.entries().getFirst().id(),CITIZEN_UI_STALE);
            h.assertTrue(f.view(recipient,Tab.INBOX).entries().isEmpty()&&f.base.data.citizenCity(recipient.getUUID())==null,"Expired invitation never grants membership");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="citizen_ui")
    public void officerPermissionRevocationAndOfflineInviteTargetRevalidateAtMutation(GameTestHelper h) throws Exception {
        try (var f=new UI(h)) {
            var owner=f.player(); var officer=f.player(); var recipient=f.player(); var city=f.base.city(owner); f.base.join(owner,officer,city); f.base.citizens.assignRole(owner,officer.getUUID(),CityRole.OFFICER);
            var before=f.view(officer,Tab.ONLINE); f.base.citizens.assignRole(owner,officer.getUUID(),CityRole.CITIZEN);
            f.reject(officer,before,Action.INVITE,recipient.getUUID(),CITIZEN_UI_STALE);
            var online=f.view(owner,Tab.ONLINE); f.offline(recipient); f.reject(owner,online,Action.INVITE,recipient.getUUID(),PLAYER_OFFLINE);
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="citizen_ui")
    public void snapshotsDoNotChangeNbtAndRequestCodecPreservesUuidTargets(GameTestHelper h) throws Exception {
        try (var f=new UI(h)) {
            var owner=f.player(); f.base.city(owner); var before=f.base.saved(); f.base.data.setDirty(false);
            var state=f.view(owner,Tab.MEMBERS); h.assertTrue(before.equals(f.base.saved())&&!f.base.data.isDirty(),"Snapshots/revisions add no persisted data or dirty writes");
            var request=new Request(7,Action.KICK,Tab.MEMBERS,"UUID filter",2,state.city(),UUID.randomUUID(),state.token());
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
            try { Request.CODEC.encode(buffer,request); h.assertTrue(request.equals(Request.CODEC.decode(buffer)),"Request codec retains authenticated snapshot token and target UUID"); } finally { buffer.release(); }
            h.assertTrue(KingdomManagementServer.gate(owner,1000,true)==null&&"ui.regnum-nations.duplicate".equals(KingdomManagementServer.gate(owner,1000,true)),"Shared receiver gate rejects packet replay");
        } h.succeed();
    }
    @GameTest(template=EMPTY_STRUCTURE,batch="citizen_ui")
    public void cityMenuResolvesCitizenMembershipWithoutGrantingOwnerRights(GameTestHelper h) throws Exception {
        try (var f=new Fixture(h,true)) {
            var owner=f.player(); var citizen=f.player(); var city=f.city(owner); f.join(owner,citizen,city);
            var state=KingdomManagementServer.state(citizen,1,0,null,null,"",false);
            h.assertTrue(state.city()!=null&&state.city().id().equals(city.id())&&state.ownSettlement().equals(city.id())&&!state.city().manager(),"Citizen city management opens own city but keeps owner actions disabled");
        } h.succeed();
    }

    private static CityRole role(State state,UUID player) { return state.entries().stream().filter(e -> e.player().equals(player)).findFirst().orElseThrow().role(); }
    private static final class UI implements AutoCloseable {
        final GameTestHelper h; final Fixture base; final CitizenManagement ui; int sequence;
        final java.util.Map<UUID,ServerPlayer> online;
        @SuppressWarnings("unchecked")
        UI(GameTestHelper h) throws ReflectiveOperationException {
            this.h=h; base=new Fixture(h,false); ui=new CitizenManagement(base.kingdoms,base.citizens,base.now::get);
            // The older fixture only populates PlayerList.players. Match vanilla's second UUID index too.
            var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
            online=(java.util.Map<UUID,ServerPlayer>)field.get(h.getLevel().getServer().getPlayerList());
        }
        ServerPlayer player() { var player=base.player(); online.put(player.getUUID(),player); return player; }
        void offline(ServerPlayer player) { base.serverPlayers().remove(player); online.remove(player.getUUID(),player); }
        State view(ServerPlayer actor,Tab tab) { return ui.handle(actor,new Request(++sequence,Action.VIEW,tab,"",0,null,null,null)); }
        State action(ServerPlayer actor,State state,Action action,UUID target) { return ui.handle(actor,new Request(++sequence,action,state.tab(),"",state.page(),state.city(),target,state.token())); }
        State reject(ServerPlayer actor,State state,Action action,UUID target,FoundingException.Reason reason) {
            var before=base.saved(); base.data.setDirty(false); var reply=action(actor,state,action,target);
            h.assertTrue(!reply.success()&&reply.result().equals(new FoundingException(reason).translationKey()),"Expected "+reason+", got "+reply.result());
            h.assertTrue(before.equals(base.saved())&&!base.data.isDirty(),"Rejected UI action cannot mutate SavedData"); return reply;
        }
        @Override public void close() { for (var player:base.players) online.remove(player.getUUID(),player); base.close(); }
    }
}
