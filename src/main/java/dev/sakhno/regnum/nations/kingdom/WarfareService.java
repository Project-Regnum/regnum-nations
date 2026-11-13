package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.block.CityObeliskBlockEntity;
import dev.sakhno.regnum.nations.block.KingdomObeliskBlockEntity;
import dev.sakhno.regnum.nations.networking.WarfarePayloads.*;
import dev.sakhno.regnum.nations.kingdom.WarfareData.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import static dev.sakhno.regnum.nations.kingdom.KingdomPermission.*;

/** Server-thread authority. Only active objective areas are queried, once per server second. */
public final class WarfareService {
    private final KingdomService kingdoms;
    private final KingdomSavedData data;
    private final Map<UUID,ServerBossEvent> bars=new HashMap<>();
    private final Map<UUID,Presence> presence=new HashMap<>();
    private record Presence(List<ServerPlayer> attackers,List<ServerPlayer> defenders,List<ServerPlayer> viewers) { }
    private long nextStep;
    WarfareService(KingdomService kingdoms) {
        this.kingdoms=kingdoms; data=kingdoms.data();
        nextStep=kingdoms.server().overworld().getGameTime()+20;
        // A restart interrupts a continuous defense hold, but retains all capture progress.
        for (var r:data.warfare.activeRecords()) if (r.defenseProgress()>0)
            store(r.update(r.state(),r.readyAt(),r.endsAt(),0,r.progress(),0,r.breached()));
    }
    private static final class Failure extends Exception {
        final String key;
        Failure(String key) { this.key="warfare.regnum-nations."+key; }
    }
    private void requireAvailable() throws FoundingException,Failure {
        data.requireAvailable(); if (!data.diplomacy.available()||!data.warfare.available()) throw new Failure("unavailable");
    }
    private Kingdom actor(ServerPlayer player,UUID expected,KingdomPermission permission) throws FoundingException,Failure {
        kingdoms.validatePlayer(player); requireAvailable();
        Kingdom own=data.playerKingdom(player.getUUID());
        if (own==null||!own.id().equals(expected)||!KingdomPermissions.allows(data,own.id(),player.getUUID(),permission)) throw new Failure("permission");
        return own;
    }
    private DiplomacyData.War war(UUID attacker,UUID defender) {
        if (attacker==null||defender==null||attacker.equals(defender)) return null;
        var pair=data.diplomacy.get(new DiplomacyData.Pair(attacker,defender)); return pair==null?null:pair.war();
    }
    public Snapshot handle(ServerPlayer player,Request request) {
        String result=""; boolean success=false;
        try {
            requireAvailable(); reconcile();
            if (request.action()!=Action.VIEW) { act(player,request); result="warfare.regnum-nations.updated"; success=true; }
            return view(player,request,result,success);
        } catch (FoundingException e) { result=e.translationKey(); }
        catch (Failure e) { result=e.key; }
        try { return view(player,request,result,false); }
        catch (FoundingException|Failure e) { return error(request.sequence(),result); }
    }
    public static Snapshot error(int sequence,String key) { return new Snapshot(sequence,null,Tab.WARS,0,0,List.of(),Set.of(),key,false); }
    private void act(ServerPlayer player,Request request) throws FoundingException,Failure {
        KingdomPermission permission=switch (request.action()) {
            case DECLARE_SIEGE, WITHDRAW -> DECLARE_SIEGE;
            case PEACE_OBJECTIVE -> NEGOTIATE_PEACE;
            default -> MANAGE_WAR_OBJECTIVES;
        };
        Kingdom own=actor(player,request.kingdom(),permission); long now=System.currentTimeMillis();
        if (request.action()==Action.CAPTURE_OBJECTIVE||request.action()==Action.DEFEND_OBJECTIVE||request.action()==Action.PEACE_OBJECTIVE) {
            if (!KingdomPermissions.allows(data,own.id(),player.getUUID(),MANAGE_WAR_OBJECTIVES)) throw new Failure("permission");
            Settlement city=data.settlement(request.city());
            if (city==null||city.sovereign()==null||data.warfare.active(city.id())!=null
                    ||!Objects.equals(data.warfare.cityRevision(city.id()),request.revision())) throw new Failure("stale");
            boolean defense=request.action()==Action.DEFEND_OBJECTIVE;
            UUID attacker=defense?opponent(own.id(),request.war()):own.id(),defender=city.sovereign();
            if (defense&&!own.id().equals(defender)||!defense&&own.id().equals(defender)) throw new Failure("target");
            var war=war(attacker,defender);
            if (war==null||!war.id().equals(request.war())) throw new Failure("war_required");
            if (data.warfare.activeRecords().size()>=WarfareConfig.MAX_ACTIVE) throw new Failure("limit");
            Objective objective=defense?Objective.DEFEND_CITY:request.action()==Action.PEACE_OBJECTIVE?Objective.NEGOTIATED_PEACE:Objective.CAPTURE_CITY;
            var record=new ObjectiveRecord(UUID.randomUUID(),UUID.randomUUID(),war.id(),attacker,defender,city.id(),city.ownerId(),city.obeliskId(),city.dimension().location().toString(),city.obeliskPos(),
                    objective,State.PLANNED,now,0,0,0,0,WarfareConfig.captureSeconds,WarfareConfig.defenseSeconds,0,false);
            if (!cityValid(record)) throw new Failure("target");
            store(record); notify(record,"objective"); KingdomManagementServer.invalidate(kingdoms.server()); return;
        }
        var record=data.warfare.get(request.objective());
        if (record==null||record.completed()||!Objects.equals(record.revision(),request.revision())
                ||!Objects.equals(record.city(),request.city())||!Objects.equals(record.war(),request.war())) throw new Failure("stale");
        switch (request.action()) {
            case DECLARE_SIEGE -> {
                if (!record.attacker().equals(own.id())) throw new Failure("permission");
                if (record.state()!=State.PLANNED||record.objective()==Objective.NEGOTIATED_PEACE) throw new Failure("target");
                if (!valid(record)||!obelisk(record).equals("present")) throw new Failure("obelisk_required");
                long ready=now+1000L*WarfareConfig.preparationSeconds;
                store(record.update(State.PREPARING,ready,ready+1000L*WarfareConfig.durationSeconds,0,0,0,false)); notify(record,"declared");
                KingdomManagementServer.invalidate(kingdoms.server());
            }
            case WITHDRAW -> {
                if (!record.attacker().equals(own.id())||!record.siege()) throw new Failure("permission");
                finish(record,State.DEFENDER_VICTORY);
            }
            case CANCEL_OBJECTIVE -> {
                // Governments may withdraw their own plans. Only an administrator cancels a live siege.
                boolean ownPlan=record.objective()==Objective.DEFEND_CITY?record.defender().equals(own.id()):record.attacker().equals(own.id());
                if (!ownPlan||record.state()!=State.PLANNED) throw new Failure("permission");
                finish(record,State.CANCELLED);
            }
            default -> throw new Failure("target");
        }
    }
    private UUID opponent(UUID kingdom,UUID warId) {
        for (var item:data.diplomacy.entries().entrySet()) if (item.getKey().contains(kingdom)&&item.getValue().war()!=null&&item.getValue().war().id().equals(warId))
            return item.getKey().first().equals(kingdom)?item.getKey().second():item.getKey().first();
        return null;
    }
    private boolean cityValid(ObjectiveRecord r) {
        Settlement city=data.settlement(r.city());
        return city!=null&&data.byId(r.attacker())!=null&&data.byId(r.defender())!=null&&r.defender().equals(city.sovereign())
                &&r.owner().equals(city.ownerId())&&r.obelisk().equals(city.obeliskId())&&r.pos().equals(city.obeliskPos())
                &&r.dimension().equals(city.dimension().location().toString())
                &&city.id().equals(data.claimOwner(ClaimPos.at(city.dimension(),city.obeliskPos())));
    }
    private boolean valid(ObjectiveRecord r) {
        var war=war(r.attacker(),r.defender()); return cityValid(r)&&war!=null&&war.id().equals(r.war());
    }
    /** Called on diplomacy transitions, startup, and before requests; terminal results never run twice. */
    public void reconcile() {
        if (!data.available()||!data.warfare.available()||!data.diplomacy.available()) { clearBars(); return; }
        for (var r:data.warfare.activeRecords()) {
            if (!cityValid(r)) finish(r,State.INVALID);
            else if (!valid(r)) finish(r,State.WAR_ENDED);
        }
    }
    void peace(UUID war) {
        if (!data.available()||!data.warfare.available()) return;
        for (var r:data.warfare.activeRecords()) if (r.war().equals(war))
            finish(r,r.objective()==Objective.NEGOTIATED_PEACE&&cityValid(r)?State.PEACE:State.WAR_ENDED);
    }
    private void store(ObjectiveRecord record) { data.warfare.put(record); data.setDirty(); }
    private void finish(ObjectiveRecord record,State outcome) {
        var current=data.warfare.get(record.id()); if (current==null||current.completed()) return;
        var done=current.update(outcome,current.readyAt(),current.endsAt(),Math.max(current.startedAt(),System.currentTimeMillis()),current.progress(),current.defenseProgress(),current.breached());
        store(done); var bar=bars.remove(record.id()); if (bar!=null) bar.removeAllPlayers(); presence.remove(record.id()); notify(done,"resolved");
        KingdomManagementServer.invalidate(kingdoms.server());
    }
    void cancel(UUID id) throws FoundingException {
        data.requireAvailable(); var record=data.warfare.get(id);
        if (record==null||record.completed()) throw new FoundingException(FoundingException.Reason.CITY_MISSING);
        finish(record,State.CANCELLED);
    }
    private void clearBars() { bars.values().forEach(ServerBossEvent::removeAllPlayers); bars.clear(); presence.clear(); }
    void disconnect(ServerPlayer player) { bars.values().forEach(bar -> bar.removePlayer(player)); presence.clear(); }
    public boolean blocksRelocation(UUID city) { var r=data.warfare.active(city); return !data.warfare.available()||r!=null; }
    private ServerLevel level(ObjectiveRecord r) {
        Settlement city=data.settlement(r.city()); return city==null?null:kingdoms.server().getLevel(city.dimension());
    }
    private String obelisk(ObjectiveRecord r) {
        ServerLevel level=level(r); if (level==null) return "missing";
        var chunk=level.getChunkSource().getChunkNow(r.pos().getX()>>4,r.pos().getZ()>>4); if (chunk==null) return "unloaded";
        if (!(chunk.getBlockEntity(r.pos()) instanceof KingdomObeliskBlockEntity block)||block.isRemoved()||!r.obelisk().equals(block.getObeliskId())) return "missing";
        Settlement city=data.settlement(r.city());
        if (city==null) return "missing";
        if (city.legacyAnchor()) {
            var capital=data.atCapital(city.dimension(),city.obeliskPos());
            return !(block instanceof CityObeliskBlockEntity)&&(capital==null?block.getKingdomId()==null:capital.id().equals(block.getKingdomId()))?"present":"missing";
        }
        return block instanceof CityObeliskBlockEntity&&city.id().equals(block.getKingdomId())?"present":"missing";
    }
    /** No packet can enroll a player or add progress: sneaking, reach and line of sight are server observations. */
    private Presence sample(ObjectiveRecord r) {
        ServerLevel level=level(r); if (level==null) return new Presence(List.of(),List.of(),List.of());
        var nearby=level.getEntitiesOfClass(ServerPlayer.class,new AABB(r.pos()).inflate(WarfareConfig.AREA_RADIUS),p -> p.isAlive()&&!p.isRemoved()&&!p.isSpectator());
        List<ServerPlayer> attackers=new ArrayList<>(),defenders=new ArrayList<>(),viewers=new ArrayList<>();
        for (var p:nearby) {
            try {
                Kingdom own=data.playerKingdom(p.getUUID()); if (own==null) continue;
                boolean attack=own.id().equals(r.attacker()),defend=own.id().equals(r.defender()); if (!attack&&!defend) continue;
                viewers.add(p);
                if (p.isCreative()||!p.isShiftKeyDown()||p.distanceToSqr(Vec3.atCenterOf(r.pos()))>WarfareConfig.CAPTURE_RADIUS*WarfareConfig.CAPTURE_RADIUS) continue;
                Vec3 target=Vec3.atCenterOf(r.pos());
                var hit=level.clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(),target,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
                if (hit.getType()!=HitResult.Type.MISS&&!hit.getBlockPos().equals(r.pos())) continue;
                (attack?attackers:defenders).add(p);
            } catch (FoundingException ignored) { /* Ambiguous membership never grants participation. */ }
        }
        return new Presence(List.copyOf(attackers),List.copyOf(defenders),List.copyOf(viewers));
    }
    void step() {
        long tick=kingdoms.server().overworld().getGameTime(); if (tick<nextStep) return; nextStep=tick+20;
        reconcile(); if (!data.available()||!data.warfare.available()||!data.diplomacy.available()) return;
        long now=System.currentTimeMillis();
        for (var original:data.warfare.activeRecords()) if (original.siege()) {
            var r=original;
            if (now>=r.endsAt()) { finish(r,State.TIMEOUT); continue; }
            String anchor=obelisk(r);
            if (anchor.equals("missing")) { finish(r,State.INVALID); continue; }
            if (r.state()==State.PREPARING&&now>=r.readyAt()) {
                r=r.update(State.ACTIVE,r.readyAt(),r.endsAt(),0,r.progress(),0,r.breached()); store(r); notify(r,"active");
            }
            Presence current=sample(r); presence.put(r.id(),current);
            if (anchor.equals("unloaded")&&r.defenseProgress()>0) {
                r=r.update(r.state(),r.readyAt(),r.endsAt(),0,r.progress(),0,r.breached()); store(r);
            }
            if (r.state()==State.ACTIVE&&original.state()==State.ACTIVE&&anchor.equals("present")) {
                int progress=r.progress(),defense=0; boolean breached=r.breached();
                if (!current.attackers().isEmpty()&&current.defenders().isEmpty()) { progress=Math.min(r.captureSeconds(),progress+1); breached=true; }
                else if (current.attackers().isEmpty()&&!current.defenders().isEmpty()) {
                    progress=Math.max(0,progress-1); if (breached&&progress==0) defense=Math.min(r.defenseSeconds(),r.defenseProgress()+1);
                }
                if (progress!=r.progress()||defense!=r.defenseProgress()||breached!=r.breached()) {
                    r=r.update(r.state(),r.readyAt(),r.endsAt(),0,progress,defense,breached); store(r);
                }
                if (progress>=r.captureSeconds()) { finish(r,State.ATTACKER_VICTORY); continue; }
                if (defense>=r.defenseSeconds()) { finish(r,State.DEFENDER_VICTORY); continue; }
            }
            updateBar(r,current,now,anchor);
        }
    }
    private void updateBar(ObjectiveRecord r,Presence p,long now,String anchor) {
        var bar=bars.computeIfAbsent(r.id(),id -> new ServerBossEvent(text("title"),BossEvent.BossBarColor.RED,BossEvent.BossBarOverlay.PROGRESS));
        bar.setName(text(r.state()==State.PREPARING?"boss_preparation":"boss_active",cityName(r.city()),r.state()==State.PREPARING?seconds(r.readyAt(),now):r.progress(),r.captureSeconds(),text("obelisk."+anchor)));
        bar.setProgress((float)r.progress()/r.captureSeconds());
        for (var player:List.copyOf(bar.getPlayers())) if (!p.viewers().contains(player)) bar.removePlayer(player);
        for (var player:p.viewers()) bar.addPlayer(player);
    }
    private void notify(ObjectiveRecord r,String event) {
        Component message=text("notification."+event,kingdomName(r.attacker()),kingdomName(r.defender()),cityName(r.city()),text("state."+r.state().name().toLowerCase(Locale.ROOT)));
        Set<UUID> members=new HashSet<>(data.kingdomMembers(r.attacker())); members.addAll(data.kingdomMembers(r.defender()));
        // Membership UUID lookup only, never a repeating full-server player scan.
        for (UUID id:members) { var player=kingdoms.server().getPlayerList().getPlayer(id); if (player!=null) player.displayClientMessage(message,false); }
    }
    private String kingdomName(UUID id) { var k=data.byId(id); return k==null?id.toString():k.name().display(); }
    private String cityName(UUID id) { var city=data.settlement(id); return city==null?id.toString():city.name().display(); }
    private static long seconds(long at,long now) { return Math.max(0,(at-now+999)/1000); }
    private static Component text(String key,Object... args) { return Component.translatable("warfare.regnum-nations."+key,args); }
    private Snapshot view(ServerPlayer player,Request request,String result,boolean success) throws FoundingException,Failure {
        kingdoms.validatePlayer(player); requireAvailable();
        var own=data.playerKingdom(player.getUUID());
        if (own==null||!KingdomPermissions.allows(data,own.id(),player.getUUID(),VIEW_WARFARE)) throw new Failure("permission");
        Set<KingdomPermission> permissions=EnumSet.noneOf(KingdomPermission.class);
        for (var permission:List.of(VIEW_WARFARE,DECLARE_SIEGE,MANAGE_WAR_OBJECTIVES,NEGOTIATE_PEACE))
            if (KingdomPermissions.allows(data,own.id(),player.getUUID(),permission)) permissions.add(permission);
        List<Entry> rows=new ArrayList<>(); long now=System.currentTimeMillis();
        if (request.tab()==Tab.WARS||request.tab()==Tab.CITIES) {
            for (var item:data.diplomacy.entries().entrySet()) {
                var war=item.getValue().war(); if (war==null||!item.getKey().contains(own.id())) continue;
                UUID other=item.getKey().first().equals(own.id())?item.getKey().second():item.getKey().first();
                if (request.tab()==Tab.WARS) {
                    UUID attacker=war.declarer(),defender=attacker.equals(own.id())?other:own.id();
                    rows.add(new Entry(null,null,war.id(),null,attacker,defender,"",kingdomName(attacker),kingdomName(defender),null,null,war.declaredAt(),0,0,0,0,0,0,0,"",0,0,"",false));
                } else for (var city:data.settlements()) if (city.sovereign()!=null&&(city.sovereign().equals(own.id())||city.sovereign().equals(other))) {
                    if (data.warfare.active(city.id())!=null) continue;
                    boolean defense=city.sovereign().equals(own.id()); UUID attacker=defense?other:own.id(),defender=city.sovereign();
                    rows.add(new Entry(null,data.warfare.cityRevision(city.id()),war.id(),city.id(),attacker,defender,city.name().display(),kingdomName(attacker),kingdomName(defender),null,null,0,0,0,0,0,0,0,0,"",0,0,"",false));
                }
            }
            rows.sort(Comparator.comparing(Entry::attackerName).thenComparing(Entry::cityName).thenComparing(Entry::war));
        } else {
            var records=data.warfare.records().stream().filter(r -> r.attacker().equals(own.id())||r.defender().equals(own.id()))
                    .filter(r -> request.tab()==Tab.HISTORY?r.completed():request.tab()==Tab.SIEGES?r.siege():!r.completed())
                    .sorted(Comparator.comparingLong(ObjectiveRecord::startedAt).reversed().thenComparing(ObjectiveRecord::id)).toList();
            for (var r:records) rows.add(entry(r,now));
        }
        int page=Math.clamp(request.page(),0,Math.max(0,(rows.size()-1)/6)),start=page*6;
        return new Snapshot(request.sequence(),own.id(),request.tab(),page,rows.size(),rows.subList(start,Math.min(rows.size(),start+6)),permissions,result,success);
    }
    private Entry entry(ObjectiveRecord r,long now) {
        var p=presence.get(r.id()); int attackers=p==null?0:p.attackers().size(),defenders=p==null?0:p.defenders().size();
        String participants="";
        if (p!=null) participants=java.util.stream.Stream.concat(p.attackers().stream(),p.defenders().stream()).limit(16)
                .map(player -> player.getGameProfile().getName()).sorted().collect(java.util.stream.Collectors.joining(", "));
        return new Entry(r.id(),r.revision(),r.war(),r.city(),r.attacker(),r.defender(),cityName(r.city()),kingdomName(r.attacker()),kingdomName(r.defender()),r.objective(),r.state(),r.startedAt(),
                r.siege()?seconds(r.readyAt(),now):0,r.siege()?seconds(r.endsAt(),now):0,r.completedAt(),r.progress(),r.captureSeconds(),r.defenseProgress(),r.defenseSeconds(),r.completed()?"archived":obelisk(r),attackers,defenders,participants,r.pendingConquest());
    }
}
