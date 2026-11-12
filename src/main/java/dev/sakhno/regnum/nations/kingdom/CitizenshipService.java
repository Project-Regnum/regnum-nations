package dev.sakhno.regnum.nations.kingdom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import static dev.sakhno.regnum.nations.kingdom.FoundingException.Reason.*;

/** Server-thread-only player citizenship; unrelated to SettlementService's kingdom invitations. */
public final class CitizenshipService {
    static final long INVITATION_NANOS = 60_000_000_000L;
    private static final long INVITE_INTERVAL_NANOS = 500_000_000L;
    private final KingdomService kingdoms;
    private final MinecraftServer server;
    private final KingdomSavedData data;
    private final LongSupplier clock;
    private final Map<Key, Invitation> invitations = new HashMap<>();
    private final Map<UUID, Long> invitedAt = new HashMap<>();
    private record Key(UUID recipient, UUID city) { }
    private record Invitation(ServerPlayer recipient, UUID inviter, UUID anchor, long at, UUID id) { }

    CitizenshipService(KingdomService kingdoms, MinecraftServer server, KingdomSavedData data, LongSupplier clock) {
        this.kingdoms = kingdoms;
        this.server = server;
        this.data = data;
        this.clock = clock;
    }

    public Settlement invite(ServerPlayer inviter, ServerPlayer recipient) throws FoundingException {
        kingdoms.validatePlayer(inviter);
        kingdoms.validatePlayer(recipient);
        Settlement city = authorized(inviter, CityPermission.INVITE);
        if (!canInviteEconomically(city, inviter.getUUID())) throw new FoundingException(NOT_OWNER);
        if (!server.getPlayerList().getPlayers().contains(recipient)) throw new FoundingException(PLAYER_OFFLINE);
        if (data.citizenCity(recipient.getUUID()) != null) throw new FoundingException(ALREADY_CITIZEN);
        long now = clock.getAsLong();
        Long previous = invitedAt.get(inviter.getUUID());
        if (previous != null && now - previous < INVITE_INTERVAL_NANOS) throw new FoundingException(CITIZEN_RATE_LIMIT);
        invitations.entrySet().removeIf(e -> !valid(e.getKey(), e.getValue(), now));
        invitations.put(new Key(recipient.getUUID(), city.id()),
                new Invitation(recipient, inviter.getUUID(), city.obeliskId(), now, UUID.randomUUID()));
        invitedAt.put(inviter.getUUID(), now);
        return city;
    }

    /** Read-only presentation of the existing ephemeral invitations; never grants private roster access. */
    public record PendingInvitation(UUID id,UUID city,UUID recipient,UUID inviter,int remainingSeconds) { }
    public List<PendingInvitation> pending(ServerPlayer viewer,boolean outgoing) throws FoundingException {
        kingdoms.validatePlayer(viewer);
        Settlement city=outgoing?authorized(viewer,CityPermission.INVITE):null;
        long now=clock.getAsLong();
        return invitations.entrySet().stream().filter(e -> valid(e.getKey(),e.getValue(),now))
                .filter(e -> outgoing?e.getKey().city().equals(city.id()):e.getKey().recipient().equals(viewer.getUUID())&&e.getValue().recipient()==viewer)
                .map(e -> new PendingInvitation(e.getValue().id(),e.getKey().city(),e.getKey().recipient(),e.getValue().inviter(),
                        (int)((INVITATION_NANOS-(now-e.getValue().at())+999_999_999L)/1_000_000_000L)))
                .sorted(java.util.Comparator.comparing(PendingInvitation::id)).toList();
    }

    public void accept(ServerPlayer player, UUID city) throws FoundingException {
        consume(player, city);
        data.addCitizen(city, player.getUUID());
        invitations.keySet().removeIf(key -> key.recipient().equals(player.getUUID()));
    }

    public void decline(ServerPlayer player, UUID city) throws FoundingException {
        consume(player, city);
    }

    private void consume(ServerPlayer player, UUID city) throws FoundingException {
        kingdoms.validatePlayer(player);
        Key key = new Key(player.getUUID(), city);
        Invitation invitation = invitations.remove(key);
        if (invitation == null || invitation.recipient() != player || !valid(key, invitation, clock.getAsLong()))
            throw new FoundingException(CITIZEN_INVITATION_REQUIRED);
    }

    private boolean valid(Key key, Invitation invitation, long now) {
        Settlement city = data.settlement(key.city());
        long age = now - invitation.at();
        return age >= 0 && age < INVITATION_NANOS && CityPermissions.allows(data, city, invitation.inviter(), CityPermission.INVITE)
                && city.obeliskId().equals(invitation.anchor()) && canInviteEconomically(city, invitation.inviter());
    }

    private boolean canInviteEconomically(Settlement city, UUID inviter) {
        var scope = dev.sakhno.regnum.nations.api.v1.EntityRef.city(city.id());
        return data.economyPermissions(scope, data.economyRole(scope, inviter))
                .containsAll(data.economyPermissions(scope, CityRole.CITIZEN.name()));
    }

    public void leave(ServerPlayer player) throws FoundingException {
        Settlement city = current(player);
        data.removeCitizen(city.id(), player.getUUID());
        revokeIssued(player.getUUID());
    }

    public void kick(ServerPlayer owner, UUID target) throws FoundingException {
        Settlement city = authorized(owner, CityPermission.MANAGE_MEMBERS);
        data.removeCitizen(city.id(), target);
        revokeIssued(target);
    }

    public List<UUID> list(ServerPlayer player) throws FoundingException {
        return data.citizens(current(player).id());
    }

    public Settlement current(ServerPlayer player) throws FoundingException {
        kingdoms.validatePlayer(player);
        Settlement city = data.citizenCity(player.getUUID());
        if (city == null) throw new FoundingException(NO_CITY);
        return city;
    }

    public void assignRole(ServerPlayer owner, UUID target, CityRole role) throws FoundingException {
        Settlement city = authorized(owner, CityPermission.MANAGE_MEMBERS);
        data.assignRole(city.id(), target, role);
        if (!CityPermissions.allows(data, city, target, CityPermission.INVITE)) revokeIssued(target);
    }

    public CityRole role(ServerPlayer viewer, UUID member) throws FoundingException {
        Settlement city = current(viewer);
        CityRole role = data.role(city.id(), member);
        if (role == null) throw new FoundingException(NOT_CITIZEN);
        return role;
    }

    public boolean permits(ServerPlayer player, CityPermission permission) throws FoundingException {
        return CityPermissions.allows(data, current(player), player.getUUID(), permission);
    }

    private Settlement authorized(ServerPlayer player, CityPermission permission) throws FoundingException {
        Settlement city = current(player);
        if (!CityPermissions.allows(data, city, player.getUUID(), permission)) throw new FoundingException(NOT_OWNER);
        return city;
    }

    private void revokeIssued(UUID player) {
        invitations.values().removeIf(invitation -> invitation.inviter().equals(player));
    }

    void disconnect(UUID player) {
        if (!server.isSameThread()) throw new IllegalStateException("Server thread required");
        invitations.entrySet().removeIf(e -> e.getKey().recipient().equals(player) || e.getValue().inviter().equals(player));
        invitedAt.remove(player);
    }
}
