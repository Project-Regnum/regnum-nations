package dev.sakhno.regnum.nations.api.v1;

import java.util.Objects;
import java.util.UUID;

/** Scope is part of identity: legacy capitals may share a city and kingdom UUID. */
public record EntityRef(Scope scope, UUID id) {
    public enum Scope { CITY, KINGDOM }
    public EntityRef { Objects.requireNonNull(scope); Objects.requireNonNull(id); }
    public static EntityRef city(UUID id) { return new EntityRef(Scope.CITY, id); }
    public static EntityRef kingdom(UUID id) { return new EntityRef(Scope.KINGDOM, id); }
}
