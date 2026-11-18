package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.networking.PoliticalMapPayloads.*;
import dev.sakhno.regnum.nations.networking.PoliticalMapPayloads;
import dev.sakhno.regnum.nations.networking.AtlasPayloads;
import java.util.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;

/** On-demand, server-thread reads of the existing authoritative registry. */
public final class PoliticalMapServer {
    private static int radius = 32;
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    public static void initialize() {
        // Preserve existing server radius settings across the branding rename.
        Path config = FabricLoader.getInstance().getConfigDir().resolve("project-reigns-map.properties");
        Properties properties = new Properties();
        try {
            if (Files.exists(config)) {
                try (var input = Files.newInputStream(config)) { properties.load(input); }
            } else {
                Files.writeString(config, "# Server settings; protection changes require a restart.\nradiusChunks=32\n"
                        + "protection.explosions=true\nprotection.pistons=true\nprotection.fluids=true\n"
                        + "protection.dispensers=true\nprotection.fire=true\nprotection.entities=true\n");
            }
            radius = Math.clamp(Integer.parseInt(properties.getProperty("radiusChunks", "32")), 1, 64);
        } catch (Exception e) { RegnumNations.LOGGER.warn("Cannot read political map config; using radius 32", e); }
        TerritoryProtectionConfig.load(properties);
        PayloadTypeRegistry.playC2S().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Snapshot.TYPE, Snapshot.CODEC);
        PayloadTypeRegistry.playS2C().register(AtlasPayloads.Dungeons.TYPE, AtlasPayloads.Dungeons.CODEC);
        PayloadTypeRegistry.playS2C().register(AtlasPayloads.DungeonMarkers.TYPE, AtlasPayloads.DungeonMarkers.CODEC);
        PayloadTypeRegistry.playS2C().register(AtlasPayloads.DungeonChange.TYPE, AtlasPayloads.DungeonChange.CODEC);
        PayloadTypeRegistry.playS2C().register(AtlasPayloads.Session.TYPE, AtlasPayloads.Session.CODEC);
        PayloadTypeRegistry.playS2C().register(AtlasPayloads.Markers.TYPE, AtlasPayloads.Markers.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (ServerPlayNetworking.canSend(handler.player, AtlasPayloads.Session.TYPE))
                ServerPlayNetworking.send(handler.player, new AtlasPayloads.Session(AtlasWorldIdentity.get(server)));
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_REQUEST.clear());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_REQUEST.remove(handler.player.getUUID()));
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, (request, context) -> {
            var player = context.player();
            if (player.isRemoved() || !player.isAlive()) return;
            long now = System.nanoTime();
            Long last = LAST_REQUEST.get(player.getUUID());
            if (last != null && now - last < 500_000_000L) return;
            LAST_REQUEST.put(player.getUUID(), now);
            var dimension = player.level().dimension();
            var center = player.chunkPosition();
            var cells = new ArrayList<Cell>();
            var markers = new ArrayList<AtlasPayloads.CityMarker>();
            int status = 0;
            try {
                var data = KingdomService.get(context.server()).data();
                data.requireAvailable();
                int bytes=0;
                outer: for (int z = center.z - radius; z <= center.z + radius; z++) {
                    for (int x = center.x - radius; x <= center.x + radius; x++) {
                        Settlement owner=data.settlement(data.claimOwner(new ClaimPos(dimension,x,z)));
                        if (owner==null) continue;
                        Kingdom sovereign=data.sovereign(owner);
                        Cell cell=new Cell(x,z,owner.id(),owner.name().display(),PoliticalColors.rgb(data.politicalColor(owner)),owner.sovereign(),sovereign==null?"":sovereign.name().display());
                        if (cells.size()==PoliticalMapPayloads.MAX_CELLS||bytes+PoliticalMapPayloads.cellBytes(cell)>PoliticalMapPayloads.MAX_CELL_BYTES) { status=1; break outer; }
                        cells.add(cell); bytes+=PoliticalMapPayloads.cellBytes(cell);
                    }
                }
                // Only inspect owners already disclosed in this bounded political snapshot.
                var marked = new HashSet<UUID>();
                for (Cell cell : cells) {
                    if (!marked.add(cell.owner())) continue;
                    Settlement city = data.settlement(cell.owner());
                    int cx = city.obeliskPos().getX() >> 4, cz = city.obeliskPos().getZ() >> 4;
                    if (!city.dimension().equals(dimension) || Math.abs((long)cx-center.x)>radius || Math.abs((long)cz-center.z)>radius) continue;
                    Kingdom sovereign = data.sovereign(city);
                    markers.add(new AtlasPayloads.CityMarker(city.id(), city.obeliskPos(),
                            sovereign != null && sovereign.capitalSettlementId().equals(city.id())));
                }
            } catch (FoundingException e) { status = 2; }
            ServerPlayNetworking.send(player, new Snapshot(request.sequence(), dimension.location(), center.x, center.z, radius, status, List.copyOf(cells)));
            if (ServerPlayNetworking.canSend(player, AtlasPayloads.Markers.TYPE))
                ServerPlayNetworking.send(player, new AtlasPayloads.Markers(request.sequence(), dimension.location(), markers));
            try {
                if (ServerPlayNetworking.canSend(player, AtlasPayloads.DungeonMarkers.TYPE)) {
                    for (var batch : dungeonBatches(DungeonTerritories.snapshot(context.server()), dimension.location(), request.sequence())) {
                        ServerPlayNetworking.send(player, batch);
                    }
                } else if (ServerPlayNetworking.canSend(player, AtlasPayloads.Dungeons.TYPE)) {
                    // Preserve the first version of the atlas extension for older clients.
                    var legacyMarkers = new ArrayList<CompoundTag>();
                    for (var raw : DungeonTerritories.snapshot(context.server())) {
                        var marker = (CompoundTag) raw;
                        if (!marker.getString("Dimension").equals(dimension.location().toString())
                                || Math.abs((marker.getInt("X") >> 4) - center.x) > radius + 3
                                || Math.abs((marker.getInt("Z") >> 4) - center.z) > radius + 3) continue;
                        var entry = new CompoundTag();
                        for (String key : List.of("Id", "Type", "Name", "Tier", "PartySize", "X", "Y", "Z", "Level", "Defeated")) {
                            entry.put(key, marker.get(key).copy());
                        }
                        legacyMarkers.add(entry);
                        if (legacyMarkers.size() == 128) break;
                    }
                    ServerPlayNetworking.send(player, new AtlasPayloads.Dungeons(dimension.location(), legacyMarkers));
                }
            } catch (IllegalStateException error) {
                RegnumNations.LOGGER.warn("Could not read World dungeon markers for the political atlas", error);
            }
        });
    }

    static List<AtlasPayloads.DungeonMarkers> dungeonBatches(ListTag snapshot, ResourceLocation dimension, int sequence) {
        var markers = new ArrayList<AtlasPayloads.DungeonMarker>();
        String dimensionId = dimension.toString();
        for (var raw : snapshot) {
            var tag = (CompoundTag) raw;
            if (!tag.getString("Dimension").equals(dimensionId)) continue;
            markers.add(AtlasPayloads.DungeonMarker.fromTag(tag));
        }
        var batches = new ArrayList<AtlasPayloads.DungeonMarkers>();
        int start = 0;
        do {
            int end = Math.min(start + AtlasPayloads.DungeonMarkers.BATCH_SIZE, markers.size());
            batches.add(new AtlasPayloads.DungeonMarkers(sequence, dimension, end == markers.size(),
                    markers.subList(start, end)));
            start = end;
        } while (start < markers.size());
        return batches;
    }

    /** World calls this on the server thread after changing the registered map data. */
    public static void dungeonsChanged(ServerLevel world) {
        var payload = new AtlasPayloads.DungeonChange(world.dimension().location());
        for (var player : world.players()) {
            if (ServerPlayNetworking.canSend(player, AtlasPayloads.DungeonChange.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

}
