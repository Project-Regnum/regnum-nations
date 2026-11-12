package dev.sakhno.regnum.nations.api.v1;

import java.util.Set;

/** Entity-specific financial policy for one assignable government role. Leader rights are intrinsic. */
public record GovernmentRolePolicy(String role, boolean leader, Set<EconomyPermission> permissions) {
    public GovernmentRolePolicy { permissions = Set.copyOf(permissions); }
}
