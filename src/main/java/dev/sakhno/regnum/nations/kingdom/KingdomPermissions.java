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
            case RULER, CHANCELLOR -> ADMIN;
            case OFFICER -> Set.of(INVITE_CITY);
            case MEMBER -> Set.of();
        };
    }
    public static boolean allows(KingdomSavedData data, UUID kingdom, UUID player, KingdomPermission permission) {
        return data.available()&&permissions(data.kingdomRole(kingdom,player)).contains(permission);
    }
    public static boolean canAssign(KingdomRole actor, KingdomRole target, KingdomRole assigned) {
        return actor != null && target != null && assigned != null && assigned != KingdomRole.RULER
                && permissions(actor).contains(MANAGE_KINGDOM_ROLES)
                && actor.ordinal() < target.ordinal() && actor.ordinal() < assigned.ordinal();
    }
}
