package dev.sakhno.regnum.nations.kingdom;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;

/** Political demands are separate from ownership. References and expiry survive server restarts. */
final class PoliticalClaims {
    record Demand(UUID id,UUID claimant,UUID owner,UUID city,ClaimPos territory,long createdAt,long expiresAt) { }
    private record Key(UUID claimant,ClaimPos territory) { }
    private final KingdomSavedData data;
    private final Map<UUID,Demand> demands=new HashMap<>();
    private final Map<Key,UUID> duplicates=new HashMap<>();
    private final Map<ClaimPos,Set<UUID>> byTerritory=new HashMap<>();
    private final Map<UUID,Set<UUID>> byKingdom=new HashMap<>();
    private final PriorityQueue<Demand> expirations=new PriorityQueue<>(Comparator.comparingLong(Demand::expiresAt).thenComparing(Demand::id));
    PoliticalClaims(KingdomSavedData data) { this.data=data; }
    List<Demand> involvingOrAt(UUID kingdom,ClaimPos pos) {
        Set<UUID> ids=new HashSet<>(byTerritory.getOrDefault(pos,Set.of()));
        if (kingdom!=null) ids.addAll(byKingdom.getOrDefault(kingdom,Set.of()));
        return ids.stream().map(demands::get).sorted(Comparator.comparingLong(Demand::createdAt).thenComparing(Demand::id)).toList();
    }
    int countAt(ClaimPos pos) { return byTerritory.getOrDefault(pos,Set.of()).size(); }
    boolean has(UUID claimant,ClaimPos pos) { return duplicates.containsKey(new Key(claimant,pos)); }
    void create(UUID claimant,ClaimPos pos,int days) throws FoundingException {
        data.requireAvailable();
        if (days<0||days>365) throw new FoundingException(INVALID_EXPIRATION);
        Settlement city=data.settlement(data.claimOwner(pos));
        if (data.byId(claimant)==null||city==null||city.sovereign()==null||claimant.equals(city.sovereign())) throw new FoundingException(POLITICAL_TARGET);
        if (has(claimant,pos)) throw new FoundingException(POLITICAL_DUPLICATE);
        long count=byKingdom.getOrDefault(claimant,Set.of()).stream().map(demands::get).filter(d -> d.claimant().equals(claimant)).count();
        if (count>=TerritoryConfig.politicalLimit()) throw new FoundingException(POLITICAL_LIMIT);
        long now=System.currentTimeMillis();
        add(new Demand(UUID.randomUUID(),claimant,city.sovereign(),city.id(),pos,now,days==0?0:now+days*86_400_000L));
        data.setDirty(); data.politicalChanged();
    }
    void withdraw(UUID claimant,UUID id) throws FoundingException {
        data.requireAvailable(); Demand demand=demands.get(id);
        if (demand==null) throw new FoundingException(POLITICAL_MISSING);
        if (!demand.claimant().equals(claimant)) throw new FoundingException(NOT_OWNER);
        remove(id); data.politicalChanged();
    }
    private void add(Demand d) {
        if (demands.containsKey(d.id())||duplicates.containsKey(new Key(d.claimant(),d.territory()))) throw new IllegalArgumentException("Duplicate political claim");
        demands.put(d.id(),d); duplicates.put(new Key(d.claimant(),d.territory()),d.id());
        byTerritory.computeIfAbsent(d.territory(),key -> new HashSet<>()).add(d.id());
        byKingdom.computeIfAbsent(d.claimant(),key -> new HashSet<>()).add(d.id());
        byKingdom.computeIfAbsent(d.owner(),key -> new HashSet<>()).add(d.id());
        if (d.expiresAt()!=0) expirations.add(d);
    }
    private void remove(UUID id) {
        Demand d=demands.remove(id); if (d==null) return;
        duplicates.remove(new Key(d.claimant(),d.territory()));
        removeIndex(byTerritory,d.territory(),id); removeIndex(byKingdom,d.claimant(),id); removeIndex(byKingdom,d.owner(),id);
        // Do not accumulate withdrawn expiry entries across repeated create/withdraw requests.
        if (d.expiresAt()!=0) expirations.remove(d);
        data.setDirty();
    }
    private static <K> void removeIndex(Map<K,Set<UUID>> index,K key,UUID id) {
        Set<UUID> values=index.get(key); if (values!=null) { values.remove(id); if (values.isEmpty()) index.remove(key); }
    }
    private boolean possible(Demand d) {
        Settlement city=data.settlement(data.claimOwner(d.territory()));
        return data.byId(d.claimant())!=null&&data.byId(d.owner())!=null&&!d.claimant().equals(d.owner())
                &&city!=null&&city.id().equals(d.city())&&d.owner().equals(city.sovereign());
    }
    void pruneAt(ClaimPos pos) {
        for (UUID id:List.copyOf(byTerritory.getOrDefault(pos,Set.of()))) if (!possible(demands.get(id))) remove(id);
    }
    /** Only political lifecycle changes scan demands; per-player border reads use the spatial index. */
    void pruneImpossible() {
        for (Demand d:List.copyOf(demands.values())) if (!possible(d)) remove(d.id());
    }
    void expireDue() {
        boolean changed=false; long now=System.currentTimeMillis();
        while (!expirations.isEmpty()&&expirations.peek().expiresAt()<=now) { remove(expirations.peek().id()); changed=true; }
        if (changed) data.politicalChanged();
    }
    ListTag save() {
        ListTag list=new ListTag();
        for (Demand d:demands.values().stream().sorted(Comparator.comparing(Demand::id)).toList()) {
            CompoundTag e=new CompoundTag(); e.putUUID("Id",d.id()); e.putUUID("Claimant",d.claimant()); e.putUUID("Owner",d.owner()); e.putUUID("City",d.city());
            e.putString("Dimension",d.territory().dimension().location().toString()); e.putInt("X",d.territory().x()); e.putInt("Z",d.territory().z());
            e.putLong("CreatedAt",d.createdAt()); if (d.expiresAt()!=0) e.putLong("ExpiresAt",d.expiresAt()); list.add(e);
        }
        return list;
    }
    void load(Tag source) {
        if (source==null) return;
        if (!(source instanceof ListTag list)||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND) throw new IllegalArgumentException("Malformed political claims list");
        for (Tag item:list) {
            CompoundTag e=(CompoundTag)item;
            if (!e.contains("Dimension",Tag.TAG_STRING)||!e.contains("X",Tag.TAG_INT)||!e.contains("Z",Tag.TAG_INT)||!e.contains("CreatedAt",Tag.TAG_LONG)
                    ||e.contains("ExpiresAt")&&!e.contains("ExpiresAt",Tag.TAG_LONG)) throw new IllegalArgumentException("Malformed political claim fields");
            Demand d=new Demand(e.getUUID("Id"),e.getUUID("Claimant"),e.getUUID("Owner"),e.getUUID("City"),
                    new ClaimPos(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(e.getString("Dimension"))),e.getInt("X"),e.getInt("Z")),e.getLong("CreatedAt"),e.getLong("ExpiresAt"));
            if (d.createdAt()<0||d.expiresAt()!=0&&d.expiresAt()<=d.createdAt()) throw new IllegalArgumentException("Malformed political claim time");
            if (!possible(d)) throw new IllegalArgumentException("Invalid political claim references");
            add(d);
        }
        expireDue();
    }
}
