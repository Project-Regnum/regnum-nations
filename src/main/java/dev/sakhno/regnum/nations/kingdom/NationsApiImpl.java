package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.api.v1.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import static dev.sakhno.regnum.nations.api.v1.ApiResult.Status.*;

/** Implementation detail. Companion mods consume NationsApi, not KingdomSavedData. */
public final class NationsApiImpl implements NationsApi {
    private final MinecraftServer server;
    public NationsApiImpl(MinecraftServer server) { this.server = Objects.requireNonNull(server); }
    private KingdomSavedData data() {
        if (!server.isSameThread()) throw new IllegalStateException("Nations API requires the server thread");
        return server.overworld() == null || server.isStopped() ? null : KingdomService.get(server).data();
    }
    private static boolean available(KingdomSavedData data) { return data != null && data.available(); }
    private static EntitySnapshot snapshot(KingdomSavedData data, EntityRef entity) {
        if (entity.scope() == EntityRef.Scope.CITY) {
            var city = data.settlement(entity.id()); return city == null ? null : PoliticalSnapshots.of(data, city);
        }
        var kingdom = data.byId(entity.id()); return kingdom == null ? null : PoliticalSnapshots.of(kingdom);
    }
    private boolean live(ServerPlayer actor) {
        return actor != null && actor.serverLevel().getServer() == server && actor.isAlive() && !actor.isRemoved()
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor;
    }
    private static boolean administrator(ServerPlayer actor) { return actor.createCommandSourceStack().hasPermission(3); }
    private ApiResult.Status failure(KingdomSavedData data, EntityRef entity) {
        if (!available(data)) return UNAVAILABLE;
        if (entity == null) return INVALID;
        return snapshot(data, entity) == null ? MISSING : VALID;
    }
    @Override public ApiResult<EntitySnapshot> resolve(EntityRef entity) {
        var data = data(); var status = failure(data, entity);
        return status == VALID ? ApiResult.valid(snapshot(data, entity)) : ApiResult.failure(status);
    }
    @Override public ApiResult<List<EntitySnapshot>> kingdoms() {
        var data = data(); if (!available(data)) return ApiResult.failure(UNAVAILABLE);
        return ApiResult.valid(data.kingdoms().stream().sorted(Comparator.comparing(Kingdom::id)).map(PoliticalSnapshots::of).toList());
    }
    @Override public ApiResult<List<EntitySnapshot>> cities() {
        var data = data(); if (!available(data)) return ApiResult.failure(UNAVAILABLE);
        return ApiResult.valid(data.settlements().stream().sorted(Comparator.comparing(Settlement::id)).map(s -> PoliticalSnapshots.of(data, s)).toList());
    }
    @Override public ApiResult<List<GovernmentRolePolicy>> roles(EntityRef entity) {
        var data = data(); var status = failure(data, entity); if (status != VALID) return ApiResult.failure(status);
        List<String> roles = entity.scope() == EntityRef.Scope.CITY
                ? Arrays.stream(CityRole.values()).map(Enum::name).toList() : Arrays.stream(KingdomRole.values()).map(Enum::name).toList();
        return ApiResult.valid(roles.stream().map(role -> new GovernmentRolePolicy(role, EconomyRolePolicies.leader(entity, role),
                data.economyPermissions(entity, role))).toList());
    }
    @Override public ApiResult<List<EntitySnapshot>> cities(UUID kingdom) {
        var data = data(); var status = failure(data, kingdom == null ? null : EntityRef.kingdom(kingdom));
        if (status != VALID) return ApiResult.failure(status);
        return ApiResult.valid(data.settlements().stream().filter(s -> kingdom.equals(s.sovereign()))
                .sorted(Comparator.comparing(Settlement::id)).map(s -> PoliticalSnapshots.of(data, s)).toList());
    }
    @Override public ApiResult<GovernmentMembership> membership(EntityRef entity, UUID player) {
        var data = data(); var status = failure(data, entity);
        if (status != VALID) return ApiResult.failure(status);
        if (player == null) return ApiResult.failure(INVALID);
        String role = data.economyRole(entity, player);
        if (role == null) return ApiResult.failure(MISSING);
        return ApiResult.valid(new GovernmentMembership(entity, player, role, EconomyRolePolicies.leader(entity, role),
                data.economyPermissions(entity, role)));
    }
    @Override public ApiResult<List<GovernmentMembership>> memberships(UUID player) {
        var data = data(); if (!available(data)) return ApiResult.failure(UNAVAILABLE);
        if (player == null) return ApiResult.failure(INVALID);
        var memberships = new ArrayList<GovernmentMembership>();
        data.settlements().stream().sorted(Comparator.comparing(Settlement::id)).forEach(city ->
                membership(EntityRef.city(city.id()), player).value().ifPresent(memberships::add));
        data.kingdoms().stream().sorted(Comparator.comparing(Kingdom::id)).forEach(kingdom ->
                membership(EntityRef.kingdom(kingdom.id()), player).value().ifPresent(memberships::add));
        return ApiResult.valid(List.copyOf(memberships));
    }
    @Override public ApiResult<List<TerritoryLocation>> claims(UUID city) {
        var data = data(); var status = failure(data, city == null ? null : EntityRef.city(city));
        if (status != VALID) return ApiResult.failure(status);
        return ApiResult.valid(data.settlementClaims(city).stream().map(p -> new TerritoryLocation(p.dimension().location(), p.x(), p.z()))
                .sorted(Comparator.comparing((TerritoryLocation p) -> p.dimension().toString()).thenComparingInt(TerritoryLocation::chunkX)
                        .thenComparingInt(TerritoryLocation::chunkZ)).toList());
    }
    @Override public ApiResult<EntitySnapshot> territoryAt(ResourceKey<Level> dimension, BlockPos position) {
        var data = data(); if (!available(data)) return ApiResult.failure(UNAVAILABLE);
        if (dimension == null || position == null) return ApiResult.failure(INVALID);
        if (server.getLevel(dimension) == null) return ApiResult.failure(UNAVAILABLE);
        UUID city = data.claimOwner(ClaimPos.at(dimension, position));
        return city == null ? ApiResult.failure(MISSING) : resolve(EntityRef.city(city));
    }
    @Override public ApiResult<Set<EconomyPermission>> permissions(ServerPlayer actor, EntityRef entity) {
        var data = data(); var status = failure(data, entity);
        if (status != VALID) return ApiResult.failure(status);
        if (!live(actor) || actor.isSpectator()) return ApiResult.failure(UNAUTHORIZED);
        return ApiResult.valid(administrator(actor) ? Set.of(EconomyPermission.values())
                : data.economyPermissions(entity, data.economyRole(entity, actor.getUUID())));
    }
    @Override public ApiResult<EconomyPermission> authorize(ServerPlayer actor, EntityRef entity, EconomyPermission permission) {
        if (permission == null) { data(); return ApiResult.failure(INVALID); }
        var result = permissions(actor, entity);
        if (!result.valid()) return ApiResult.failure(result.status());
        return result.value().orElseThrow().contains(permission) ? ApiResult.valid(permission) : ApiResult.failure(UNAUTHORIZED);
    }
    private ApiResult.Status canAdminister(KingdomSavedData data, ServerPlayer actor, EntityRef entity) {
        var status = failure(data, entity); if (status != VALID) return status;
        if (!live(actor) || actor.isSpectator()) return UNAUTHORIZED;
        return administrator(actor) || EconomyRolePolicies.leader(entity, data.economyRole(entity, actor.getUUID())) ? VALID : UNAUTHORIZED;
    }
    @Override public ApiResult<Set<EconomyPermission>> setRolePermissions(ServerPlayer actor, EntityRef entity, String role,
            Set<EconomyPermission> permissions) {
        var data = data(); var status = canAdminister(data, actor, entity);
        if (status != VALID) return ApiResult.failure(status);
        if (!EconomyRolePolicies.validRole(entity, role) || EconomyRolePolicies.leader(entity, role)
                || permissions == null || permissions.stream().anyMatch(Objects::isNull)) return ApiResult.failure(INVALID);
        data.setEconomyPermissions(entity, role, Set.copyOf(permissions));
        return ApiResult.valid(data.economyPermissions(entity, role));
    }
    @Override public ApiResult<EntitySnapshot> renameCity(ServerPlayer actor, UUID city, String name) {
        var data = data(); var entity = city == null ? null : EntityRef.city(city); var status = canAdminister(data, actor, entity);
        if (status != VALID) return ApiResult.failure(status);
        if (name == null) return ApiResult.failure(INVALID);
        try { data.renameSettlement(city, new KingdomName(name)); return resolve(entity); }
        catch (IllegalArgumentException | FoundingException failure) { return ApiResult.failure(INVALID); }
    }
    @Override public ApiResult<EntitySnapshot> transferCity(ServerPlayer actor, UUID city, UUID recipient) {
        var data = data(); var entity = city == null ? null : EntityRef.city(city); var status = canAdminister(data, actor, entity);
        if (status != VALID) return ApiResult.failure(status);
        if (recipient == null) return ApiResult.failure(INVALID);
        try { data.transferSettlement(city, recipient); return resolve(entity); }
        catch (FoundingException failure) { return ApiResult.failure(INVALID); }
    }
}
