package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.RegnumNations;
import java.util.*;
import net.minecraft.nbt.*;

/** Embedded in the kingdom registry. Agreements, declarations and requests are distinct contracts. */
public final class DiplomacyData {
    public enum Relation { NEUTRAL, ALLIED, HOSTILE }
    public record Pair(UUID first, UUID second) {
        public Pair {
            Objects.requireNonNull(first); Objects.requireNonNull(second);
            if (first.equals(second)) throw new IllegalArgumentException("Self diplomacy");
            if (first.compareTo(second)>0) { UUID swap=first; first=second; second=swap; }
        }
        boolean contains(UUID id) { return first.equals(id)||second.equals(id); }
    }
    public record Alliance(UUID id,long acceptedAt) { }
    public record Invitation(UUID id,UUID sender,UUID recipient,long createdAt,long expiresAt,UUID inviter) { }
    public record Entry(UUID revision,Alliance alliance,Set<UUID> declarations,Invitation invitation) {
        public Entry { Objects.requireNonNull(revision); declarations=Set.copyOf(declarations); }
        public Relation relation() { return !declarations.isEmpty()?Relation.HOSTILE:alliance!=null?Relation.ALLIED:Relation.NEUTRAL; }
    }
    private final Map<Pair,Entry> entries=new HashMap<>();
    private Tag unreadable;
    boolean available() { return unreadable==null; }
    Entry get(Pair pair) { return entries.get(pair); }
    void put(Pair pair,Alliance alliance,Set<UUID> declarations,Invitation invitation) {
        entries.put(pair,new Entry(UUID.randomUUID(),alliance,declarations,invitation));
    }
    void delete(UUID id) { entries.keySet().removeIf(pair -> pair.contains(id)); }
    boolean expire(Pair pair,long now) {
        Entry entry=entries.get(pair);
        if (entry==null||entry.invitation()==null||now<entry.invitation().expiresAt()) return false;
        put(pair,entry.alliance(),entry.declarations(),null); return true;
    }
    /** Revocation changes the pair revision deterministically and never revives on promotion. */
    boolean pruneUnauthorized(KingdomSavedData kingdoms) {
        if (!available()) return false;
        boolean changed=false;
        for (var item:entries.entrySet()) {
            Entry entry=item.getValue(); Invitation invitation=entry.invitation();
            if (invitation!=null&&!KingdomPermissions.allows(kingdoms,invitation.sender(),invitation.inviter(),KingdomPermission.MANAGE_DIPLOMACY)) {
                UUID revision=UUID.nameUUIDFromBytes(("revoked:"+entry.revision()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                item.setValue(new Entry(revision,entry.alliance(),entry.declarations(),null)); changed=true;
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
            if (entry.alliance()!=null) { e.putUUID("Alliance",entry.alliance().id()); e.putLong("AcceptedAt",entry.alliance().acceptedAt()); }
            ListTag declarations=new ListTag();
            entry.declarations().stream().sorted().forEach(id -> { CompoundTag d=new CompoundTag(); d.putUUID("Declarer",id); declarations.add(d); });
            e.put("Hostility",declarations);
            if (entry.invitation()!=null) {
                Invitation i=entry.invitation(); CompoundTag request=new CompoundTag();
                request.putUUID("Id",i.id()); request.putUUID("Sender",i.sender()); request.putUUID("Recipient",i.recipient());
                request.putUUID("Inviter",i.inviter()); request.putLong("CreatedAt",i.createdAt()); request.putLong("ExpiresAt",i.expiresAt()); e.put("Invitation",request);
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
                if (e.contains("Alliance")) {
                    if (!e.contains("AcceptedAt",Tag.TAG_LONG)||e.getLong("AcceptedAt")<0) throw new IllegalArgumentException("Invalid alliance time");
                    alliance=new Alliance(e.getUUID("Alliance"),e.getLong("AcceptedAt"));
                    if (!identities.add(alliance.id())) throw new IllegalArgumentException("Duplicate agreement");
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
                            r.contains("Inviter")?r.getUUID("Inviter"):kingdoms.byId(r.getUUID("Sender"))==null?null:kingdoms.byId(r.getUUID("Sender")).rulerId());
                    if (!r.contains("Inviter")) repaired=true;
                    if (!new Pair(invitation.sender(),invitation.recipient()).equals(pair)||invitation.createdAt()<0||invitation.expiresAt()<=invitation.createdAt()||!identities.add(invitation.id())) throw new IllegalArgumentException("Invalid invitation");
                }
                if (alliance!=null&&(!hostile.isEmpty()||invitation!=null)||!hostile.isEmpty()&&invitation!=null) throw new IllegalArgumentException("Contradictory diplomacy");
                if (kingdoms.byId(pair.first())==null||kingdoms.byId(pair.second())==null) { repaired=true; continue; }
                entries.put(pair,new Entry(revision,alliance,hostile,invitation));
            }
            return pruneUnauthorized(kingdoms)||repaired;
        } catch (RuntimeException exception) {
            entries.clear(); unreadable=raw.copy();
            RegnumNations.LOGGER.error("Diplomacy data is invalid; diplomacy disabled and original NBT preserved",exception);
            return false;
        }
    }
}
