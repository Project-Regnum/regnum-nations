package dev.sakhno.regnum.nations.networking;

import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.api.v1.*;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Additive bounded political UI channels. No amounts, balances, accounts or Economy actions. */
public final class EconomyNavigationPayloads {
    private EconomyNavigationPayloads() { }
    public record Capabilities(Set<ResourceLocation> providers) implements CustomPacketPayload {
        public Capabilities { providers = Set.copyOf(providers); if (providers.size() > 16) throw new IllegalArgumentException("Too many providers"); }
        public static final Type<Capabilities> TYPE = new Type<>(RegnumNations.id("economy_capabilities"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Capabilities> CODEC = StreamCodec.of((b,p) -> {
            b.writeVarInt(p.providers.size()); p.providers.stream().sorted().forEach(b::writeResourceLocation);
        }, b -> {
            int count = b.readVarInt(); if (count < 0 || count > 16) throw new IllegalArgumentException("Too many providers");
            var providers = new HashSet<ResourceLocation>(); for (int i = 0; i < count; i++) providers.add(b.readResourceLocation());
            return new Capabilities(providers);
        });
        @Override public Type<Capabilities> type() { return TYPE; }
    }
    public record Request(int sequence, EntityRef entity) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(RegnumNations.id("economy_navigation_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); ref(b,p.entity);
        }, b -> new Request(b.readInt(), ref(b)));
        @Override public Type<Request> type() { return TYPE; }
    }
    public record State(int sequence, ApiResult.Status status, Optional<EntitySnapshot> entity, String role,
            Set<EconomyPermission> permissions) implements CustomPacketPayload {
        public State {
            Objects.requireNonNull(status); Objects.requireNonNull(entity); Objects.requireNonNull(role);
            permissions = Set.copyOf(permissions);
            if ((status == ApiResult.Status.VALID) != entity.isPresent()) throw new IllegalArgumentException("Invalid navigation state");
            if (status != ApiResult.Status.VALID && (!role.isEmpty() || !permissions.isEmpty())) throw new IllegalArgumentException("Failed state contains authority");
        }
        public static final Type<State> TYPE = new Type<>(RegnumNations.id("economy_navigation_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of((b,p) -> {
            b.writeInt(p.sequence); b.writeEnum(p.status); b.writeBoolean(p.entity.isPresent());
            p.entity.ifPresent(e -> EconomyNavigationPayloads.entity(b,e)); b.writeUtf(p.role,32); b.writeVarInt(p.permissions.size());
            p.permissions.stream().sorted().forEach(b::writeEnum);
        }, b -> {
            int sequence = b.readInt(); var status = b.readEnum(ApiResult.Status.class);
            var entity = b.readBoolean() ? Optional.of(EconomyNavigationPayloads.entity(b)) : Optional.<EntitySnapshot>empty(); String role = b.readUtf(32);
            int count = b.readVarInt(); if (count < 0 || count > EconomyPermission.values().length) throw new IllegalArgumentException("Too many permissions");
            var permissions = EnumSet.noneOf(EconomyPermission.class);
            for (int i = 0; i < count; i++) if (!permissions.add(b.readEnum(EconomyPermission.class))) throw new IllegalArgumentException("Duplicate permission");
            return new State(sequence, status, entity, role, permissions);
        });
        @Override public Type<State> type() { return TYPE; }
    }
    private static void ref(RegistryFriendlyByteBuf b, EntityRef entity) { b.writeEnum(entity.scope()); b.writeUUID(entity.id()); }
    private static EntityRef ref(RegistryFriendlyByteBuf b) { return new EntityRef(b.readEnum(EntityRef.Scope.class), b.readUUID()); }
    private static void optionalId(RegistryFriendlyByteBuf b, Optional<UUID> value) { b.writeNullable(value.orElse(null), RegistryFriendlyByteBuf::writeUUID); }
    private static Optional<UUID> optionalId(RegistryFriendlyByteBuf b) { return Optional.ofNullable(b.readNullable(RegistryFriendlyByteBuf::readUUID)); }
    private static void entity(RegistryFriendlyByteBuf b, EntitySnapshot e) {
        ref(b,e.ref()); b.writeUtf(e.name(),64); b.writeUUID(e.founder()); b.writeUUID(e.owner());
        optionalId(b,e.kingdom()); optionalId(b,e.capitalCity()); b.writeInt(e.color()); optionalId(b,e.heraldry());
        b.writeResourceLocation(e.dimension()); b.writeBlockPos(e.anchor());
    }
    private static EntitySnapshot entity(RegistryFriendlyByteBuf b) {
        return new EntitySnapshot(ref(b), b.readUtf(64), b.readUUID(), b.readUUID(), optionalId(b), optionalId(b),
                b.readInt(), optionalId(b), b.readResourceLocation(), b.readBlockPos());
    }
}
