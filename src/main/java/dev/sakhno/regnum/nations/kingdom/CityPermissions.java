package dev.sakhno.regnum.nations.kingdom;

import java.util.Set;
import java.util.UUID;

import static dev.sakhno.regnum.nations.kingdom.CityPermission.*;

/** Municipal policy only: roles come from current membership, never political affiliation. */
public final class CityPermissions {
    private static final Set<CityPermission> OWNER_PERMISSIONS = Set.of(CityPermission.values());
    private static final Set<CityPermission> OFFICER_PERMISSIONS = Set.of(BUILD, INTERACT, INVITE);
    private static final Set<CityPermission> CITIZEN_PERMISSIONS = Set.of(BUILD, INTERACT);

    private CityPermissions() { }

    /** Immutable defaults. Future configurable member policies belong here; OWNER always has every permission. */
    public static Set<CityPermission> permissions(CityRole role) {
        return switch (role) {
            case OWNER -> OWNER_PERMISSIONS;
            case OFFICER -> OFFICER_PERMISSIONS;
            case CITIZEN -> CITIZEN_PERMISSIONS;
        };
    }

    public static boolean allows(KingdomSavedData data, Settlement city, UUID player, CityPermission permission) {
        if (city == null || player == null) return false;
        CityRole role = data.role(city.id(), player);
        return role != null && permissions(role).contains(permission);
    }
}
