package dev.sakhno.regnum.nations.api.client.v1;

import dev.sakhno.regnum.nations.api.v1.*;
import java.util.Set;

/** Immutable server-supplied political context, containing no financial state. Permissions describe
 * the moment of receipt and only guide UI. Every financial request requires fresh server checks.
 */
public record EconomyContext(EntitySnapshot entity, String role, Set<EconomyPermission> permissions) {
    public EconomyContext { permissions = Set.copyOf(permissions); }
}
