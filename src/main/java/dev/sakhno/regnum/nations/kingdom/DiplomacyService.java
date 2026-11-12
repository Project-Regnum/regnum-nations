package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.api.diplomacy.*;
import dev.sakhno.regnum.nations.api.v1.ApiResult;
import dev.sakhno.regnum.nations.kingdom.DiplomacyData.*;
import dev.sakhno.regnum.nations.networking.InterstatePayloads;
import dev.sakhno.regnum.nations.networking.InterstatePayloads.*;
import java.util.*;
import java.util.function.LongSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import static dev.sakhno.regnum.nations.api.diplomacy.DiplomacyEvents.Kind.*;

/** All transitions reauthorize current governments on the owning server thread. */
public final class DiplomacyService {
    private final KingdomService kingdoms;
    private final MinecraftServer server;
    private final LongSupplier clock,lifetime;
    private record Deadline(Pair pair,UUID proposal,long at) { }
    private final NavigableSet<Deadline> deadlines=new TreeSet<>(Comparator.comparingLong(Deadline::at).thenComparing(Deadline::proposal));
    DiplomacyService(KingdomService kingdoms,LongSupplier clock,LongSupplier lifetime) {
        this.kingdoms=kingdoms; this.server=kingdoms.server(); this.clock=clock; this.lifetime=lifetime;
        kingdoms.data().diplomacy.entries().forEach((pair,entry) -> schedule(pair,entry,true));
        kingdoms.data().diplomacy.onChange(this::changed);
    }
    public static final class Failure extends Exception {
        private final String key;
        Failure(String key) { this.key="diplomacy.regnum-nations."+key; }
        public String translationKey() { return key; }
    }
    private KingdomSavedData data() throws FoundingException,Failure {
        var data=kingdoms.data(); data.requireAvailable();
        if (!data.diplomacy.available()) throw new Failure("unavailable");
        return data;
    }
    private void schedule(Pair pair,DiplomacyData.Entry entry,boolean add) {
        if (entry==null||entry.invitation()==null) return;
        var invitation=entry.invitation(); var deadline=new Deadline(pair,invitation.id(),invitation.expiresAt());
        if (add) deadlines.add(deadline); else deadlines.remove(deadline);
    }
    /** Constant-time idle check; only due proposals are visited. No kingdom/world scan or per-pair ticking. */
    void expireDue() {
        var data=kingdoms.data();
        if (!data.available()||!data.diplomacy.available()) return;
        long now=clock.getAsLong();
        while (!deadlines.isEmpty()&&deadlines.first().at()<=now) {
            Deadline due=deadlines.pollFirst(); var entry=data.diplomacy.get(due.pair());
            if (entry!=null&&entry.invitation()!=null&&entry.invitation().id().equals(due.proposal())) data.diplomacy.expire(due.pair(),now);
        }
    }
    private DiplomacyData.Entry current(KingdomSavedData data,Pair pair) {
        data.diplomacy.expire(pair,clock.getAsLong()); return data.diplomacy.get(pair);
    }
    public Relation relation(UUID first,UUID second) throws FoundingException,Failure {
        var data=data();
        if (first==null||second==null||first.equals(second)||data.byId(first)==null||data.byId(second)==null) throw new Failure("target");
        var entry=current(data,new Pair(first,second)); return entry==null?Relation.NEUTRAL:entry.relation();
    }
    public static KingdomPermission permission(Action action,Invitation invitation) {
        return switch (action) {
            case VIEW -> KingdomPermission.VIEW_DIPLOMACY;
            case INVITE -> KingdomPermission.PROPOSE_ALLIANCE;
            case PROPOSE_PEACE -> KingdomPermission.NEGOTIATE_PEACE;
            case DECLARE_WAR -> KingdomPermission.DECLARE_WAR;
            case ACCEPT, REJECT, CANCEL -> invitation==null?KingdomPermission.MANAGE_TREATIES:invitation.type().permission();
            default -> KingdomPermission.MANAGE_TREATIES;
        };
    }
    private static ProposalType proposal(Action action) {
        return switch (action) {
            case INVITE -> ProposalType.ALLIANCE;
            case PROPOSE_PACT -> ProposalType.NON_AGGRESSION;
            case PROPOSE_PEACE -> ProposalType.PEACE;
            case TERMINATE -> ProposalType.END_ALLIANCE;
            case END_PACT -> ProposalType.END_NON_AGGRESSION;
            default -> null;
        };
    }
    /** Revision + proposal UUID reject replay and stale confirmations, including across restarts. */
    public void act(ServerPlayer player,Request request) throws FoundingException,Failure {
        kingdoms.validatePlayer(player); var data=data();
        Kingdom actor=data.playerKingdom(player.getUUID());
        if (actor==null) throw new Failure("permission");
        if (!actor.id().equals(request.kingdom())) throw new Failure("stale");
        UUID target=request.target();
        if (target==null||target.equals(actor.id())||data.byId(target)==null) throw new Failure("target");
        Pair pair=new Pair(actor.id(),target); var entry=current(data,pair);
        Alliance alliance=entry==null?null:entry.alliance();
        War war=entry==null?null:entry.war();
        Set<UUID> declarations=new HashSet<>(entry==null?Set.of():entry.declarations());
        Invitation invitation=entry==null?null:entry.invitation();
        if (!KingdomPermissions.allows(data,actor.id(),player.getUUID(),permission(request.action(),invitation))) throw new Failure("permission");
        if (!Objects.equals(request.revision(),entry==null?null:entry.revision())
                ||!Objects.equals(request.invitation(),invitation==null?null:invitation.id())) throw new Failure("stale");
        Relation relation=entry==null?Relation.NEUTRAL:entry.relation();
        long now=clock.getAsLong(),nextProposalAt=entry==null?0:entry.nextProposalAt();
        var kind=RELATION_CHANGED;
        ProposalType type=proposal(request.action());
        if (type!=null) {
            if (invitation!=null||!type.valid(relation)) throw new Failure("conflict");
            if (now<nextProposalAt) throw new Failure("cooldown");
            invitation=new Invitation(UUID.randomUUID(),actor.id(),target,now,Math.addExact(now,lifetime.getAsLong()),player.getUUID(),type);
            nextProposalAt=Math.addExact(now,DiplomacyConfig.proposalCooldownMillis); kind=PROPOSED;
        } else switch (request.action()) {
            case ACCEPT,REJECT,CANCEL -> {
                if (invitation==null||now<invitation.createdAt()||now>=invitation.expiresAt()) throw new Failure("stale");
                UUID representative=request.action()==Action.CANCEL?invitation.sender():invitation.recipient();
                if (!actor.id().equals(representative)) throw new Failure("permission");
                if (data.byId(invitation.sender())==null||data.byId(invitation.recipient())==null
                        ||!KingdomPermissions.allows(data,invitation.sender(),invitation.inviter(),invitation.type().permission())) throw new Failure("stale");
                if (!invitation.type().valid(relation)) throw new Failure("conflict");
                if (request.action()==Action.ACCEPT) {
                    switch (invitation.type()) {
                        case ALLIANCE -> alliance=new Alliance(UUID.randomUUID(),now,Relation.ALLIED);
                        case NON_AGGRESSION -> alliance=new Alliance(UUID.randomUUID(),now,Relation.NON_AGGRESSION);
                        case END_ALLIANCE, END_NON_AGGRESSION -> alliance=null;
                        case PEACE -> { war=null; declarations.clear(); }
                    }
                    kind=ACCEPTED;
                } else kind=request.action()==Action.REJECT?REJECTED:CANCELLED;
                invitation=null;
            }
            case HOSTILE -> {
                if (relation!=Relation.NEUTRAL&&relation!=Relation.HOSTILE||!declarations.add(actor.id())) throw new Failure("conflict");
                invitation=null;
            }
            case NEUTRAL -> {
                if (war!=null||!declarations.remove(actor.id())) throw new Failure("conflict");
                invitation=null;
            }
            case DECLARE_WAR -> {
                // Existing treaties must first be ended by an accepted termination proposal.
                if (relation!=Relation.NEUTRAL&&relation!=Relation.HOSTILE) throw new Failure("war_treaty");
                war=new War(UUID.randomUUID(),now,actor.id()); declarations.clear(); invitation=null; kind=WAR_DECLARED;
            }
            default -> throw new Failure("conflict");
        }
        data.diplomacy.put(pair,alliance,declarations,invitation,war,nextProposalAt,kind);
    }
    public State handle(ServerPlayer player,Request request) {
        String result=""; boolean success=false;
        try {
            kingdoms.validatePlayer(player);
            if (request.action()!=Action.VIEW) { act(player,request); success=true; result="diplomacy.regnum-nations.updated"; }
            return view(player,request,result,success);
        } catch (FoundingException e) { result=e.translationKey(); }
        catch (Failure e) { result=e.translationKey(); }
        try { return view(player,request,result,false); }
        catch (FoundingException|Failure e) { return error(request.sequence(),result); }
    }
    private State view(ServerPlayer player,Request request,String result,boolean success) throws FoundingException,Failure {
        kingdoms.validatePlayer(player); var data=data();
        Kingdom own=data.playerKingdom(player.getUUID());
        Set<KingdomPermission> permissions=EnumSet.noneOf(KingdomPermission.class);
        for (var permission:List.of(KingdomPermission.VIEW_DIPLOMACY,KingdomPermission.PROPOSE_ALLIANCE,KingdomPermission.MANAGE_TREATIES,
                KingdomPermission.DECLARE_WAR,KingdomPermission.NEGOTIATE_PEACE))
            if (own!=null&&KingdomPermissions.allows(data,own.id(),player.getUUID(),permission)) permissions.add(permission);
        boolean manage=permissions.stream().anyMatch(p -> p!=KingdomPermission.VIEW_DIPLOMACY);
        if (own!=null&&!permissions.contains(KingdomPermission.VIEW_DIPLOMACY)) throw new Failure("permission");
        Tab tab=manage?request.tab():Tab.KINGDOMS; List<InterstatePayloads.Entry> rows=new ArrayList<>();
        var sorted=data.kingdoms().stream().sorted(Comparator.comparing((Kingdom k) -> k.name().key()).thenComparing(Kingdom::id)).toList();
        for (Kingdom other:sorted) {
            if (own!=null&&own.id().equals(other.id())) continue;
            var entry=own==null?null:current(data,new Pair(own.id(),other.id()));
            Invitation invitation=entry==null||!manage?null:entry.invitation();
            boolean incoming=invitation!=null&&invitation.recipient().equals(own.id());
            if (tab==Tab.INCOMING&&(invitation==null||!incoming)||tab==Tab.OUTGOING&&(invitation==null||incoming)) continue;
            rows.add(new InterstatePayloads.Entry(other.id(),other.name().display(),entry==null?Relation.NEUTRAL:entry.relation(),
                    entry==null?null:entry.revision(),invitation==null?null:invitation.id(),incoming,invitation==null?0:invitation.expiresAt(),
                    entry!=null&&entry.declarations().contains(own.id()),entry!=null&&entry.declarations().contains(other.id()),
                    invitation==null?null:invitation.type(),invitation==null?0:invitation.createdAt(),entry==null?0:entry.war()!=null?entry.war().declaredAt():entry.alliance()!=null?entry.alliance().acceptedAt():0,
                    entry==null?0:entry.nextProposalAt(),entry==null||entry.war()==null?null:entry.war().declarer()));
        }
        int page=Math.clamp(request.page(),0,Math.max(0,(rows.size()-1)/InterstatePayloads.PAGE_SIZE));
        int start=page*InterstatePayloads.PAGE_SIZE,end=Math.min(rows.size(),start+InterstatePayloads.PAGE_SIZE);
        return new State(request.sequence(),own==null?null:own.id(),own==null?"":own.name().display(),manage,tab,page,rows.size(),rows.subList(start,end),result,success,permissions);
    }
    public static State error(int sequence,String key) { return new State(sequence,null,"",false,Tab.KINGDOMS,0,0,List.of(),key,false,Set.of()); }

    private static DiplomacyApi.Snapshot snapshot(Pair pair,DiplomacyData.Entry entry) {
        var treaty=entry==null?null:entry.alliance(); var war=entry==null?null:entry.war(); var proposal=entry==null?null:entry.invitation();
        return new DiplomacyApi.Snapshot(pair.first(),pair.second(),DiplomacyApi.Relation.valueOf(entry==null?"NEUTRAL":entry.relation().name()),
                Optional.ofNullable(entry==null?null:entry.revision()),
                Optional.ofNullable(treaty==null?null:new DiplomacyApi.Treaty(treaty.id(),DiplomacyApi.Relation.valueOf(treaty.kind().name()),treaty.acceptedAt())),
                Optional.ofNullable(war==null?null:new DiplomacyApi.War(war.id(),war.declarer(),war.declaredAt())),
                entry==null?Set.of():entry.declarations(),
                Optional.ofNullable(proposal==null?null:new DiplomacyApi.Proposal(proposal.id(),proposal.sender(),proposal.recipient(),proposal.inviter(),
                        DiplomacyApi.ProposalType.valueOf(proposal.type().name()),proposal.createdAt(),proposal.expiresAt())),entry==null?0:entry.nextProposalAt());
    }
    public ApiResult<DiplomacyApi.Snapshot> snapshot(UUID first,UUID second) {
        var data=kingdoms.data(); // Enforces the server thread even for invalid arguments.
        if (first==null||second==null||first.equals(second)) return ApiResult.failure(ApiResult.Status.INVALID);
        if (!data.available()||!data.diplomacy.available()) return ApiResult.failure(ApiResult.Status.UNAVAILABLE);
        if (data.byId(first)==null||data.byId(second)==null) return ApiResult.failure(ApiResult.Status.MISSING);
        Pair pair=new Pair(first,second); return ApiResult.valid(snapshot(pair,current(data,pair)));
    }
    public ApiResult<List<DiplomacyApi.Snapshot>> snapshots(UUID kingdom) {
        var data=kingdoms.data();
        if (kingdom==null) return ApiResult.failure(ApiResult.Status.INVALID);
        if (!data.available()||!data.diplomacy.available()) return ApiResult.failure(ApiResult.Status.UNAVAILABLE);
        if (data.byId(kingdom)==null) return ApiResult.failure(ApiResult.Status.MISSING);
        var result=new ArrayList<DiplomacyApi.Snapshot>();
        for (Kingdom other:data.kingdoms().stream().sorted(Comparator.comparing(Kingdom::id)).toList())
            if (!kingdom.equals(other.id())) result.add(snapshot(kingdom,other.id()).value().orElseThrow());
        return ApiResult.valid(List.copyOf(result));
    }
    private void changed(Pair pair,DiplomacyData.Entry before,DiplomacyData.Entry after,DiplomacyEvents.Kind kind) {
        schedule(pair,before,false); schedule(pair,after,true);
        var data=kingdoms.data(); data.setDirty(); data.politicalChanged();
        DiplomacyEvents.CHANGED.invoker().changed(server,new DiplomacyEvents.Change(kind,snapshot(pair,before),Optional.ofNullable(after==null?null:snapshot(pair,after))));
        Invitation proposal=kind==PROPOSED?after.invitation():before==null?null:before.invitation();
        String message=switch (kind) {
            case PROPOSED -> "proposed";
            case ACCEPTED -> switch (proposal.type()) {
                case ALLIANCE -> "allied";
                case NON_AGGRESSION -> "pact";
                case PEACE -> "peace";
                case END_ALLIANCE -> "alliance_ended";
                case END_NON_AGGRESSION -> "pact_ended";
            };
            case REJECTED -> "rejected";
            case CANCELLED -> "cancelled";
            case EXPIRED -> "expired";
            case REVOKED -> "revoked";
            case WAR_DECLARED -> "war";
            case KINGDOM_DISSOLVED -> "dissolved";
            default -> "relation";
        };
        boolean officials=kind==PROPOSED||kind==REJECTED||kind==CANCELLED||kind==EXPIRED||kind==REVOKED;
        Kingdom first=data.byId(pair.first()),second=data.byId(pair.second());
        Component firstName=first==null?text("dissolved_name"):Component.literal(first.name().display());
        Component secondName=second==null?text("dissolved_name"):Component.literal(second.name().display());
        if (kind==WAR_DECLARED&&after.war().declarer().equals(pair.second())) {
            Component swap=firstName; firstName=secondName; secondName=swap;
        }
        Component notification=text("notification."+message,firstName,secondName,
                proposal==null?Component.empty():text("proposal."+proposal.type().name().toLowerCase(Locale.ROOT)));
        Set<UUID> members=new HashSet<>(data.kingdomMembers(pair.first())); members.addAll(data.kingdomMembers(pair.second()));
        for (ServerPlayer player:server.getPlayerList().getPlayers()) {
            if (!members.contains(player.getUUID())) continue;
            if (officials) {
                var required=proposal==null?KingdomPermission.MANAGE_TREATIES:proposal.type().permission();
                if (!KingdomPermissions.allows(data,pair.first(),player.getUUID(),required)&&!KingdomPermissions.allows(data,pair.second(),player.getUUID(),required)) continue;
            }
            player.sendSystemMessage(notification);
        }
    }
    private static Component text(String key,Object... args) { return Component.translatable("diplomacy.regnum-nations."+key,args); }

    // Retained v0.8 channels use the same authoritative transitions. Legacy termination now proposes
    // termination; old clients can never bypass treaty/war rules or consume a non-alliance proposal.
    private static Request upgrade(dev.sakhno.regnum.nations.networking.DiplomacyPayloads.Request request) {
        return new Request(request.sequence(),Action.valueOf(request.action().name()),Tab.valueOf(request.tab().name()),request.page(),request.kingdom(),request.target(),request.revision(),request.invitation());
    }
    public void act(ServerPlayer player,dev.sakhno.regnum.nations.networking.DiplomacyPayloads.Request request) throws FoundingException,Failure {
        var data=data();
        if (request.kingdom()!=null&&request.target()!=null&&!request.kingdom().equals(request.target())) {
            var entry=data.diplomacy.get(new Pair(request.kingdom(),request.target()));
            if (entry!=null&&entry.invitation()!=null&&entry.invitation().type()!=ProposalType.ALLIANCE) throw new Failure("upgrade_client");
        }
        act(player,upgrade(request));
    }
    public dev.sakhno.regnum.nations.networking.DiplomacyPayloads.State handle(ServerPlayer player,dev.sakhno.regnum.nations.networking.DiplomacyPayloads.Request request) {
        String result=""; boolean success=false;
        try { if (request.action()!=dev.sakhno.regnum.nations.networking.DiplomacyPayloads.Action.VIEW) { act(player,request); success=true; result="diplomacy.regnum-nations.updated"; } }
        catch (FoundingException e) { result=e.translationKey(); } catch (Failure e) { result=e.translationKey(); }
        State state;
        try { state=view(player,upgrade(request),result,success); } catch (FoundingException|Failure e) { state=error(request.sequence(),result.isEmpty()?"diplomacy.regnum-nations.unavailable":result); }
        var entries=new ArrayList<dev.sakhno.regnum.nations.networking.DiplomacyPayloads.Entry>();
        for (var e:state.entries()) entries.add(new dev.sakhno.regnum.nations.networking.DiplomacyPayloads.Entry(e.kingdom(),e.name(),
                e.relation()==Relation.AT_WAR?Relation.HOSTILE:e.relation()==Relation.NON_AGGRESSION?Relation.NEUTRAL:e.relation(),e.revision(),
                e.proposalType()==ProposalType.ALLIANCE?e.invitation():null,e.incoming(),e.expiresAt(),e.ownHostility(),e.theirHostility()));
        return new dev.sakhno.regnum.nations.networking.DiplomacyPayloads.State(state.sequence(),state.kingdom(),state.name(),state.manage(),
                dev.sakhno.regnum.nations.networking.DiplomacyPayloads.Tab.valueOf(state.tab().name()),state.page(),state.matches(),entries,state.result(),state.success());
    }
    public static dev.sakhno.regnum.nations.networking.DiplomacyPayloads.State legacyError(int sequence,String key) {
        return new dev.sakhno.regnum.nations.networking.DiplomacyPayloads.State(sequence,null,"",false,dev.sakhno.regnum.nations.networking.DiplomacyPayloads.Tab.KINGDOMS,0,0,List.of(),key,false);
    }
}
