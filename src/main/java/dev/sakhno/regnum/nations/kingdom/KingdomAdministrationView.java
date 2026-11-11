package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.networking.KingdomAdministrationPayloads.*;
import java.util.*;
import java.util.function.LongSupplier;
import net.minecraft.server.level.ServerPlayer;
import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;
import static dev.sakhno.regnum.nations.kingdom.KingdomPermission.*;
import static dev.sakhno.regnum.nations.networking.KingdomAdministrationPayloads.PAGE_SIZE;

/** One-use UI sessions expose only the actor's kingdom roster and their city's inbox. */
public final class KingdomAdministrationView {
    static final long SESSION_NANOS=60_000_000_000L;
    private final KingdomService kingdoms;
    private final KingdomSavedData data;
    private final KingdomAdministration admin;
    private final LongSupplier clock;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private record Session(ServerPlayer actor,UUID token,UUID kingdom,UUID ruler,Tab tab,List<Entry> rows,UUID transferTarget,long at) { }
    KingdomAdministrationView(KingdomService kingdoms,LongSupplier clock) {
        this.kingdoms=kingdoms; this.data=kingdoms.data(); this.admin=kingdoms.administration(); this.clock=clock;
    }
    void disconnect(UUID player) { sessions.remove(player); }
    static State error(int sequence,String result) { return new State(sequence,null,null,"",null,0,0,Tab.INBOX,0,0,List.of(),null,result,false); }
    public State handle(ServerPlayer actor,Request request) {
        try {
            kingdoms.validatePlayer(actor);
            UUID transfer=null;
            if (request.action()!=Action.VIEW) {
                mutate(actor,request);
                if (request.action()==Action.PREPARE_TRANSFER) transfer=request.target();
            }
            return snapshot(actor,request,transfer,request.action()==Action.VIEW?"":"kingdom_admin.regnum-nations.done."+request.action().name().toLowerCase(Locale.ROOT),request.action()!=Action.VIEW);
        } catch (FoundingException e) {
            sessions.remove(actor.getUUID());
            try { return snapshot(actor,new Request(request.sequence(),Action.VIEW,request.tab(),request.query(),0,null,null,null),null,e.translationKey(),false); }
            catch (FoundingException unavailable) { return error(request.sequence(),e.translationKey()); }
        }
    }
    private void mutate(ServerPlayer actor,Request request) throws FoundingException {
        Session session=sessions.remove(actor.getUUID()); long age=session==null?-1:clock.getAsLong()-session.at();
        Kingdom current=data.playerKingdom(actor.getUUID()); UUID realm=current==null?null:current.id();
        if (session==null||session.actor()!=actor||!Objects.equals(request.token(),session.token())||age<0||age>=SESSION_NANOS
                ||request.tab()!=session.tab()||!Objects.equals(request.kingdom(),session.kingdom())||!Objects.equals(realm,session.kingdom())
                ||current!=null&&!current.rulerId().equals(session.ruler())) throw new FoundingException(KINGDOM_UI_STALE);
        Entry entry=session.rows().stream().filter(e -> e.id().equals(request.target())).findFirst().orElseThrow(() -> new FoundingException(KINGDOM_UI_STALE));
        if (request.action()==Action.TRANSFER) {
            if (!entry.id().equals(session.transferTarget())) throw new FoundingException(KINGDOM_CONFIRMATION_REQUIRED);
        } else if (!entry.actions().contains(request.action())) throw new FoundingException(KINGDOM_ROLE_FORBIDDEN);
        if (entry.role()!=null&&data.kingdomRole(realm,entry.id())!=entry.role()) throw new FoundingException(KINGDOM_UI_STALE);
        switch (request.action()) {
            case INVITE -> admin.inviteCity(actor,realm,entry.id());
            case ACCEPT,REJECT -> admin.answer(actor,entry.id(),request.action()==Action.ACCEPT);
            case CANCEL -> admin.cancel(actor,entry.id());
            case LEAVE -> admin.leaveCity(actor,entry.id());
            case REMOVE -> admin.removeCity(actor,realm,entry.id());
            case MEMBER,OFFICER,CHANCELLOR -> admin.assignRole(actor,realm,entry.id(),KingdomRole.valueOf(request.action().name()));
            case PREPARE_TRANSFER -> {
                if (data.kingdomRole(realm,actor.getUUID())!=KingdomRole.RULER||data.kingdomRole(realm,entry.id())==null||actor.getUUID().equals(entry.id())) throw new FoundingException(KINGDOM_ROLE_FORBIDDEN);
            }
            case TRANSFER -> admin.transfer(actor,realm,entry.id());
            default -> throw new FoundingException(KINGDOM_ROLE_FORBIDDEN);
        }
    }
    private boolean allows(UUID realm,ServerPlayer actor,KingdomPermission permission) { return KingdomPermissions.allows(data,realm,actor.getUUID(),permission); }
    private State snapshot(ServerPlayer actor,Request request,UUID transfer,String result,boolean success) throws FoundingException {
        kingdoms.validatePlayer(actor);
        Kingdom state=data.playerKingdom(actor.getUUID()); UUID realm=state==null?null:state.id();
        if (request.action()==Action.VIEW&&request.kingdom()!=null&&!request.kingdom().equals(realm)) throw new FoundingException(KINGDOM_NOT_MEMBER);
        Settlement own=data.citizenCity(actor.getUUID());
        KingdomRole role=data.kingdomRole(realm,actor.getUUID()); Tab tab=request.tab();
        if (state==null&&tab!=Tab.INBOX) tab=Tab.INBOX;
        if ((tab==Tab.AVAILABLE||tab==Tab.OUTGOING)&&!allows(realm,actor,INVITE_CITY)) tab=Tab.GOVERNMENT;
        List<Entry> entries=new ArrayList<>();
        switch (tab) {
            case GOVERNMENT -> {
                for (UUID member:data.kingdomMembers(realm)) {
                    KingdomRole target=data.kingdomRole(realm,member); List<Action> actions=new ArrayList<>();
                    for (KingdomRole assigned:List.of(KingdomRole.MEMBER,KingdomRole.OFFICER,KingdomRole.CHANCELLOR))
                        if (assigned!=target&&KingdomPermissions.canAssign(role,target,assigned)) actions.add(Action.valueOf(assigned.name()));
                    if (role==KingdomRole.RULER&&!actor.getUUID().equals(member)) actions.add(Action.PREPARE_TRANSFER);
                    var online=actor.serverLevel().getServer().getPlayerList().getPlayer(member);
                    String name=online==null?"":online.getGameProfile().getName();
                    var cache=actor.serverLevel().getServer().getProfileCache();
                    if (online==null&&cache!=null) name=cache.get(member).map(p -> p.getName()).orElse("");
                    entries.add(new Entry(member,name,member.toString(),target,online!=null,actions));
                }
            }
            case CITIES,AVAILABLE -> {
                for (Settlement city:data.settlements()) {
                    boolean affiliated=Objects.equals(realm,city.sovereign())&&realm!=null;
                    if (tab==Tab.CITIES?!affiliated:city.sovereign()!=null) continue;
                    List<Action> actions=new ArrayList<>();
                    if (tab==Tab.AVAILABLE) {
                        if (data.cityInvitations().stream().noneMatch(i -> i.kingdom().equals(realm)&&i.city().equals(city.id()))) actions.add(Action.INVITE);
                    } else if (!state.capitalSettlementId().equals(city.id())&&!data.isCitizen(city.id(),state.rulerId())) {
                        if (allows(realm,actor,REMOVE_CITY)) actions.add(Action.REMOVE);
                        if (city.ownerId().equals(actor.getUUID())) actions.add(Action.LEAVE);
                    }
                    entries.add(new Entry(city.id(),city.name().display(),city.ownerId().toString(),null,false,actions));
                }
            }
            case INBOX,OUTGOING -> {
                for (var invitation:data.cityInvitations()) {
                    Settlement city=data.settlement(invitation.city()); Kingdom inviting=data.byId(invitation.kingdom());
                    if (city==null||inviting==null) continue;
                    boolean inbox=tab==Tab.INBOX;
                    if (inbox?(own==null||!own.ownerId().equals(actor.getUUID())||!own.id().equals(city.id())):!invitation.kingdom().equals(realm)) continue;
                    entries.add(new Entry(invitation.id(),inbox?inviting.name().display():city.name().display(),inbox?city.name().display():inviting.name().display(),null,false,
                            inbox?List.of(Action.ACCEPT,Action.REJECT):List.of(Action.CANCEL)));
                }
            }
        }
        String search=request.query().strip().toLowerCase(Locale.ROOT);
        entries.removeIf(e -> !(e.name()+" "+e.detail()+" "+e.id()).toLowerCase(Locale.ROOT).contains(search));
        entries.sort(Comparator.comparingInt((Entry e) -> e.role()==null?4:e.role().ordinal()).thenComparing(e -> e.name().toLowerCase(Locale.ROOT)).thenComparing(Entry::id));
        int count=entries.size(),page=Math.clamp(request.page(),0,Math.max(0,(count-1)/PAGE_SIZE));
        List<Entry> rows=List.copyOf(entries.subList(page*PAGE_SIZE,Math.min(count,(page+1)*PAGE_SIZE)));
        UUID token=UUID.randomUUID(); long now=clock.getAsLong(); sessions.values().removeIf(s -> now-s.at()<0||now-s.at()>=SESSION_NANOS);
        sessions.put(actor.getUUID(),new Session(actor,token,realm,state==null?null:state.rulerId(),tab,rows,transfer,now));
        int cities=(int)data.settlements().stream().filter(c -> realm!=null&&realm.equals(c.sovereign())).count();
        return new State(request.sequence(),token,realm,state==null?"":state.name().display(),role,cities,data.kingdomMembers(realm).size(),tab,page,count,rows,transfer,result,success);
    }
}
