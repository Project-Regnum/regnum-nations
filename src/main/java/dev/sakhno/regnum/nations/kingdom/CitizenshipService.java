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
    private record Invitation(ServerPlayer recipient, UUID inviter, UUID anchor, long at) { }

    CitizenshipService(KingdomService kingdoms, MinecraftServer server, KingdomSavedData data, LongSupplier clock) {
        this.kingdoms = kingdoms;
        this.server = server;
        this.data = data;
        this.clock = clock;
    }

    public Settlement invite(ServerPlayer owner, ServerPlayer recipient) throws FoundingException {
        kingdoms.validatePlayer(owner);
        kingdoms.validatePlayer(recipient);
        Settlement city = managed(owner);
        if (!server.getPlayerList().getPlayers().contains(recipient)) throw new FoundingException(PLAYER_OFFLINE);
        if (data.citizenCity(recipient.getUUID()) != null) throw new FoundingException(ALREADY_CITIZEN);
        long now = clock.getAsLong();
        Long previous = invitedAt.get(owner.getUUID());
        if (previous != null && now - previous < INVITE_INTERVAL_NANOS) throw new FoundingException(CITIZEN_RATE_LIMIT);
        invitations.entrySet().removeIf(e -> !valid(e.getKey(), e.getValue(), now));
        invitations.put(new Key(recipient.getUUID(), city.id()),
                new Invitation(recipient, owner.getUUID(), city.obeliskId(), now));
        invitedAt.put(owner.getUUID(), now);
        return city;
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
        return age >= 0 && age < INVITATION_NANOS && CityPermissions.canManage(city, invitation.inviter())
                && city.obeliskId().equals(invitation.anchor());
    }

    public void leave(ServerPlayer player) throws FoundingException {
        Settlement city = current(player);
        data.removeCitizen(city.id(), player.getUUID());
    }

    public void kick(ServerPlayer owner, UUID target) throws FoundingException {
        Settlement city = managed(owner);
        data.removeCitizen(city.id(), target);
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

    private Settlement managed(ServerPlayer player) throws FoundingException {
        Settlement city = current(player);
        if (!CityPermissions.canManage(city, player.getUUID())) throw new FoundingException(NOT_OWNER);
        return city;
    }

    void disconnect(UUID player) {
        if (!server.isSameThread()) throw new IllegalStateException("Server thread required");
        invitations.entrySet().removeIf(e -> e.getKey().recipient().equals(player) || e.getValue().inviter().equals(player));
        invitedAt.remove(player);
    }
}
