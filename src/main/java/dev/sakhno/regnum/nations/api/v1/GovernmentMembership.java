package dev.sakhno.regnum.nations.api.v1;

import java.util.Set;
import java.util.UUID;

/** Current role names are OWNER/OFFICER/CITIZEN or RULER/CHANCELLOR/OFFICER/MEMBER.
 * Permissions are copied from the scoped role policy. Re-evaluate before every financial action.
 */
public record GovernmentMembership(EntityRef entity, UUID player, String role, boolean leader,
        Set<EconomyPermission> permissions) {
    public GovernmentMembership { permissions = Set.copyOf(permissions); }
}
