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
    private final Map<UUID,Map<UUID,KingdomRole>> governmentRoles=new HashMap<>();
    private final Map<UUID,CityInvitation> cityInvitations=new HashMap<>();
    public record CityInvitation(UUID id, UUID kingdom, UUID city, UUID inviter, long createdAt) { }
    public List<CityInvitation> cityInvitations() { return List.copyOf(cityInvitations.values()); }
    public CityInvitation cityInvitation(UUID id) { return cityInvitations.get(id); }
    public Kingdom playerKingdom(UUID player) throws FoundingException {
        requireAvailable();
        // A retained historical ruler seat must not be hidden by foreign city citizenship.
        Kingdom ruled=kingdoms.values().stream().filter(k -> k.rulerId().equals(player)).sorted(order()).findFirst().orElse(null);
        return ruled!=null?ruled:sovereign(citizenCity(player));
    }
    public KingdomRole kingdomRole(UUID kingdom,UUID player) {
        Kingdom state=byId(kingdom);
        if (state==null||player==null) return null;
        if (state.rulerId().equals(player)) return KingdomRole.RULER;
        boolean member=settlements.values().stream().anyMatch(city -> kingdom.equals(city.sovereign())&&isCitizen(city.id(),player));
        return member?governmentRoles.getOrDefault(kingdom,Map.of()).getOrDefault(player,KingdomRole.MEMBER):null;
    }
    public List<UUID> kingdomMembers(UUID kingdom) {
        Kingdom state=byId(kingdom);
        if (state==null) return List.of();
        Set<UUID> members=new TreeSet<>(); members.add(state.rulerId());
        settlements.values().stream().filter(city -> kingdom.equals(city.sovereign())).forEach(city -> members.addAll(citizens(city.id())));
        return List.copyOf(members);
    }
    void setKingdomRole(UUID kingdom,UUID player,KingdomRole role) throws FoundingException {
        requireAvailable();
        if (kingdomRole(kingdom,player)==null) throw new FoundingException(KINGDOM_NOT_MEMBER);
        if (kingdomRole(kingdom,player)==KingdomRole.RULER||role==null||role==KingdomRole.RULER) throw new FoundingException(KINGDOM_ROLE_FORBIDDEN);
        if (role==KingdomRole.MEMBER) governmentRoles.getOrDefault(kingdom,new HashMap<>()).remove(player);
        else governmentRoles.computeIfAbsent(kingdom,key -> new HashMap<>()).put(player,role);
        pruneInvitations(); setDirty(); politicalChanged();
    }
    void transferRuler(UUID kingdom,UUID target) throws FoundingException {
        requireAvailable(); Kingdom state=byId(kingdom);
        if (state==null||kingdomRole(kingdom,target)==null||state.rulerId().equals(target)) throw new FoundingException(KINGDOM_ROLE_FORBIDDEN);
        kingdoms.put(kingdom,state.withRuler(target));
        Map<UUID,KingdomRole> roles=governmentRoles.computeIfAbsent(kingdom,key -> new HashMap<>());
        roles.remove(target);
        // The previous ruler remains an administrator only while still a city participant.
        if (kingdomRole(kingdom,state.rulerId())!=null) roles.put(state.rulerId(),KingdomRole.CHANCELLOR);
        pruneInvitations(); setDirty(); politicalChanged();
    }
    void addCityInvitation(CityInvitation invitation) throws FoundingException {
        requireAvailable();
        if (cityInvitations.values().stream().anyMatch(i -> i.kingdom().equals(invitation.kingdom())&&i.city().equals(invitation.city()))) throw new FoundingException(KINGDOM_INVITATION_DUPLICATE);
        cityInvitations.put(invitation.id(),invitation); setDirty(); politicalChanged();
    }
    void removeCityInvitation(UUID id) { if (cityInvitations.remove(id)!=null) { setDirty(); politicalChanged(); } }
    private void pruneInvitations() {
        boolean changed=cityInvitations.values().removeIf(i -> byId(i.kingdom())==null||settlement(i.city())==null
                ||settlement(i.city()).sovereign()!=null||!KingdomPermissions.allows(this,i.kingdom(),i.inviter(),KingdomPermission.INVITE_CITY));
        if (diplomacy.pruneUnauthorized(this)) changed=true;
        if (changed) setDirty();
    }
    private void pruneGovernment() {
        governmentRoles.entrySet().removeIf(e -> !kingdoms.containsKey(e.getKey()));
        governmentRoles.forEach((kingdom,roles) -> roles.keySet().removeIf(player -> kingdomRole(kingdom,player)==null));
        pruneInvitations();
    }
    private void protectRuler(Settlement city) throws FoundingException {
        Kingdom state=sovereign(city);
        if (state!=null&&isCitizen(city.id(),state.rulerId())) throw new FoundingException(KINGDOM_RULER_MUST_TRANSFER);
    }
    void detachCity(UUID cityId) throws FoundingException {
        requireAvailable(); Settlement city=settlement(cityId);
        if (city==null) throw new FoundingException(CITY_MISSING);
        Kingdom state=sovereign(city);
        if (state==null) throw new FoundingException(NO_KINGDOM);
        if (state.capitalSettlementId().equals(cityId)) throw new FoundingException(CITY_IS_CAPITAL);
        protectRuler(city);
        settlements.put(cityId,city.withSovereign(null)); pruneGovernment(); setDirty(); politicalChanged();
    }
    private CompoundTag unreadableData;
    private CompoundTag extensionSource=new CompoundTag();
    private Runnable politicalChange=() -> { };
    void onPoliticalChange(Runnable listener) { politicalChange=Objects.requireNonNull(listener); }
    void politicalChanged() { politicalChange.run(); }
    final DiplomacyData diplomacy=new DiplomacyData();
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
        citizens.put(player,new Membership(city,role)); membershipChanged(city); setDirty(); politicalChanged();
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
        citizens.put(player,new Membership(city,CityRole.CITIZEN)); membershipChanged(city); setDirty(); politicalChanged();
    }
    void removeCitizen(UUID city,UUID player) throws FoundingException {
        requireAvailable();
        Settlement current=settlement(city);
        if (current==null) throw new FoundingException(CITY_MISSING);
        if (current.ownerId().equals(player)) throw new FoundingException(OWNER_CANNOT_LEAVE);
        if (role(city,player)==null) throw new FoundingException(NOT_CITIZEN);
        Kingdom state=sovereign(current);
        if (state!=null&&state.rulerId().equals(player)) throw new FoundingException(KINGDOM_RULER_MUST_TRANSFER);
        citizens.remove(player); membershipChanged(city); pruneGovernment(); setDirty(); politicalChanged();
    }
    /** Internal lifecycle operation. Capital cities must survive until their state is dissolved. */
    void deleteSettlement(UUID city) throws FoundingException {
        requireAvailable();
        if (settlement(city)==null) throw new FoundingException(CITY_MISSING);
        if (kingdoms.values().stream().anyMatch(k -> k.capitalSettlementId().equals(city))) throw new FoundingException(CITY_IS_CAPITAL);
        protectRuler(settlement(city));
        citizens.values().removeIf(member -> city.equals(member.city()));
        claims.values().removeIf(city::equals);
        settlements.remove(city); membershipChanged(city); pruneGovernment(); setDirty(); politicalChanged();
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
    boolean available() { return unreadableData==null; }
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
        index(kingdom); settlements.put(city.id(),city); claims.put(capital,city.id()); setDirty(); politicalChanged();
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
        settlements.put(city.id(),city); claims.put(capital,city.id()); setDirty(); politicalChanged();
    }
    void elevate(Kingdom kingdom,Settlement city) throws FoundingException {
        validate(kingdom.founderId(),kingdom.name(),kingdom.dimension(),kingdom.capitalPos(),kingdom.capitalObeliskId());
        if (!city.equals(settlements.get(city.id()))||city.sovereign()!=null||!city.ownerId().equals(kingdom.founderId())) throw new FoundingException(ALREADY_AFFILIATED);
        if (kingdoms.containsKey(kingdom.id())||deleted.containsKey(kingdom.id())||settlements.containsKey(kingdom.id())||!kingdom.capitalSettlementId().equals(city.id())) throw new IllegalArgumentException("Invalid elevation identity");
        if (!city.id().equals(claimOwner(ClaimPos.at(kingdom.dimension(),kingdom.capitalPos())))) throw new FoundingException(CAPITAL_CONFLICT);
        index(kingdom); settlements.put(city.id(),city.withSovereign(kingdom.id())); setDirty(); politicalChanged();
    }
    void affiliate(Settlement city,UUID kingdom) throws FoundingException {
        requireAvailable();
        if (!city.equals(settlements.get(city.id()))||city.sovereign()!=null||!kingdoms.containsKey(kingdom)) throw new FoundingException(ALREADY_AFFILIATED);
        settlements.put(city.id(),city.withSovereign(kingdom)); pruneInvitations(); setDirty(); politicalChanged();
    }
    Kingdom rename(UUID id,KingdomName name) throws FoundingException {
        requireAvailable(); Kingdom current=kingdoms.get(id);
        if (current==null) throw new IllegalArgumentException("Unknown kingdom");
        UUID existing=names.get(name.key());
        if (existing!=null&&!existing.equals(id)) throw new FoundingException(DUPLICATE_NAME);
        if (current.name().equals(name)) return current;
        Kingdom renamed=current.withName(name); names.remove(current.name().key(),id); names.put(name.key(),id); kingdoms.put(id,renamed); setDirty(); politicalChanged(); return renamed;
    }
    void deleteKingdom(UUID id) throws FoundingException {
        requireAvailable(); Kingdom current=kingdoms.get(id);
        if (current==null) throw new IllegalArgumentException("Unknown kingdom");
        deleted.put(id,new DeletedAnchor(id,current.dimension(),current.capitalPos(),current.capitalObeliskId()));
        settlements.replaceAll((key,city) -> id.equals(city.sovereign())?city.withSovereign(null):city);
        kingdoms.remove(id); names.remove(current.name().key(),id); obelisks.remove(current.capitalObeliskId(),id);
        capitals.remove(GlobalPos.of(current.dimension(),current.capitalPos()),id);
        founders.remove(current.founderId(),id);
        diplomacy.delete(id);
        kingdoms.values().stream().filter(k -> k.founderId().equals(current.founderId())).sorted(order()).findFirst().ifPresent(k -> founders.put(k.founderId(),k.id()));
        governmentRoles.remove(id); pruneInvitations(); setDirty(); politicalChanged();
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
        tag.putInt("Version",KingdomSchema.CURRENT);
        ListTag list=new ListTag();
        for (Kingdom k:kingdoms.values().stream().sorted(Comparator.comparing(Kingdom::id)).toList()) {
            CompoundTag e=anchor(k.id(),k.name(),k.founderId(),k.dimension(),k.capitalPos(),k.capitalObeliskId(),k.createdAt(),k.capitalStatus());
            e.putUUID("Ruler",k.rulerId()); e.putUUID("CapitalSettlement",k.capitalSettlementId()); e.putInt("Color",k.color()); e.putString("Government",k.government()); list.add(e);
        }
        tag.put("Kingdoms",list); list=new ListTag();
        for (Settlement s:settlements.values().stream().sorted(Comparator.comparing(Settlement::id)).toList()) {
            CompoundTag e=anchor(s.id(),s.name(),s.founderId(),s.dimension(),s.obeliskPos(),s.obeliskId(),s.createdAt(),s.status());
            e.putUUID("Owner",s.ownerId()); if (s.sovereign()!=null) e.putUUID("Sovereign",s.sovereign());
            e.putInt("Color",s.color()); e.putBoolean("LegacyAnchor",s.legacyAnchor()); list.add(e);
        }
        tag.put("Settlements",list); list=new ListTag();
        for (var claim:claims.entrySet().stream().sorted(Comparator
                .comparing((Map.Entry<ClaimPos,UUID> entry) -> entry.getKey().dimension().location().toString())
                .thenComparingInt(entry -> entry.getKey().x()).thenComparingInt(entry -> entry.getKey().z())).toList()) {
            CompoundTag e=new CompoundTag(); e.putString("Dimension",claim.getKey().dimension().location().toString());
            e.putInt("X",claim.getKey().x()); e.putInt("Z",claim.getKey().z()); e.putUUID("Settlement",claim.getValue()); list.add(e);
        }
        tag.put("Claims",list); list=new ListTag();
        for (DeletedAnchor d:deleted.values().stream().sorted(Comparator.comparing(DeletedAnchor::kingdom)).toList()) { CompoundTag e=new CompoundTag(); e.putUUID("Id",d.kingdom()); e.putString("Dimension",d.dimension().location().toString()); e.putLong("Position",d.pos().asLong()); e.putUUID("Obelisk",d.obelisk()); list.add(e); }
        tag.put("DeletedStateAnchors",list); list=new ListTag();
        for (var member:citizens.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            CompoundTag e=new CompoundTag(); e.putUUID("Player",member.getKey()); e.putUUID("Settlement",member.getValue().city());
            e.putString("Role",member.getValue().role().name()); list.add(e);
        }
        tag.put("Citizens",list); list=new ListTag();
        for (var realm:governmentRoles.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
            for (var role:realm.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            CompoundTag e=new CompoundTag(); e.putUUID("Kingdom",realm.getKey()); e.putUUID("Player",role.getKey()); e.putString("Role",role.getValue().name()); list.add(e);
        }
        tag.put("KingdomRoles",list); list=new ListTag();
        for (CityInvitation invitation:cityInvitations.values().stream().sorted(Comparator.comparing(CityInvitation::id)).toList()) {
            CompoundTag e=new CompoundTag(); e.putUUID("Id",invitation.id()); e.putUUID("Kingdom",invitation.kingdom());
            e.putUUID("City",invitation.city()); e.putUUID("Inviter",invitation.inviter()); e.putLong("CreatedAt",invitation.createdAt()); list.add(e);
        }
        tag.put("CityInvitations",list); tag.put("Diplomacy",diplomacy.save());
        KingdomSchema.retainExtensions(extensionSource,tag); return tag;
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
        var data=new KingdomSavedData(); CompoundTag original=tag;
        try {
            int version=tag.getInt("Version");
            tag=KingdomSchema.migrate(tag); data.extensionSource=original.copy();
            List<Kingdom> parsed=new ArrayList<>();
            for (Tag entry:list(tag,"Kingdoms")) {
                CompoundTag e=(CompoundTag)entry; UUID id=e.getUUID("Id");
                if (e.contains("Color")&&!e.contains("Color",Tag.TAG_INT)||e.contains("Government")&&!e.contains("Government",Tag.TAG_STRING)) throw new IllegalArgumentException("Malformed political fields");
                parsed.add(new Kingdom(id,new KingdomName(e.getString("Name")),e.getUUID("Founder"),dimension(e),BlockPos.of(e.getLong("Position")),e.getUUID("Obelisk"),e.getLong("CreatedAt"),status(e),
                        version>=3?e.getUUID("CapitalSettlement"):id,e.contains("Color",Tag.TAG_INT)?e.getInt("Color"):PoliticalColors.fallback(id),e.contains("Government",Tag.TAG_STRING)?e.getString("Government"):"unspecified",e.getUUID("Ruler")));
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
            data.setDirty(version<KingdomSchema.CURRENT||!tag.equals(original)); data.repairAffiliations(); data.reconcileCapitals();
            {
                for (Tag entry:list(tag,"KingdomRoles")) {
                    CompoundTag e=(CompoundTag)entry; UUID kingdom=e.getUUID("Kingdom"),player=e.getUUID("Player");
                    KingdomRole role=KingdomRole.valueOf(e.getString("Role"));
                    if (role==KingdomRole.RULER||role==KingdomRole.MEMBER||data.byId(kingdom)!=null&&data.byId(kingdom).rulerId().equals(player)
                            ||data.governmentRoles.computeIfAbsent(kingdom,key -> new HashMap<>()).putIfAbsent(player,role)!=null)
                        throw new IllegalArgumentException("Conflicting kingdom role");
                }
                Set<String> pairs=new HashSet<>();
                for (Tag entry:list(tag,"CityInvitations")) {
                    CompoundTag e=(CompoundTag)entry;
                    CityInvitation invitation=new CityInvitation(e.getUUID("Id"),e.getUUID("Kingdom"),e.getUUID("City"),e.getUUID("Inviter"),e.getLong("CreatedAt"));
                    if (!e.contains("CreatedAt",Tag.TAG_LONG)||!pairs.add(invitation.kingdom()+":"+invitation.city())||data.cityInvitations.putIfAbsent(invitation.id(),invitation)!=null)
                        throw new IllegalArgumentException("Malformed/duplicate city invitation");
                }
                var before=data.governmentRoles.toString(); data.pruneGovernment();
                if (!before.equals(data.governmentRoles.toString())) data.setDirty();
            }
            if (data.diplomacy.load(tag.get("Diplomacy"),data)) data.setDirty();
            var owners=data.settlements.values().stream().collect(java.util.stream.Collectors.groupingBy(Settlement::ownerId,java.util.stream.Collectors.counting()));
            owners.forEach((owner,count) -> { if (count>1) RegnumNations.LOGGER.warn("Historical multiple domains for owner {}: {} records retained; claim management requires repair",owner,count); });
        } catch (RuntimeException exception) {
            data=new KingdomSavedData(); data.unreadableData=original.copy();
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
