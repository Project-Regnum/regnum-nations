package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.networking.CitizenManagementPayloads.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.server.level.ServerPlayer;
import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;
import static dev.sakhno.regnum.nations.networking.CitizenManagementPayloads.PAGE_SIZE;

/** Presentation/session adapter only. Existing citizenship services authorize and perform all mutations. */
public final class CitizenManagement {
    static final long SNAPSHOT_NANOS=60_000_000_000L;
    private final KingdomService kingdoms;
    private final KingdomSavedData data;
    private final CitizenshipService citizens;
    private final LongSupplier clock;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private record Session(ServerPlayer player,UUID token,UUID city,long revision,Tab tab,List<Entry> entries,long at) { }

    CitizenManagement(KingdomService kingdoms,CitizenshipService citizens,LongSupplier clock) {
        this.kingdoms=kingdoms; this.data=kingdoms.data(); this.citizens=citizens; this.clock=clock;
    }
    void disconnect(UUID player) { sessions.remove(player); }

    public State handle(ServerPlayer actor,Request request) {
        String result=""; boolean success=false;
        try {
            kingdoms.validatePlayer(actor);
            if (request.action()!=Action.VIEW) { mutate(actor,request); success=true; result="citizen_ui.regnum-nations.done."+request.action().name().toLowerCase(Locale.ROOT); }
            return snapshot(actor,request.sequence(),request.action()==Action.VIEW?request.city():null,request.tab(),request.query(),request.page(),result,success);
        } catch (FoundingException e) {
            result=e.translationKey();
            // Rebuild only the actor's current view. Never retain a foreign roster after an error/removal.
            try { return snapshot(actor,request.sequence(),null,request.tab(),request.query(),0,result,false); }
            catch (FoundingException inaccessible) {
                try { return snapshot(actor,request.sequence(),null,Tab.MEMBERS,request.query(),0,result,false); }
                catch (FoundingException unavailable) { sessions.remove(actor.getUUID()); return error(request.sequence(),result); }
            }
        }
    }
    static State error(int sequence,String result) { return new State(sequence,null,null,"",0,false,false,false,Tab.MEMBERS,0,0,List.of(),result,false); }

    private void mutate(ServerPlayer actor,Request request) throws FoundingException {
        Session session=sessions.remove(actor.getUUID());
        Settlement city=data.citizenCity(actor.getUUID()); UUID cityId=city==null?null:city.id();
        long age=session==null?-1:clock.getAsLong()-session.at();
        if (session==null||session.player()!=actor||!session.token().equals(request.token())||age<0||age>=SNAPSHOT_NANOS
                ||session.tab()!=request.tab()||!Objects.equals(session.city(),cityId)||!Objects.equals(request.city(),cityId)
                ||cityId!=null&&data.membershipRevision(cityId)!=session.revision()) throw new FoundingException(CITIZEN_UI_STALE);
        if (request.action()==Action.LEAVE) {
            if (city==null||request.target()!=null) throw new FoundingException(CITIZEN_UI_TARGET);
            citizens.leave(actor); return;
        }
        Entry entry=session.entries().stream().filter(e -> e.id().equals(request.target())).findFirst().orElseThrow(() -> new FoundingException(CITIZEN_UI_TARGET));
        switch (request.action()) {
            case PROMOTE,DEMOTE,KICK -> {
                if (session.tab()!=Tab.MEMBERS) throw new FoundingException(CITIZEN_UI_TARGET);
                // Do not trust the displayed/client role. Check current policy even for a forged action.
                if (!citizens.permits(actor,CityPermission.MANAGE_MEMBERS)) throw new FoundingException(NOT_OWNER);
                if (request.action()==Action.KICK) citizens.kick(actor,entry.player());
                else {
                    CityRole expected=request.action()==Action.PROMOTE?CityRole.CITIZEN:CityRole.OFFICER;
                    if (data.role(cityId,entry.player())!=expected) throw new FoundingException(CITIZEN_UI_TARGET);
                    citizens.assignRole(actor,entry.player(),request.action()==Action.PROMOTE?CityRole.OFFICER:CityRole.CITIZEN);
                }
            }
            case INVITE -> {
                if (session.tab()!=Tab.ONLINE) throw new FoundingException(CITIZEN_UI_TARGET);
                var online=actor.serverLevel().getServer().getPlayerList().getPlayer(entry.player());
                if (online==null) throw new FoundingException(PLAYER_OFFLINE);
                Settlement invited=citizens.invite(actor,online);
                online.displayClientMessage(net.minecraft.network.chat.Component.translatable("citizen.regnum-nations.invitation",actor.getGameProfile().getName(),
                        invited.name().display(),invited.id().toString(),invited.id().toString()),false);
            }
            case ACCEPT,DECLINE -> {
                if (session.tab()!=Tab.INBOX) throw new FoundingException(CITIZEN_UI_TARGET);
                var invitation=citizens.pending(actor,false).stream().filter(i -> i.id().equals(entry.id())).findFirst()
                        .orElseThrow(() -> new FoundingException(CITIZEN_UI_STALE));
                if (request.action()==Action.ACCEPT) citizens.accept(actor,invitation.city()); else citizens.decline(actor,invitation.city());
            }
            default -> throw new FoundingException(CITIZEN_UI_TARGET);
        }
    }

    State snapshot(ServerPlayer actor,int sequence,UUID requestedCity,Tab tab,String query,int requestedPage,String result,boolean success) throws FoundingException {
        kingdoms.validatePlayer(actor);
        Settlement city=data.citizenCity(actor.getUUID()); UUID cityId=city==null?null:city.id();
        if (requestedCity!=null&&!requestedCity.equals(cityId)) throw new FoundingException(NOT_CITIZEN);
        boolean invite=CityPermissions.allows(data,city,actor.getUUID(),CityPermission.INVITE);
        boolean manage=CityPermissions.allows(data,city,actor.getUUID(),CityPermission.MANAGE_MEMBERS);
        if ((tab==Tab.ONLINE||tab==Tab.OUTGOING)&&!invite) {
            if (!success) throw new FoundingException(NOT_OWNER);
            tab=Tab.MEMBERS;
        }
        List<Entry> entries=new ArrayList<>();
        switch (tab) {
            case MEMBERS -> {
                if (city!=null) for (UUID member:citizens.list(actor)) entries.add(entry(actor,member,member,data.role(cityId,member),cityId,city.name().display(),0));
            }
            case ONLINE -> {
                for (var online:actor.serverLevel().getServer().getPlayerList().getPlayers())
                    if (online.isAlive()&&!online.isRemoved()&&!online.isSpectator()&&eligible(online.getUUID()))
                        entries.add(entry(actor,online.getUUID(),online.getUUID(),null,null,"",0));
            }
            case INBOX,OUTGOING -> {
                for (var invitation:citizens.pending(actor,tab==Tab.OUTGOING)) {
                    UUID player=tab==Tab.INBOX?invitation.inviter():invitation.recipient();
                    entries.add(entry(actor,invitation.id(),player,null,invitation.city(),data.settlement(invitation.city()).name().display(),invitation.remainingSeconds()));
                }
            }
        }
        String search=query.strip().toLowerCase(Locale.ROOT);
        entries.removeIf(e -> !(e.name()+" "+e.player()+" "+e.cityName()).toLowerCase(Locale.ROOT).contains(search));
        entries.sort(Comparator.comparingInt((Entry e) -> e.role()==null?3:e.role().ordinal())
                .thenComparing(e -> e.name().toLowerCase(Locale.ROOT)).thenComparing(Entry::id));
        int matches=entries.size(); int page=Math.clamp(requestedPage,0,Math.max(0,(matches-1)/PAGE_SIZE));
        List<Entry> rows=List.copyOf(entries.subList(page*PAGE_SIZE,Math.min(matches,(page+1)*PAGE_SIZE)));
        long now=clock.getAsLong(); sessions.values().removeIf(s -> now-s.at()<0||now-s.at()>=SNAPSHOT_NANOS);
        UUID token=UUID.randomUUID(); sessions.put(actor.getUUID(),new Session(actor,token,cityId,cityId==null?0:data.membershipRevision(cityId),tab,rows,now));
        return new State(sequence,token,cityId,city==null?"":city.name().display(),city==null?0:data.citizens(cityId).size(),invite,manage,
                city!=null&&data.role(cityId,actor.getUUID())!=CityRole.OWNER,tab,page,matches,rows,result,success);
    }
    private boolean eligible(UUID player) throws FoundingException {
        try { return data.citizenCity(player)==null; }
        catch (FoundingException e) { if (e.reason()==DOMAIN_REPAIR) return false; throw e; }
    }
    private Entry entry(ServerPlayer viewer,UUID id,UUID player,CityRole role,UUID city,String cityName,int seconds) {
        var server=viewer.serverLevel().getServer(); var online=server.getPlayerList().getPlayer(player);
        String name=online==null?"":online.getGameProfile().getName();
        if (online==null&&server.getProfileCache()!=null) name=server.getProfileCache().get(player).map(p -> p.getName()).orElse("");
        return new Entry(id,player,name,role,online!=null,city,cityName,seconds);
    }
}
