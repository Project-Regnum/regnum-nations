package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.RegnumNations;
import java.util.*;
import net.minecraft.nbt.*;

/** Embedded in the kingdom registry. Agreements, declarations and requests are distinct contracts. */
public final class DiplomacyData {
    public enum Relation { NEUTRAL, ALLIED, HOSTILE, NON_AGGRESSION, AT_WAR }
    public enum ProposalType {
        ALLIANCE, NON_AGGRESSION, PEACE, END_ALLIANCE, END_NON_AGGRESSION;
        public KingdomPermission permission() {
            return switch (this) {
                case ALLIANCE -> KingdomPermission.PROPOSE_ALLIANCE;
                case PEACE -> KingdomPermission.NEGOTIATE_PEACE;
                default -> KingdomPermission.MANAGE_TREATIES;
            };
        }
        public boolean valid(Relation relation) {
            return switch (this) {
                case ALLIANCE, NON_AGGRESSION -> relation==Relation.NEUTRAL;
                case PEACE -> relation==Relation.AT_WAR||relation==Relation.HOSTILE;
                case END_ALLIANCE -> relation==Relation.ALLIED;
                case END_NON_AGGRESSION -> relation==Relation.NON_AGGRESSION;
            };
        }
    }
    public record Pair(UUID first, UUID second) {
        public Pair {
            Objects.requireNonNull(first); Objects.requireNonNull(second);
            if (first.equals(second)) throw new IllegalArgumentException("Self diplomacy");
            if (first.compareTo(second)>0) { UUID swap=first; first=second; second=swap; }
        }
        boolean contains(UUID id) { return first.equals(id)||second.equals(id); }
    }
    public record Alliance(UUID id,long acceptedAt,Relation kind) {
        public Alliance(UUID id,long acceptedAt) { this(id,acceptedAt,Relation.ALLIED); }
    }
    public record War(UUID id,long declaredAt,UUID declarer) { }
    public record Invitation(UUID id,UUID sender,UUID recipient,long createdAt,long expiresAt,UUID inviter,ProposalType type) {
        public Invitation(UUID id,UUID sender,UUID recipient,long createdAt,long expiresAt,UUID inviter) {
            this(id,sender,recipient,createdAt,expiresAt,inviter,ProposalType.ALLIANCE);
        }
    }
    public record Entry(UUID revision,Alliance alliance,Set<UUID> declarations,Invitation invitation,War war,long nextProposalAt) {
        public Entry(UUID revision,Alliance alliance,Set<UUID> declarations,Invitation invitation) { this(revision,alliance,declarations,invitation,null,0); }
        public Entry { Objects.requireNonNull(revision); declarations=Set.copyOf(declarations); }
        public Relation relation() { return war!=null?Relation.AT_WAR:!declarations.isEmpty()?Relation.HOSTILE:alliance!=null?alliance.kind():Relation.NEUTRAL; }
    }
    private final Map<Pair,Entry> entries=new HashMap<>();
    private Tag unreadable;
    @FunctionalInterface interface Changes { void changed(Pair pair,Entry before,Entry after,dev.sakhno.regnum.nations.api.diplomacy.DiplomacyEvents.Kind kind); }
    private Changes changes=(pair,before,after,kind) -> { };
    void onChange(Changes listener) { changes=Objects.requireNonNull(listener); }
    Map<Pair,Entry> entries() { return Map.copyOf(entries); }
    boolean available() { return unreadable==null; }
    Entry get(Pair pair) { return entries.get(pair); }
    void put(Pair pair,Alliance alliance,Set<UUID> declarations,Invitation invitation) {
        Entry old=entries.get(pair);
        put(pair,alliance,declarations,invitation,old==null?null:old.war(),old==null?0:old.nextProposalAt(),dev.sakhno.regnum.nations.api.diplomacy.DiplomacyEvents.Kind.RELATION_CHANGED);
    }
    void put(Pair pair,Alliance alliance,Set<UUID> declarations,Invitation invitation,War war,long nextProposalAt,
            dev.sakhno.regnum.nations.api.diplomacy.DiplomacyEvents.Kind kind) {
        Entry after=new Entry(UUID.randomUUID(),alliance,declarations,invitation,war,nextProposalAt);
        Entry before=entries.put(pair,after); changes.changed(pair,before,after,kind);
    }
    void delete(UUID id) {
        for (Pair pair:List.copyOf(entries.keySet())) if (pair.contains(id)) {
            Entry before=entries.remove(pair);
            changes.changed(pair,before,null,dev.sakhno.regnum.nations.api.diplomacy.DiplomacyEvents.Kind.KINGDOM_DISSOLVED);
        }
    }
    boolean expire(Pair pair,long now) {
        Entry entry=entries.get(pair);
        if (entry==null||entry.invitation()==null||now<entry.invitation().expiresAt()) return false;
        put(pair,entry.alliance(),entry.declarations(),null,entry.war(),entry.nextProposalAt(),dev.sakhno.regnum.nations.api.diplomacy.DiplomacyEvents.Kind.EXPIRED); return true;
    }
    /** Revocation changes the pair revision deterministically and never revives on promotion. */
    boolean pruneUnauthorized(KingdomSavedData kingdoms) {
        if (!available()) return false;
        boolean changed=false;
        for (var item:Map.copyOf(entries).entrySet()) {
            Entry entry=item.getValue(); Invitation invitation=entry.invitation();
            if (invitation!=null&&!KingdomPermissions.allows(kingdoms,invitation.sender(),invitation.inviter(),invitation.type().permission())) {
                UUID revision=UUID.nameUUIDFromBytes(("revoked:"+entry.revision()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                Entry after=new Entry(revision,entry.alliance(),entry.declarations(),null,entry.war(),entry.nextProposalAt());
                entries.put(item.getKey(),after); changes.changed(item.getKey(),entry,after,dev.sakhno.regnum.nations.api.diplomacy.DiplomacyEvents.Kind.REVOKED); changed=true;
            }
        }
        return changed;
    }
    Tag save() {
        if (unreadable!=null) return unreadable.copy();
        ListTag result=new ListTag();
        entries.entrySet().stream().sorted(Comparator.comparing((Map.Entry<Pair,Entry> e) -> e.getKey().first()).thenComparing(e -> e.getKey().second())).forEach(item -> {
            Pair pair=item.getKey(); Entry entry=item.getValue(); CompoundTag e=new CompoundTag();
            e.putUUID("First",pair.first()); e.putUUID("Second",pair.second()); e.putUUID("Revision",entry.revision());
            if (entry.alliance()!=null) { e.putUUID("Alliance",entry.alliance().id()); e.putLong("AcceptedAt",entry.alliance().acceptedAt()); e.putString("TreatyKind",entry.alliance().kind().name()); }
            e.putLong("NextProposalAt",entry.nextProposalAt());
            if (entry.war()!=null) {
                e.putUUID("War",entry.war().id()); e.putLong("DeclaredAt",entry.war().declaredAt()); e.putUUID("WarDeclarer",entry.war().declarer());
            }
            ListTag declarations=new ListTag();
            entry.declarations().stream().sorted().forEach(id -> { CompoundTag d=new CompoundTag(); d.putUUID("Declarer",id); declarations.add(d); });
            e.put("Hostility",declarations);
            if (entry.invitation()!=null) {
                Invitation i=entry.invitation(); CompoundTag request=new CompoundTag();
                request.putUUID("Id",i.id()); request.putUUID("Sender",i.sender()); request.putUUID("Recipient",i.recipient());
                request.putString("Type",i.type().name()); request.putUUID("Inviter",i.inviter()); request.putLong("CreatedAt",i.createdAt()); request.putLong("ExpiresAt",i.expiresAt()); e.put("Invitation",request);
            }
            result.add(e);
        });
        return result;
    }
    /** Fail closed for diplomacy only, retaining the exact original tag for operator repair. */
    boolean load(Tag raw,KingdomSavedData kingdoms) {
        if (raw==null) return false;
        try {
            if (!(raw instanceof ListTag list)||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND) throw new IllegalArgumentException("Invalid diplomacy list");
            Set<Pair> seen=new HashSet<>(); Set<UUID> identities=new HashSet<>(); boolean repaired=false;
            for (Tag item:list) {
                CompoundTag e=(CompoundTag)item; Pair pair=new Pair(e.getUUID("First"),e.getUUID("Second"));
                if (!seen.add(pair)) throw new IllegalArgumentException("Duplicate diplomacy pair");
                UUID revision=e.getUUID("Revision"); Alliance alliance=null; Invitation invitation=null;
                if (!e.contains("Alliance")&&(e.contains("TreatyKind")||e.contains("AcceptedAt"))) throw new IllegalArgumentException("Treaty fields without treaty");
                if (!e.contains("War")&&(e.contains("DeclaredAt")||e.contains("WarDeclarer"))) throw new IllegalArgumentException("War fields without war");
                if (e.contains("Alliance")) {
                    if (!e.contains("AcceptedAt",Tag.TAG_LONG)||e.getLong("AcceptedAt")<0) throw new IllegalArgumentException("Invalid alliance time");
                    Relation kind=e.contains("TreatyKind")?Relation.valueOf(e.getString("TreatyKind")):Relation.ALLIED;
                    if (kind!=Relation.ALLIED&&kind!=Relation.NON_AGGRESSION) throw new IllegalArgumentException("Invalid treaty kind");
                    alliance=new Alliance(e.getUUID("Alliance"),e.getLong("AcceptedAt"),kind);
                    if (!identities.add(alliance.id())) throw new IllegalArgumentException("Duplicate agreement");
                }
                War war=null;
                long nextProposalAt=e.getLong("NextProposalAt");
                if (e.contains("NextProposalAt")&&(!e.contains("NextProposalAt",Tag.TAG_LONG)||nextProposalAt<0)) throw new IllegalArgumentException("Invalid cooldown");
                if (e.contains("War")) {
                    war=new War(e.getUUID("War"),e.getLong("DeclaredAt"),e.getUUID("WarDeclarer"));
                    if (!e.contains("DeclaredAt",Tag.TAG_LONG)||war.declaredAt()<0||!pair.contains(war.declarer())||!identities.add(war.id())) throw new IllegalArgumentException("Invalid war");
                }
                if (!e.contains("Hostility",Tag.TAG_LIST)) throw new IllegalArgumentException("Missing declarations");
                ListTag declarations=(ListTag)e.get("Hostility"); Set<UUID> hostile=new HashSet<>();
                for (Tag declaration:declarations) {
                    if (!(declaration instanceof CompoundTag d)) throw new IllegalArgumentException("Invalid declaration");
                    UUID id=d.getUUID("Declarer");
                    if (!pair.contains(id)||!hostile.add(id)) throw new IllegalArgumentException("Invalid declarer");
                }
                if (e.contains("Invitation")) {
                    if (!(e.get("Invitation") instanceof CompoundTag r)||!r.contains("CreatedAt",Tag.TAG_LONG)||!r.contains("ExpiresAt",Tag.TAG_LONG)) throw new IllegalArgumentException("Invalid request");
                    invitation=new Invitation(r.getUUID("Id"),r.getUUID("Sender"),r.getUUID("Recipient"),r.getLong("CreatedAt"),r.getLong("ExpiresAt"),
                            r.contains("Inviter")?r.getUUID("Inviter"):kingdoms.byId(r.getUUID("Sender"))==null?null:kingdoms.byId(r.getUUID("Sender")).rulerId(),r.contains("Type")?ProposalType.valueOf(r.getString("Type")):ProposalType.ALLIANCE);
                    if (!r.contains("Inviter")) repaired=true;
                    if (!new Pair(invitation.sender(),invitation.recipient()).equals(pair)||invitation.createdAt()<0||invitation.expiresAt()<=invitation.createdAt()||!identities.add(invitation.id())) throw new IllegalArgumentException("Invalid invitation");
                }
                if (alliance!=null&&(!hostile.isEmpty()||war!=null)||war!=null&&!hostile.isEmpty()) throw new IllegalArgumentException("Contradictory diplomacy");
                Relation relation=war!=null?Relation.AT_WAR:!hostile.isEmpty()?Relation.HOSTILE:alliance!=null?alliance.kind():Relation.NEUTRAL;
                if (invitation!=null&&!invitation.type().valid(relation)) throw new IllegalArgumentException("Proposal incompatible with relation");
                if (kingdoms.byId(pair.first())==null||kingdoms.byId(pair.second())==null) { repaired=true; continue; }
                entries.put(pair,new Entry(revision,alliance,hostile,invitation,war,nextProposalAt));
            }
            return pruneUnauthorized(kingdoms)||repaired;
        } catch (RuntimeException exception) {
            entries.clear(); unreadable=raw.copy();
            RegnumNations.LOGGER.error("Diplomacy data is invalid; diplomacy disabled and original NBT preserved",exception);
            return false;
        }
    }
}
