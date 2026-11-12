package dev.sakhno.regnum.nations.api.diplomacy;

import dev.sakhno.regnum.nations.RegnumNations;
import java.util.Optional;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;

/** Synchronous post-mutation notifications, including expiration, revocation and dissolution.
 * Never emitted by loading/saving or failed actions. Listeners must not mutate diplomacy reentrantly.
 */
public final class DiplomacyEvents {
    private DiplomacyEvents() { }
    public enum Kind { PROPOSED, ACCEPTED, REJECTED, CANCELLED, EXPIRED, REVOKED, WAR_DECLARED, RELATION_CHANGED, KINGDOM_DISSOLVED }
    public record Change(Kind kind, DiplomacyApi.Snapshot before, Optional<DiplomacyApi.Snapshot> after) { }
    @FunctionalInterface public interface Listener { void changed(MinecraftServer server, Change change); }
    public static final Event<Listener> CHANGED=EventFactory.createArrayBacked(Listener.class,listeners -> (server,change) -> {
        for (var listener:listeners) {
            try { listener.changed(server,change); }
            catch (RuntimeException failure) { RegnumNations.LOGGER.error("Diplomacy lifecycle listener failed for {}",change,failure); }
        }
    });
}
