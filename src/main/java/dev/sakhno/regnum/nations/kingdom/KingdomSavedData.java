package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.RegnumNations;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;

/** One versioned registry per save. Claims have exactly one authoritative settlement owner. */
public final class KingdomSavedData extends SavedData {
    // Persistent filename: changing this would hide existing kingdoms, cities and claims.
    public static final String FILE_ID="project-reigns-kingdoms";
    static final Factory<KingdomSavedData> FACTORY=new Factory<>(KingdomSavedData::new,KingdomSavedData::load,null);
    private final Map<UUID,Kingdom> kingdoms=new HashMap<>();
    private final Map<UUID,UUID> founders=new HashMap<>(), obelisks=new HashMap<>();
    private final Map<String,UUID> names=new HashMap<>();
    private final Map<GlobalPos,UUID> capitals=new HashMap<>();
    private final Map<UUID,Settlement> settlements=new HashMap<>();
    private final Map<ClaimPos,UUID> claims=new HashMap<>();
    // Non-owner citizenship only. Owners remain implicit citizens, never duplicate records.
    private final Map<UUID,Membership> citizens=new HashMap<>();
    private record Membership(UUID city, CityRole role) { }
    // Transient UI concurrency stamp; no schema or gameplay rule change.
    private final Map<UUID,Long> membershipRevisions=new HashMap<>();
    long membershipRevision(UUID city) { return membershipRevisions.getOrDefault(city,0L); }
    private void membershipChanged(UUID city) { membershipRevisions.merge(city,1L,Long::sum); }
    private final Map<UUID,DeletedAnchor> deleted=new HashMap<>();
    private CompoundTag unreadableData;
    public record DeletedAnchor(UUID kingdom, ResourceKey<Level> dimension, BlockPos pos, UUID obelisk) { }
    static KingdomSavedData get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(FACTORY,FILE_ID); }
    public Collection<Kingdom> kingdoms() { return List.copyOf(kingdoms.values()); }
    public Collection<Settlement> settlements() { return List.copyOf(settlements.values()); }
    public Kingdom byId(UUID id) { return kingdoms.get(id); }
    public Kingdom byFounder(UUID id) { return kingdoms.get(founders.get(id)); }
    public Kingdom atCapital(ResourceKey<Level> dimension,BlockPos pos) { return kingdoms.get(capitals.get(GlobalPos.of(dimension,pos))); }
    public Settlement settlement(UUID id) { return settlements.get(id); }
    public List<Settlement> ownedSettlements(UUID owner) {
        return settlements.values().stream().filter(s -> s.ownerId().equals(owner))
                .sorted(Comparator.comparingLong(Settlement::createdAt).thenComparing(Settlement::id)).toList();
    }
    public Settlement byOwner(UUID owner) { var owned=ownedSettlements(owner); return owned.isEmpty()?null:owned.getFirst(); }
    public Settlement citizenCity(UUID player) throws FoundingException {
        requireAvailable();
        var owned=ownedSettlements(player);
        if (owned.size()>1) throw new FoundingException(DOMAIN_REPAIR);
        Membership member=citizens.get(player);
        return owned.isEmpty()?(member==null?null:settlement(member.city())):owned.getFirst();
    }
    public boolean isCitizen(UUID city,UUID player) {
        return role(city,player)!=null;
    }
    /** A null role means outsider; ownership is always resolved from the current city record. */
    public CityRole role(UUID city,UUID player) {
        Settlement current=settlement(city);
        if (current==null) return null;
        if (current.ownerId().equals(player)) return CityRole.OWNER;
        Membership member=citizens.get(player);
        return member!=null&&city.equals(member.city())?member.role():null;
    }
    void assignRole(UUID city,UUID player,CityRole role) throws FoundingException {
        requireAvailable();
        Settlement current=settlement(city);
        if (current==null) throw new FoundingException(CITY_MISSING);
        if (current.ownerId().equals(player)||role==CityRole.OWNER) throw new FoundingException(OWNER_ROLE_IMMUTABLE);
        if (role==null) throw new FoundingException(INVALID_CITY_ROLE);
        Membership member=citizens.get(player);
        if (member==null||!city.equals(member.city())) throw new FoundingException(NOT_CITIZEN);
        if (member.role()==role) return;
        citizens.put(player,new Membership(city,role)); membershipChanged(city); setDirty();
    }
    public List<UUID> citizens(UUID city) {
        Settlement current=settlement(city);
        if (current==null) return List.of();
        var members=new TreeSet<UUID>(); members.add(current.ownerId());
        citizens.forEach((player,member) -> { if (city.equals(member.city())) members.add(player); });
        return List.copyOf(members);
    }
    void addCitizen(UUID city,UUID player) throws FoundingException {
        requireAvailable();
        if (settlement(city)==null) throw new FoundingException(CITY_MISSING);
        if (citizenCity(player)!=null) throw new FoundingException(ALREADY_CITIZEN);
        citizens.put(player,new Membership(city,CityRole.CITIZEN)); membershipChanged(city); setDirty();
    }
    void removeCitizen(UUID city,UUID player) throws FoundingException {
        requireAvailable();
        Settlement current=settlement(city);
        if (current==null) throw new FoundingException(CITY_MISSING);
        if (current.ownerId().equals(player)) throw new FoundingException(OWNER_CANNOT_LEAVE);
        if (role(city,player)==null) throw new FoundingException(NOT_CITIZEN);
        citizens.remove(player); membershipChanged(city); setDirty();
    }
    /** Internal lifecycle operation. Capital cities must survive until their state is dissolved. */
    void deleteSettlement(UUID city) throws FoundingException {
        requireAvailable();
        if (settlement(city)==null) throw new FoundingException(CITY_MISSING);
        if (kingdoms.values().stream().anyMatch(k -> k.capitalSettlementId().equals(city))) throw new FoundingException(CITY_IS_CAPITAL);
        citizens.values().removeIf(member -> city.equals(member.city()));
        claims.values().removeIf(city::equals);
        settlements.remove(city); membershipChanged(city); setDirty();
    }
    public Settlement cityAt(ResourceKey<Level> dimension,BlockPos pos) {
        return settlements.values().stream().filter(s -> s.dimension().equals(dimension)&&s.obeliskPos().equals(pos)).findFirst().orElse(null);
    }
    /** Identity checks span both kinds of anchors, including migrated shared anchors. */
    public boolean usesObelisk(UUID identity) {
        return obelisks.containsKey(identity)||settlements.values().stream().anyMatch(s -> s.obeliskId().equals(identity));
    }
    boolean stateIdentityOccupied(ResourceKey<Level> dimension,BlockPos pos,UUID identity) {
        if (obelisks.containsKey(identity)) return true;
        return settlements.values().stream().anyMatch(s -> s.obeliskId().equals(identity)
                && !(s.legacyAnchor()&&s.sovereign()==null&&s.dimension().equals(dimension)&&s.obeliskPos().equals(pos)));
    }
    public boolean anchorAt(ResourceKey<Level> dimension,BlockPos pos) {
        return cityAt(dimension,pos)!=null||atCapital(dimension,pos)!=null;
    }
    /** Only position/status change; claims, memberships and political references are untouched. */
    void relocate(Settlement city,Kingdom state,BlockPos destination) throws FoundingException {
        requireAvailable();
        if (city!=null&&!city.equals(settlement(city.id()))||state!=null&&!state.equals(byId(state.id()))) throw new FoundingException(OBELISK_UNAVAILABLE);
        if (city!=null) settlements.put(city.id(),city.withObeliskPos(destination));
        if (state!=null) {
            capitals.remove(GlobalPos.of(state.dimension(),state.capitalPos()),state.id());
            Kingdom moved=state.withCapitalPos(destination);
            kingdoms.put(state.id(),moved); capitals.put(GlobalPos.of(moved.dimension(),destination),moved.id());
        }
        setDirty();
    }
    public UUID claimOwner(ClaimPos pos) { return claims.get(pos); }
    public List<ClaimPos> settlementClaims(UUID id) { return claims.entrySet().stream().filter(e -> e.getValue().equals(id)).map(Map.Entry::getKey).toList(); }
    public long settlementClaimCount(UUID id) { return claims.values().stream().filter(id::equals).count(); }
    /** Compatibility/public kingdom aggregate; city-specific callers use settlementClaimCount. */
    public long claimCount(UUID id) {
        if (!kingdoms.containsKey(id)) return settlementClaimCount(id);
        return claims.values().stream().filter(owner -> { Settlement s=settlements.get(owner); return s!=null&&id.equals(s.sovereign()); }).count();
    }
    public Kingdom sovereign(Settlement s) { return s==null?null:kingdoms.get(s.sovereign()); }
    public int politicalColor(Settlement s) { Kingdom k=sovereign(s); return k==null?s.color():k.color(); }
    public boolean isCapital(ClaimPos pos) {
        return settlements.values().stream().anyMatch(s -> ClaimPos.at(s.dimension(),s.obeliskPos()).equals(pos))
                || kingdoms.values().stream().anyMatch(k -> ClaimPos.at(k.dimension(),k.capitalPos()).equals(pos));
    }
    public DeletedAnchor deletedAnchor(UUID id) { return deleted.get(id); }
    boolean available() { return unreadableData == null; }
    void requireAvailable() throws FoundingException { if (!available()) throw new FoundingException(DATA_UNAVAILABLE); }
    void validate(UUID founder,KingdomName name,ResourceKey<Level> dim,BlockPos pos,UUID obelisk) throws FoundingException {
        requireAvailable();
        if (founders.containsKey(founder)) throw new FoundingException(ALREADY_FOUNDER);
        if (names.containsKey(name.key())) throw new FoundingException(DUPLICATE_NAME);
        if (stateIdentityOccupied(dim,pos,obelisk)||capitals.containsKey(GlobalPos.of(dim,pos))) throw new FoundingException(ALREADY_BOUND);
    }
    /** Historical/command combined founding, still one personal domain. */
    void add(Kingdom kingdom) throws FoundingException {
        validate(kingdom.founderId(),kingdom.name(),kingdom.dimension(),kingdom.capitalPos(),kingdom.capitalObeliskId());
        if (byOwner(kingdom.founderId())!=null) throw new FoundingException(DOMAIN_LIMIT);
        if (citizens.containsKey(kingdom.founderId())) throw new FoundingException(ALREADY_CITIZEN);
        if (kingdoms.containsKey(kingdom.id())||deleted.containsKey(kingdom.id())||settlements.containsKey(kingdom.id())||!kingdom.capitalSettlementId().equals(kingdom.id())||kingdom.id().equals(kingdom.capitalObeliskId())) throw new IllegalArgumentException("Duplicate identity");
        ClaimPos capital=ClaimPos.at(kingdom.dimension(),kingdom.capitalPos());
        if (claims.containsKey(capital)) throw new FoundingException(TERRITORY_OCCUPIED);
        Settlement city=legacyCity(kingdom);
        index(kingdom); settlements.put(city.id(),city); claims.put(capital,city.id()); setDirty();
    }
    void addSettlement(Settlement city) throws FoundingException {
        requireAvailable();
        if (byOwner(city.ownerId())!=null) throw new FoundingException(DOMAIN_LIMIT);
        if (citizens.containsKey(city.ownerId())) throw new FoundingException(ALREADY_CITIZEN);
        if (settlements.containsKey(city.id())||kingdoms.containsKey(city.id())||city.id().equals(city.obeliskId())
                || anchorAt(city.dimension(),city.obeliskPos()) || usesObelisk(city.obeliskId())) throw new FoundingException(ALREADY_BOUND);
        if (city.sovereign()!=null||city.legacyAnchor()) throw new IllegalArgumentException("Native city must start independent");
        ClaimPos capital=ClaimPos.at(city.dimension(),city.obeliskPos());
        if (claims.containsKey(capital)||isCapital(capital)) throw new FoundingException(TERRITORY_OCCUPIED);
        settlements.put(city.id(),city); claims.put(capital,city.id()); setDirty();
    }
    void elevate(Kingdom kingdom,Settlement city) throws FoundingException {
        validate(kingdom.founderId(),kingdom.name(),kingdom.dimension(),kingdom.capitalPos(),kingdom.capitalObeliskId());
        if (!city.equals(settlements.get(city.id()))||city.sovereign()!=null||!city.ownerId().equals(kingdom.founderId())) throw new FoundingException(ALREADY_AFFILIATED);
        if (kingdoms.containsKey(kingdom.id())||deleted.containsKey(kingdom.id())||settlements.containsKey(kingdom.id())||!kingdom.capitalSettlementId().equals(city.id())) throw new IllegalArgumentException("Invalid elevation identity");
        if (!city.id().equals(claimOwner(ClaimPos.at(kingdom.dimension(),kingdom.capitalPos())))) throw new FoundingException(CAPITAL_CONFLICT);
        index(kingdom); settlements.put(city.id(),city.withSovereign(kingdom.id())); setDirty();
    }
    void affiliate(Settlement city,UUID kingdom) throws FoundingException {
        requireAvailable();
        if (!city.equals(settlements.get(city.id()))||city.sovereign()!=null||!kingdoms.containsKey(kingdom)) throw new FoundingException(ALREADY_AFFILIATED);
        settlements.put(city.id(),city.withSovereign(kingdom)); setDirty();
    }
    Kingdom rename(UUID id,KingdomName name) throws FoundingException {
        requireAvailable(); Kingdom current=kingdoms.get(id);
        if (current==null) throw new IllegalArgumentException("Unknown kingdom");
        UUID existing=names.get(name.key());
        if (existing!=null&&!existing.equals(id)) throw new FoundingException(DUPLICATE_NAME);
        if (current.name().equals(name)) return current;
        Kingdom renamed=current.withName(name); names.remove(current.name().key(),id); names.put(name.key(),id); kingdoms.put(id,renamed); setDirty(); return renamed;
    }
    void deleteKingdom(UUID id) throws FoundingException {
        requireAvailable(); Kingdom current=kingdoms.get(id);
        if (current==null) throw new IllegalArgumentException("Unknown kingdom");
        deleted.put(id,new DeletedAnchor(id,current.dimension(),current.capitalPos(),current.capitalObeliskId()));
        settlements.replaceAll((key,city) -> id.equals(city.sovereign())?city.withSovereign(null):city);
        kingdoms.remove(id); names.remove(current.name().key(),id); obelisks.remove(current.capitalObeliskId(),id);
        capitals.remove(GlobalPos.of(current.dimension(),current.capitalPos()),id);
        founders.remove(current.founderId(),id);
        kingdoms.values().stream().filter(k -> k.founderId().equals(current.founderId())).sorted(order()).findFirst().ifPresent(k -> founders.put(k.founderId(),k.id()));
        setDirty();
    }
    void putClaim(ClaimPos pos,UUID city) {
        if (!settlements.containsKey(city)||claims.containsKey(pos)&&!city.equals(claims.get(pos))) throw new IllegalArgumentException("Invalid claim owner or overlap");
        claims.put(pos,city); setDirty();
    }
    void removeClaim(ClaimPos pos) { claims.remove(pos); setDirty(); }
    void capitalStatus(Kingdom kingdom,Kingdom.CapitalStatus status) {
        Kingdom current=kingdoms.get(kingdom.id());
        if (current!=null&&current.capitalStatus()!=status) { kingdoms.put(current.id(),current.withCapitalStatus(status)); setDirty(); }
        Settlement city=settlements.get(kingdom.capitalSettlementId());
        if (city!=null&&city.legacyAnchor()) settlementStatus(city,status);
    }
    void settlementStatus(Settlement city,Kingdom.CapitalStatus status) {
        Settlement current=settlements.get(city.id());
        if (current!=null&&current.status()!=status) { settlements.put(city.id(),current.withStatus(status)); setDirty(); }
    }
    private static Comparator<Kingdom> order() { return Comparator.comparingLong(Kingdom::createdAt).thenComparing(Kingdom::id); }
    private void index(Kingdom kingdom) {
        kingdoms.put(kingdom.id(),kingdom); founders.putIfAbsent(kingdom.founderId(),kingdom.id()); names.put(kingdom.name().key(),kingdom.id());
        obelisks.put(kingdom.capitalObeliskId(),kingdom.id()); capitals.put(GlobalPos.of(kingdom.dimension(),kingdom.capitalPos()),kingdom.id());
    }
    private static Settlement legacyCity(Kingdom k) {
        return new Settlement(k.id(),k.name(),k.founderId(),k.founderId(),k.dimension(),k.capitalPos(),k.capitalObeliskId(),k.createdAt(),k.capitalStatus(),k.id(),k.color(),true);
    }
    private void reconcileCapitals() {
        var cities=settlements.values().stream().sorted(Comparator.comparingLong(Settlement::createdAt).thenComparing(Settlement::id)).toList();
        for (Settlement city:cities) {
            ClaimPos pos=ClaimPos.at(city.dimension(),city.obeliskPos()); UUID owner=claims.putIfAbsent(pos,city.id());
            if (owner==null) setDirty();
            else if (!owner.equals(city.id())) RegnumNations.LOGGER.warn("Capital claim conflict for settlement {} at {}: preserved owner {}",city.id(),pos,owner);
        }
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        if (unreadableData!=null) return unreadableData.copy();
        tag.putInt("Version",5);
        ListTag list=new ListTag();
        for (Kingdom k:kingdoms.values()) {
            CompoundTag e=anchor(k.id(),k.name(),k.founderId(),k.dimension(),k.capitalPos(),k.capitalObeliskId(),k.createdAt(),k.capitalStatus());
            e.putUUID("CapitalSettlement",k.capitalSettlementId()); e.putInt("Color",k.color()); e.putString("Government",k.government()); list.add(e);
        }
        tag.put("Kingdoms",list); list=new ListTag();
        for (Settlement s:settlements.values()) {
            CompoundTag e=anchor(s.id(),s.name(),s.founderId(),s.dimension(),s.obeliskPos(),s.obeliskId(),s.createdAt(),s.status());
            e.putUUID("Owner",s.ownerId()); if (s.sovereign()!=null) e.putUUID("Sovereign",s.sovereign());
            e.putInt("Color",s.color()); e.putBoolean("LegacyAnchor",s.legacyAnchor()); list.add(e);
        }
        tag.put("Settlements",list); list=new ListTag();
        for (var claim:claims.entrySet()) {
            CompoundTag e=new CompoundTag(); e.putString("Dimension",claim.getKey().dimension().location().toString());
            e.putInt("X",claim.getKey().x()); e.putInt("Z",claim.getKey().z()); e.putUUID("Settlement",claim.getValue()); list.add(e);
        }
        tag.put("Claims",list); list=new ListTag();
        for (DeletedAnchor d:deleted.values()) { CompoundTag e=new CompoundTag(); e.putUUID("Id",d.kingdom()); e.putString("Dimension",d.dimension().location().toString()); e.putLong("Position",d.pos().asLong()); e.putUUID("Obelisk",d.obelisk()); list.add(e); }
        tag.put("DeletedStateAnchors",list); list=new ListTag();
        for (var member:citizens.entrySet()) {
            CompoundTag e=new CompoundTag(); e.putUUID("Player",member.getKey()); e.putUUID("Settlement",member.getValue().city());
            e.putString("Role",member.getValue().role().name()); list.add(e);
        }
        tag.put("Citizens",list); return tag;
    }
    private static CompoundTag anchor(UUID id,KingdomName name,UUID founder,ResourceKey<Level> dim,BlockPos pos,UUID obelisk,long time,Kingdom.CapitalStatus status) {
        CompoundTag e=new CompoundTag(); e.putUUID("Id",id); e.putString("Name",name.display()); e.putUUID("Founder",founder);
        e.putString("Dimension",dim.location().toString()); e.putLong("Position",pos.asLong()); e.putUUID("Obelisk",obelisk); e.putLong("CreatedAt",time); e.putString("CapitalStatus",status.name()); return e;
    }
    private static ListTag list(CompoundTag tag,String key) {
        if (!tag.contains(key,Tag.TAG_LIST)) throw new IllegalArgumentException("Missing list "+key);
        ListTag list=tag.getList(key,Tag.TAG_COMPOUND);
        if (list.size()!=((ListTag)tag.get(key)).size()) throw new IllegalArgumentException("Invalid list "+key); return list;
    }
    private static ResourceKey<Level> dimension(CompoundTag e) { return ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(e.getString("Dimension"))); }
    private static Kingdom.CapitalStatus status(CompoundTag e) {
        if (!e.contains("Position",Tag.TAG_LONG)||!e.contains("CreatedAt",Tag.TAG_LONG)) throw new IllegalArgumentException("Missing anchor");
        var status=Kingdom.CapitalStatus.valueOf(e.getString("CapitalStatus")); return status==Kingdom.CapitalStatus.PRESENT?Kingdom.CapitalStatus.UNRESOLVED:status;
    }
    static KingdomSavedData load(CompoundTag tag,HolderLookup.Provider registries) {
        var data=new KingdomSavedData();
        try {
            int version=tag.getInt("Version"); if (version<1||version>5) throw new IllegalArgumentException("Unknown kingdom saved-data schema");
            List<Kingdom> parsed=new ArrayList<>();
            for (Tag entry:list(tag,"Kingdoms")) {
                CompoundTag e=(CompoundTag)entry; UUID id=e.getUUID("Id");
                if (e.contains("Color")&&!e.contains("Color",Tag.TAG_INT)||e.contains("Government")&&!e.contains("Government",Tag.TAG_STRING)) throw new IllegalArgumentException("Malformed political fields");
                parsed.add(new Kingdom(id,new KingdomName(e.getString("Name")),e.getUUID("Founder"),dimension(e),BlockPos.of(e.getLong("Position")),e.getUUID("Obelisk"),e.getLong("CreatedAt"),status(e),
                        version>=3?e.getUUID("CapitalSettlement"):id,e.contains("Color",Tag.TAG_INT)?e.getInt("Color"):PoliticalColors.fallback(id),e.contains("Government",Tag.TAG_STRING)?e.getString("Government"):"unspecified"));
            }
            for (Kingdom k:parsed.stream().sorted(order()).toList()) {
                if (data.kingdoms.containsKey(k.id())||k.id().equals(k.capitalObeliskId())||data.names.containsKey(k.name().key())
                        ||data.obelisks.containsKey(k.capitalObeliskId())||data.capitals.containsKey(GlobalPos.of(k.dimension(),k.capitalPos()))) throw new IllegalArgumentException("Duplicate kingdom identity/name/anchor");
                data.index(k);
            }
            if (version<3) {
                for (Kingdom k:parsed) data.settlements.put(k.id(),legacyCity(k));
            } else {
                Set<UUID> anchorIds=new HashSet<>(); Set<GlobalPos> positions=new HashSet<>();
                for (Tag entry:list(tag,"Settlements")) {
                    CompoundTag e=(CompoundTag)entry; UUID id=e.getUUID("Id");
                    if (e.contains("Sovereign")&&!e.hasUUID("Sovereign")||e.contains("Color")&&!e.contains("Color",Tag.TAG_INT)
                            ||!e.contains("LegacyAnchor",Tag.TAG_BYTE)) throw new IllegalArgumentException("Malformed settlement fields");
                    Settlement s=new Settlement(id,new KingdomName(e.getString("Name")),e.getUUID("Founder"),e.getUUID("Owner"),dimension(e),BlockPos.of(e.getLong("Position")),e.getUUID("Obelisk"),e.getLong("CreatedAt"),status(e),e.hasUUID("Sovereign")?e.getUUID("Sovereign"):null,
                            e.contains("Color",Tag.TAG_INT)?e.getInt("Color"):PoliticalColors.fallback(id),e.getBoolean("LegacyAnchor"));
                    Kingdom k=data.byId(s.sovereign());
                    // A migrated combined anchor is resolved from its historical state record.
                    if (s.legacyAnchor()&&k!=null&&k.capitalSettlementId().equals(id)) s=new Settlement(id,s.name(),s.founderId(),s.ownerId(),k.dimension(),k.capitalPos(),k.capitalObeliskId(),s.createdAt(),s.status(),k.id(),s.color(),true);
                    if (data.settlements.containsKey(id)||id.equals(s.obeliskId())||!anchorIds.add(s.obeliskId())||!positions.add(GlobalPos.of(s.dimension(),s.obeliskPos()))) throw new IllegalArgumentException("Invalid settlement identity");
                    data.settlements.put(id,s);
                }
                for (Kingdom k:parsed) { Settlement s=data.settlement(k.capitalSettlementId()); if (s==null||!k.id().equals(s.sovereign())) throw new IllegalArgumentException("Missing capital settlement"); }
                for (Tag entry:list(tag,"DeletedStateAnchors")) {
                    CompoundTag e=(CompoundTag)entry; UUID id=e.getUUID("Id");
                    if (data.kingdoms.containsKey(id)||data.deleted.containsKey(id)||!e.contains("Position",Tag.TAG_LONG)) throw new IllegalArgumentException("Invalid deletion tombstone");
                    data.deleted.put(id,new DeletedAnchor(id,dimension(e),BlockPos.of(e.getLong("Position")),e.getUUID("Obelisk")));
                }
            }
            if (version>=2) for (Tag entry:list(tag,"Claims")) {
                CompoundTag e=(CompoundTag)entry;
                if (!e.contains("X",Tag.TAG_INT)||!e.contains("Z",Tag.TAG_INT)) throw new IllegalArgumentException("Missing claim position");
                UUID owner=e.getUUID(version>=3?"Settlement":"Kingdom"); ClaimPos pos=new ClaimPos(dimension(e),e.getInt("X"),e.getInt("Z"));
                if (!data.settlements.containsKey(owner)||data.claims.putIfAbsent(pos,owner)!=null) throw new IllegalArgumentException("Unknown owner or duplicate claim");
            }
            if (version>=4||tag.contains("Citizens")) for (Tag entry:list(tag,"Citizens")) {
                CompoundTag e=(CompoundTag)entry; UUID player=e.getUUID("Player"), city=e.getUUID("Settlement");
                CityRole role=CityRole.CITIZEN;
                if (version>=5||e.contains("Role")) {
                    if (!e.contains("Role",Tag.TAG_STRING)) throw new IllegalArgumentException("Missing or malformed citizen role");
                    role=CityRole.valueOf(e.getString("Role"));
                }
                if (role==CityRole.OWNER||!data.settlements.containsKey(city)||data.byOwner(player)!=null
                        ||data.citizens.putIfAbsent(player,new Membership(city,role))!=null)
                    throw new IllegalArgumentException("Conflicting or orphaned citizenship/role");
            }
            data.setDirty(version<5); data.repairAffiliations(); data.reconcileCapitals();
            var owners=data.settlements.values().stream().collect(java.util.stream.Collectors.groupingBy(Settlement::ownerId,java.util.stream.Collectors.counting()));
            owners.forEach((owner,count) -> { if (count>1) RegnumNations.LOGGER.warn("Historical multiple domains for owner {}: {} records retained; claim management requires repair",owner,count); });
        } catch (RuntimeException exception) {
            data=new KingdomSavedData(); data.unreadableData=tag.copy();
            RegnumNations.LOGGER.error("Kingdom data is invalid; mutations disabled to preserve original NBT",exception);
        }
        return data;
    }

    /** Missing political affiliation cannot invalidate an otherwise valid private domain. */
    private void repairAffiliations() {
        settlements.replaceAll((id,city) -> {
            UUID sovereign=city.sovereign();
            if (sovereign==null||kingdoms.containsKey(sovereign)) return city;
            // A legacy member may have joined a different state; only the original shared ID proves its anchor.
            if (city.legacyAnchor()&&city.id().equals(sovereign)) deleted.putIfAbsent(sovereign,
                    new DeletedAnchor(sovereign,city.dimension(),city.obeliskPos(),city.obeliskId()));
            RegnumNations.LOGGER.warn("Detached settlement {} from absent kingdom {}; ownership and claims preserved",id,sovereign);
            setDirty();
            return city.withSovereign(null);
        });
    }
}
