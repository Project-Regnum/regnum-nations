package dev.sakhno.regnum.nations.kingdom;

import java.util.Set;
import java.util.UUID;

import static dev.sakhno.regnum.nations.kingdom.CityPermission.*;

/** Municipal policy only: roles come from current membership, never political affiliation. */
public final class CityPermissions {
    private static final Set<CityPermission> OWNER_PERMISSIONS = Set.of(CityPermission.values());
    private static final Set<CityPermission> OFFICER_PERMISSIONS = Set.of(BUILD, INTERACT, INVITE, VIEW_TERRITORY, EXPAND_TERRITORY);
    private static final Set<CityPermission> CITIZEN_PERMISSIONS = Set.of(BUILD, INTERACT, VIEW_TERRITORY);

    private CityPermissions() { }

    /** Immutable defaults. Per-city economic overrides are evaluated by allows; OWNER retains every permission. */
    public static Set<CityPermission> permissions(CityRole role) {
        return switch (role) {
            case OWNER -> OWNER_PERMISSIONS;
            case OFFICER -> OFFICER_PERMISSIONS;
            case CITIZEN -> CITIZEN_PERMISSIONS;
        };
    }

    public static boolean allows(KingdomSavedData data, Settlement city, UUID player, CityPermission permission) {
        if (city == null || player == null || !data.available()) return false;
        if (permission.name().startsWith("ECONOMY_")) return data.economyAllows(
                dev.sakhno.regnum.nations.api.v1.EntityRef.city(city.id()), player,
                dev.sakhno.regnum.nations.api.v1.EconomyPermission.valueOf(permission.name()));
        CityRole role = data.role(city.id(), player);
        return role != null && permissions(role).contains(permission);
    }
}
