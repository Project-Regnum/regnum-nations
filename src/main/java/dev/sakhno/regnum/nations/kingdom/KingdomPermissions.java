package dev.sakhno.regnum.nations.kingdom;

import java.util.Set;
import java.util.UUID;
import static dev.sakhno.regnum.nations.kingdom.KingdomPermission.*;

/** Political authority only. Never used to grant municipal/build permissions. */
public final class KingdomPermissions {
    private static final Set<KingdomPermission> ADMIN = Set.of(KingdomPermission.values());
    private KingdomPermissions() { }
    public static Set<KingdomPermission> permissions(KingdomRole role) {
        if (role == null) return Set.of();
        return switch (role) {
            case RULER -> ADMIN;
            case CHANCELLOR -> Set.of(MANAGE_KINGDOM, MANAGE_KINGDOM_ROLES, INVITE_CITY, REMOVE_CITY,
                    MANAGE_KINGDOM_MEMBERS, MANAGE_DIPLOMACY, MANAGE_HERALDRY,
                    VIEW_DIPLOMACY, PROPOSE_ALLIANCE, MANAGE_TREATIES, DECLARE_WAR, NEGOTIATE_PEACE, DECLARE_SIEGE, MANAGE_WAR_OBJECTIVES, VIEW_WARFARE, VIEW_TERRITORY, MANAGE_TERRITORIAL_CLAIMS);
            case OFFICER -> Set.of(INVITE_CITY, VIEW_DIPLOMACY, VIEW_WARFARE, DECLARE_SIEGE, VIEW_TERRITORY);
            case MEMBER -> Set.of(VIEW_DIPLOMACY, VIEW_WARFARE, VIEW_TERRITORY);
        };
    }
    public static boolean allows(KingdomSavedData data, UUID kingdom, UUID player, KingdomPermission permission) {
        if (permission.name().startsWith("ECONOMY_")) return data.economyAllows(
                dev.sakhno.regnum.nations.api.v1.EntityRef.kingdom(kingdom), player,
                dev.sakhno.regnum.nations.api.v1.EconomyPermission.valueOf(permission.name()));
        return data.available()&&permissions(data.kingdomRole(kingdom,player)).contains(permission);
    }
    public static boolean canAssign(KingdomRole actor, KingdomRole target, KingdomRole assigned) {
        return actor != null && target != null && assigned != null && assigned != KingdomRole.RULER
                && permissions(actor).contains(MANAGE_KINGDOM_ROLES)
                && actor.ordinal() < target.ordinal() && actor.ordinal() < assigned.ordinal();
    }
}
