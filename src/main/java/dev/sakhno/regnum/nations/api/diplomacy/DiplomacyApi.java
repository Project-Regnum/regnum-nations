package dev.sakhno.regnum.nations.api.diplomacy;

import dev.sakhno.regnum.nations.api.v1.ApiResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Additive read-only server API. Call on the owning server thread. Snapshots are immutable,
 * include official pending proposals, and are intended for trusted server mods, not client export.
 * Events are not durable: reconcile snapshots at startup. No Economy dependency or financial state.
 */
public interface DiplomacyApi {
    enum Relation { NEUTRAL, ALLIED, HOSTILE, NON_AGGRESSION, AT_WAR }
    enum ProposalType { ALLIANCE, NON_AGGRESSION, PEACE, END_ALLIANCE, END_NON_AGGRESSION }
    record Proposal(UUID id, UUID sender, UUID recipient, UUID issuer, ProposalType type, long createdAt, long expiresAt) { }
    record Treaty(UUID id, Relation relation, long acceptedAt) { }
    record War(UUID id, UUID declarer, long declaredAt) { }
    record Snapshot(UUID first, UUID second, Relation relation, Optional<UUID> revision,
            Optional<Treaty> treaty, Optional<War> war, Set<UUID> hostilityDeclarations,
            Optional<Proposal> proposal, long nextProposalAt) {
        public Snapshot { hostilityDeclarations=Set.copyOf(hostilityDeclarations); }
    }
    static DiplomacyApi get(MinecraftServer server) {
        return new DiplomacyApi() {
            public ApiResult<Snapshot> relation(UUID first, UUID second) {
                return dev.sakhno.regnum.nations.kingdom.KingdomService.get(server).diplomacy().snapshot(first,second);
            }
            public ApiResult<List<Snapshot>> relations(UUID kingdom) {
                return dev.sakhno.regnum.nations.kingdom.KingdomService.get(server).diplomacy().snapshots(kingdom);
            }
        };
    }
    ApiResult<Snapshot> relation(UUID first, UUID second);
    /** All other existing kingdoms, including neutral relations. Missing/dissolved kingdoms return MISSING. */
    ApiResult<List<Snapshot>> relations(UUID kingdom);
}
