package dev.sakhno.regnum.nations.api.v1;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Immutable public identity. owner is the municipal owner or current ruler, never the historical founder.
 * Heraldry identifiers are kingdom UUIDs, suitable for Nations' client heraldry lookup.
 * An independent city has empty kingdom/heraldry IDs; a capital represents a city-state when
 * NationsApi.cities(kingdomId) contains that city alone. Color is an opaque ARGB value.
 */
public record EntitySnapshot(EntityRef ref, String name, UUID founder, UUID owner,
        Optional<UUID> kingdom, Optional<UUID> capitalCity, int color,
        Optional<UUID> heraldry, ResourceLocation dimension, BlockPos anchor) {
    public EntitySnapshot {
        Objects.requireNonNull(ref); Objects.requireNonNull(name); Objects.requireNonNull(founder); Objects.requireNonNull(owner);
        Objects.requireNonNull(kingdom); Objects.requireNonNull(capitalCity); Objects.requireNonNull(heraldry);
        Objects.requireNonNull(dimension); anchor = anchor.immutable();
    }
    public boolean independentCity() { return ref.scope() == EntityRef.Scope.CITY && kingdom.isEmpty(); }
    public boolean capital() { return capitalCity.filter(ref.id()::equals).isPresent() && ref.scope() == EntityRef.Scope.CITY; }
}
