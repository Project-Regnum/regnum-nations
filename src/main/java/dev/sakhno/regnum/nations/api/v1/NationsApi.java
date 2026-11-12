package dev.sakhno.regnum.nations.api.v1;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Public v1 companion-mod boundary. Obtain per server; call exclusively on its server thread
 * (network handlers must use server.execute). Off-thread access throws IllegalStateException.
 * Snapshot reads are trusted server-mod operations, not a network authorization mechanism.
 * Re-check authorize for each monetary mutation, including membership/leadership changes.
 * Nations stores political identities and policies only; balances belong to the caller.
 */
public interface NationsApi {
    static NationsApi get(MinecraftServer server) { return new dev.sakhno.regnum.nations.kingdom.NationsApiImpl(server); }
    ApiResult<EntitySnapshot> resolve(EntityRef entity);
    default ApiResult<EntitySnapshot> city(UUID id) { return resolve(id == null ? null : EntityRef.city(id)); }
    default ApiResult<EntitySnapshot> kingdom(UUID id) { return resolve(id == null ? null : EntityRef.kingdom(id)); }
    /** Startup reconciliation only; immutable identity lists have no financial or private roster data. */
    ApiResult<List<EntitySnapshot>> kingdoms();
    ApiResult<List<EntitySnapshot>> cities();
    ApiResult<List<EntitySnapshot>> cities(UUID kingdom);
    ApiResult<List<GovernmentRolePolicy>> roles(EntityRef entity);
    ApiResult<List<GovernmentMembership>> memberships(UUID player);
    ApiResult<GovernmentMembership> membership(EntityRef entity, UUID player);
    ApiResult<List<TerritoryLocation>> claims(UUID city);
    /** Resolves current political owner of a market location without loading/generating a chunk.
     * MISSING means wilderness. It does not authorize placing/using a market or bypass protection.
     */
    ApiResult<EntitySnapshot> territoryAt(ResourceKey<Level> dimension, BlockPos position);
    /** VALID contains the permitted capability. UNAUTHORIZED means no current authority.
     * The existing level-3 administrative override is honored only for the live server player.
     */
    ApiResult<EconomyPermission> authorize(ServerPlayer actor, EntityRef entity, EconomyPermission permission);
    ApiResult<Set<EconomyPermission>> permissions(ServerPlayer actor, EntityRef entity);
    /** Only the current municipal owner / kingdom ruler or level-3 administrator may edit
     * financial role policy. MANAGE_TREASURY does not delegate permission administration.
     * Leader rights are intrinsic; all non-leader roles start with no economic authority.
     */
    ApiResult<Set<EconomyPermission>> setRolePermissions(ServerPlayer actor, EntityRef entity,
            String role, Set<EconomyPermission> permissions);
    ApiResult<EntitySnapshot> renameCity(ServerPlayer actor, UUID city, String name);
    /** Recipient must already be a citizen of this city and own no city; former owner becomes CITIZEN. */
    ApiResult<EntitySnapshot> transferCity(ServerPlayer actor, UUID city, UUID recipient);
}
