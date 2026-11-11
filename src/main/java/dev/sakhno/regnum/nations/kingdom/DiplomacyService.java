package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.kingdom.DiplomacyData.*;
import dev.sakhno.regnum.nations.networking.DiplomacyPayloads;
import dev.sakhno.regnum.nations.networking.DiplomacyPayloads.*;
import java.util.*;
import java.util.function.LongSupplier;
import net.minecraft.server.level.ServerPlayer;

/** All reads and transitions run on the owning server thread, with current founder authority. */
public final class DiplomacyService {
    private final KingdomService kingdoms;
    private final LongSupplier clock,lifetime;
    DiplomacyService(KingdomService kingdoms,LongSupplier clock,LongSupplier lifetime) {
        this.kingdoms=kingdoms; this.clock=clock; this.lifetime=lifetime;
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
    private DiplomacyData.Entry current(KingdomSavedData data,Pair pair) {
        if (data.diplomacy.expire(pair,clock.getAsLong())) data.setDirty();
        return data.diplomacy.get(pair);
    }
    public Relation relation(UUID first,UUID second) throws FoundingException,Failure {
        var data=data();
        if (first==null||second==null||first.equals(second)||data.byId(first)==null||data.byId(second)==null) throw new Failure("target");
        var entry=current(data,new Pair(first,second)); return entry==null?Relation.NEUTRAL:entry.relation();
    }
    /** expected revision and invitation identity reject stale confirmations, replay and ABA changes. */
    public void act(ServerPlayer player,Request request) throws FoundingException,Failure {
        kingdoms.validatePlayer(player); var data=data();
        Kingdom actor=data.byFounder(player.getUUID());
        if (!KingdomPermission.MANAGE_DIPLOMACY.allows(data,actor,player.getUUID())) throw new Failure("permission");
        if (!actor.id().equals(request.kingdom())) throw new Failure("stale");
        UUID target=request.target();
        if (target==null||target.equals(actor.id())||data.byId(target)==null) throw new Failure("target");
        Pair pair=new Pair(actor.id(),target); var entry=current(data,pair);
        if (!Objects.equals(request.revision(),entry==null?null:entry.revision())) throw new Failure("stale");
        Alliance alliance=entry==null?null:entry.alliance();
        Set<UUID> declarations=new HashSet<>(entry==null?Set.of():entry.declarations());
        Invitation invitation=entry==null?null:entry.invitation();
        if (!Objects.equals(request.invitation(),invitation==null?null:invitation.id())) throw new Failure("stale");
        switch (request.action()) {
            case INVITE -> {
                if (alliance!=null||!declarations.isEmpty()||invitation!=null) throw new Failure("conflict");
                long now=clock.getAsLong();
                invitation=new Invitation(UUID.randomUUID(),actor.id(),target,now,Math.addExact(now,lifetime.getAsLong()));
            }
            case ACCEPT,REJECT,CANCEL -> {
                if (invitation==null||clock.getAsLong()<invitation.createdAt()||clock.getAsLong()>=invitation.expiresAt()) throw new Failure("stale");
                UUID representative=request.action()==Action.CANCEL?invitation.sender():invitation.recipient();
                if (!actor.id().equals(representative)) throw new Failure("permission");
                if (request.action()==Action.ACCEPT) {
                    if (alliance!=null||!declarations.isEmpty()) throw new Failure("conflict");
                    alliance=new Alliance(UUID.randomUUID(),clock.getAsLong());
                }
                invitation=null;
            }
            case TERMINATE -> {
                if (alliance==null) throw new Failure("conflict");
                alliance=null; // Termination returns to neutral; it never creates a declaration.
            }
            case HOSTILE -> {
                if (!declarations.add(actor.id())) throw new Failure("conflict");
                alliance=null; invitation=null;
            }
            case NEUTRAL -> {
                if (!declarations.remove(actor.id())) throw new Failure("conflict");
            }
            default -> throw new Failure("conflict");
        }
        data.diplomacy.put(pair,alliance,declarations,invitation); data.setDirty();
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
        Kingdom own=data.byFounder(player.getUUID());
        if (own==null) own=data.sovereign(data.citizenCity(player.getUUID()));
        boolean manage=KingdomPermission.MANAGE_DIPLOMACY.allows(data,own,player.getUUID());
        // Only representatives see pending requests. Public kingdom names and relations are safe.
        Tab tab=manage?request.tab():Tab.KINGDOMS; List<DiplomacyPayloads.Entry> rows=new ArrayList<>();
        var sorted=data.kingdoms().stream().sorted(Comparator.comparing((Kingdom k) -> k.name().key()).thenComparing(Kingdom::id)).toList();
        for (Kingdom other:sorted) {
            if (own!=null&&own.id().equals(other.id())) continue;
            var entry=own==null?null:current(data,new Pair(own.id(),other.id()));
            Invitation invitation=entry==null||!manage?null:entry.invitation();
            boolean incoming=invitation!=null&&invitation.recipient().equals(own.id());
            if (tab==Tab.INCOMING&&(invitation==null||!incoming)||tab==Tab.OUTGOING&&(invitation==null||incoming)) continue;
            rows.add(new DiplomacyPayloads.Entry(other.id(),other.name().display(),entry==null?Relation.NEUTRAL:entry.relation(),
                    entry==null?null:entry.revision(),invitation==null?null:invitation.id(),incoming,invitation==null?0:invitation.expiresAt(),
                    entry!=null&&entry.declarations().contains(own.id()),entry!=null&&entry.declarations().contains(other.id())));
        }
        int page=Math.clamp(request.page(),0,Math.max(0,(rows.size()-1)/DiplomacyPayloads.PAGE_SIZE));
        int start=page*DiplomacyPayloads.PAGE_SIZE,end=Math.min(rows.size(),start+DiplomacyPayloads.PAGE_SIZE);
        return new State(request.sequence(),own==null?null:own.id(),own==null?"":own.name().display(),manage,tab,page,rows.size(),rows.subList(start,end),result,success);
    }
    public static State error(int sequence,String key) { return new State(sequence,null,"",false,Tab.KINGDOMS,0,0,List.of(),key,false); }
}
