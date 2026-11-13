package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.RegnumNations;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

/** Additive schema-9 extension. A single record owns objective, siege and terminal result. */
public final class WarfareData {
    public enum Objective { CAPTURE_CITY, DEFEND_CITY, NEGOTIATED_PEACE }
    public enum State {
        PLANNED, PREPARING, ACTIVE, ATTACKER_VICTORY, DEFENDER_VICTORY, TIMEOUT, WAR_ENDED, PEACE, INVALID, CANCELLED;
        public boolean completed() { return ordinal()>=ATTACKER_VICTORY.ordinal(); }
    }
    public record ObjectiveRecord(UUID id, UUID revision, UUID war, UUID attacker, UUID defender, UUID city,
            UUID owner, UUID obelisk, String dimension, BlockPos pos, Objective objective, State state,
            long startedAt, long readyAt, long endsAt, long completedAt, int progress, int captureSeconds,
            int defenseSeconds, int defenseProgress, boolean breached) {
        public ObjectiveRecord { pos=pos.immutable(); }
        public boolean completed() { return state.completed(); }
        public boolean siege() { return state==State.PREPARING||state==State.ACTIVE; }
        public boolean pendingConquest() { return state==State.ATTACKER_VICTORY; }
        ObjectiveRecord update(State next,long ready,long end,long finished,int value,int defense,boolean touched) {
            return new ObjectiveRecord(id,next==state&&finished==0?revision:UUID.randomUUID(),war,attacker,defender,city,owner,obelisk,dimension,pos,objective,
                    next,startedAt,ready,end,finished,value,captureSeconds,defenseSeconds,defense,touched);
        }
    }
    private final Map<UUID,ObjectiveRecord> records=new HashMap<>();
    private final Map<UUID,UUID> activeCities=new HashMap<>();
    private final Map<UUID,CompoundTag> extensions=new HashMap<>();
    private CompoundTag root=new CompoundTag();
    private Tag unreadable;
    boolean available() { return unreadable==null; }
    Collection<ObjectiveRecord> activeRecords() { return activeCities.values().stream().map(records::get).toList(); }
    Collection<ObjectiveRecord> records() { return List.copyOf(records.values()); }
    ObjectiveRecord get(UUID id) { return records.get(id); }
    ObjectiveRecord active(UUID city) { return records.get(activeCities.get(city)); }
    UUID cityRevision(UUID city) {
        var active=active(city); if (active!=null) return active.revision();
        return records.values().stream().filter(r -> r.city().equals(city))
                .max(Comparator.comparingLong(ObjectiveRecord::completedAt).thenComparingLong(ObjectiveRecord::startedAt).thenComparing(ObjectiveRecord::id))
                .map(ObjectiveRecord::revision).orElse(new UUID(0,0));
    }
    void put(ObjectiveRecord record) {
        ObjectiveRecord before=records.get(record.id());
        if (before!=null&&before.completed()) throw new IllegalStateException("Terminal warfare result is immutable");
        if (!record.completed()) {
            UUID previous=activeCities.putIfAbsent(record.city(),record.id());
            if (previous!=null&&!previous.equals(record.id())) throw new IllegalStateException("Duplicate city objective");
        } else activeCities.remove(record.city(),record.id());
        records.put(record.id(),record);
    }
    Tag save() {
        if (unreadable!=null) return unreadable.copy();
        CompoundTag tag=root.copy(); tag.putInt("Version",1); ListTag list=new ListTag();
        for (var r:records.values().stream().sorted(Comparator.comparing(ObjectiveRecord::id)).toList()) {
            CompoundTag e=extensions.getOrDefault(r.id(),new CompoundTag()).copy();
            e.putUUID("Id",r.id()); e.putUUID("Revision",r.revision()); e.putUUID("War",r.war());
            e.putUUID("Attacker",r.attacker()); e.putUUID("Defender",r.defender()); e.putUUID("City",r.city());
            e.putUUID("Owner",r.owner()); e.putUUID("Obelisk",r.obelisk()); e.putString("Dimension",r.dimension()); e.putLong("Position",r.pos().asLong());
            e.putString("Objective",r.objective().name()); e.putString("State",r.state().name());
            e.putLong("StartedAt",r.startedAt()); e.putLong("ReadyAt",r.readyAt()); e.putLong("EndsAt",r.endsAt()); e.putLong("CompletedAt",r.completedAt());
            e.putInt("Progress",r.progress()); e.putInt("CaptureSeconds",r.captureSeconds()); e.putInt("DefenseSeconds",r.defenseSeconds());
            e.putInt("DefenseProgress",r.defenseProgress()); e.putBoolean("Breached",r.breached()); list.add(e);
        }
        tag.put("Objectives",list); return tag;
    }
    /** Malformed extensions disable warfare only and round-trip the exact original NBT. */
    void load(Tag raw) {
        if (raw==null) return;
        try {
            if (!(raw instanceof CompoundTag tag)||tag.getInt("Version")!=1
                    ||!(tag.get("Objectives") instanceof ListTag list)||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND)
                throw new IllegalArgumentException("Unknown warfare format");
            root=tag.copy(); Set<UUID> revisions=new HashSet<>();
            for (Tag item:list) {
                CompoundTag e=(CompoundTag)item;
                for (String key:List.of("Position","StartedAt","ReadyAt","EndsAt","CompletedAt"))
                    if (!e.contains(key,Tag.TAG_LONG)) throw new IllegalArgumentException("Missing warfare time/position");
                for (String key:List.of("Progress","CaptureSeconds","DefenseSeconds","DefenseProgress"))
                    if (!e.contains(key,Tag.TAG_INT)) throw new IllegalArgumentException("Missing warfare progress");
                var r=new ObjectiveRecord(e.getUUID("Id"),e.getUUID("Revision"),e.getUUID("War"),e.getUUID("Attacker"),e.getUUID("Defender"),e.getUUID("City"),
                        e.getUUID("Owner"),e.getUUID("Obelisk"),e.getString("Dimension"),BlockPos.of(e.getLong("Position")),
                        Objective.valueOf(e.getString("Objective")),State.valueOf(e.getString("State")),e.getLong("StartedAt"),e.getLong("ReadyAt"),e.getLong("EndsAt"),e.getLong("CompletedAt"),
                        e.getInt("Progress"),e.getInt("CaptureSeconds"),e.getInt("DefenseSeconds"),e.getInt("DefenseProgress"),e.getBoolean("Breached"));
                if (!e.contains("Breached",Tag.TAG_BYTE)||records.containsKey(r.id())||!revisions.add(r.revision())||r.attacker().equals(r.defender())||r.startedAt()<0
                        ||r.captureSeconds()<1||r.captureSeconds()>86400||r.defenseSeconds()<1||r.defenseSeconds()>86400
                        ||r.progress()<0||r.progress()>r.captureSeconds()||r.defenseProgress()<0||r.defenseProgress()>r.defenseSeconds()
                        ||r.completed()!=(r.completedAt()>0)||r.completed()&&r.completedAt()<r.startedAt()
                        ||r.progress()>0&&!r.breached()||r.defenseProgress()>0&&(!r.breached()||r.progress()!=0)
                        ||r.state()==State.PLANNED&&(r.readyAt()!=0||r.endsAt()!=0||r.progress()!=0||r.breached())
                        ||r.objective()==Objective.NEGOTIATED_PEACE&&(r.siege()||r.pendingConquest()||r.state()==State.DEFENDER_VICTORY)
                        ||r.siege()&&(r.readyAt()<r.startedAt()||r.endsAt()<=r.readyAt())
                        ||r.pendingConquest()&&(r.progress()!=r.captureSeconds()||r.endsAt()<=r.readyAt())) throw new IllegalArgumentException("Invalid/duplicate warfare record");
                net.minecraft.resources.ResourceLocation.parse(r.dimension());
                put(r); extensions.put(r.id(),e.copy());
                if (activeCities.size()>WarfareConfig.MAX_ACTIVE) throw new IllegalArgumentException("Too many active objectives");
            }
        } catch (RuntimeException failure) {
            records.clear(); activeCities.clear(); extensions.clear(); unreadable=raw.copy();
            RegnumNations.LOGGER.error("Invalid warfare data; warfare disabled, original NBT retained",failure);
        }
    }
}
