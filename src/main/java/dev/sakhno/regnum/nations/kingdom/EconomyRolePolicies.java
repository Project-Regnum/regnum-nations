package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.api.v1.*;
import java.util.*;
import net.minecraft.nbt.*;

/** Additive political policy, not financial storage. Scope remains part of every map key. */
final class EconomyRolePolicies {
    private final Map<EntityRef, Map<String, Set<EconomyPermission>>> policies = new HashMap<>();
    static boolean leader(EntityRef entity, String role) {
        return (entity.scope() == EntityRef.Scope.CITY ? "OWNER" : "RULER").equals(role);
    }
    static boolean validRole(EntityRef entity, String role) {
        if (role == null) return false;
        return entity.scope() == EntityRef.Scope.CITY
                ? Arrays.stream(CityRole.values()).anyMatch(r -> r.name().equals(role))
                : Arrays.stream(KingdomRole.values()).anyMatch(r -> r.name().equals(role));
    }
    Set<EconomyPermission> permissions(EntityRef entity, String role) {
        if (role == null) return Set.of();
        if (leader(entity, role)) return Set.of(EconomyPermission.values());
        return policies.getOrDefault(entity, Map.of()).getOrDefault(role, Set.of());
    }
    void put(EntityRef entity, String role, Set<EconomyPermission> permissions) {
        if (!validRole(entity, role) || leader(entity, role)) throw new IllegalArgumentException("Invalid economic role policy");
        if (permissions.isEmpty()) {
            var roles = policies.get(entity);
            if (roles != null) { roles.remove(role); if (roles.isEmpty()) policies.remove(entity); }
        } else policies.computeIfAbsent(entity, key -> new HashMap<>()).put(role, Set.copyOf(permissions));
    }
    void remove(EntityRef entity) { policies.remove(entity); }
    CompoundTag save() {
        var root = new CompoundTag();
        for (var scope : EntityRef.Scope.values()) {
            var entities = new CompoundTag();
            policies.entrySet().stream().filter(e -> e.getKey().scope() == scope)
                    .sorted(Comparator.comparing(e -> e.getKey().id())).forEach(e -> {
                var roles = new CompoundTag();
                new TreeMap<>(e.getValue()).forEach((role, permissions) -> {
                    var list = new ListTag();
                    permissions.stream().sorted().forEach(p -> list.add(StringTag.valueOf(p.name())));
                    roles.put(role, list);
                });
                entities.put(e.getKey().id().toString(), roles);
            });
            root.put(scope.name(), entities);
        }
        return root;
    }
    void load(Tag source, KingdomSavedData data) {
        if (source == null) return; // Every older world starts with owner/ruler authority only.
        if (!(source instanceof CompoundTag root)) throw new IllegalArgumentException("Invalid economic policies");
        for (String key : root.getAllKeys()) {
            var scope = EntityRef.Scope.valueOf(key);
            if (!(root.get(key) instanceof CompoundTag entities)) throw new IllegalArgumentException("Invalid policy scope");
            for (String id : entities.getAllKeys()) {
                var entity = new EntityRef(scope, UUID.fromString(id));
                if (scope == EntityRef.Scope.CITY ? data.settlement(entity.id()) == null : data.byId(entity.id()) == null)
                    throw new IllegalArgumentException("Orphaned economic policy");
                if (!(entities.get(id) instanceof CompoundTag roles)) throw new IllegalArgumentException("Invalid policy roles");
                for (String role : roles.getAllKeys()) {
                    if (!(roles.get(role) instanceof ListTag list) || !list.isEmpty() && list.getElementType() != Tag.TAG_STRING
                            || list.size() > EconomyPermission.values().length) throw new IllegalArgumentException("Invalid permission list");
                    var permissions = EnumSet.noneOf(EconomyPermission.class);
                    for (int i = 0; i < list.size(); i++) {
                        if (!permissions.add(EconomyPermission.valueOf(list.getString(i)))) throw new IllegalArgumentException("Duplicate permission");
                    }
                    put(entity, role, permissions);
                }
            }
        }
    }
}
