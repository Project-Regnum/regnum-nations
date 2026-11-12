package dev.sakhno.regnum.nations.api.v1;

import dev.sakhno.regnum.nations.RegnumNations;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;

/** Synchronous post-commit events on the owning server thread, never on save/load or failed/no-op
 * mutations. Listeners should reconcile by stable IDs and avoid reentrant political mutations.
 * Listener failure is isolated; delivery is in-process, not a durable transaction log. Reconcile
 * persisted Economy ownership through NationsApi at startup as well. Nations never touches money.
 */
public final class NationsEvents {
    private NationsEvents() { }
    public enum Kind { KINGDOM_CREATED, KINGDOM_DISSOLVED, CITY_CREATED, CITY_DISSOLVED,
        CITY_JOINED_KINGDOM, CITY_LEFT_KINGDOM, CITY_OWNERSHIP_TRANSFERRED,
        KINGDOM_LEADERSHIP_TRANSFERRED, RENAMED, IDENTITY_CHANGED }
    public sealed interface Change permits EntityChanged, RoleChanged, PermissionsChanged, TerritoryChanged { }
    public record EntityChanged(Kind kind, Optional<EntitySnapshot> before, Optional<EntitySnapshot> after) implements Change { }
    /** Empty role denotes membership removal; owner/ruler changes are also explicit entity events. */
    public record RoleChanged(EntityRef entity, UUID player, Optional<String> before, Optional<String> after) implements Change { }
    public record PermissionsChanged(EntityRef entity, String role, Set<EconomyPermission> before,
            Set<EconomyPermission> after) implements Change {
        public PermissionsChanged { before = Set.copyOf(before); after = Set.copyOf(after); }
    }
    /** Incremental claims. CITY_CREATED/CITY_DISSOLVED also invalidate that city's entire territory. */
    public record TerritoryChanged(TerritoryLocation location, Optional<EntityRef> before, Optional<EntityRef> after) implements Change { }
    @FunctionalInterface public interface Listener { void changed(MinecraftServer server, Change change); }
    public static final Event<Listener> CHANGED = EventFactory.createArrayBacked(Listener.class, listeners -> (server, change) -> {
        for (var listener : listeners) {
            try { listener.changed(server, change); }
            catch (RuntimeException failure) { RegnumNations.LOGGER.error("Nations lifecycle listener failed for {}", change, failure); }
        }
    });
}
