package dev.sakhno.regnum.nations.kingdom;

import dev.sakhno.regnum.nations.RegnumNations;
import dev.sakhno.regnum.nations.networking.PoliticalMapPayloads.*;
import dev.sakhno.regnum.nations.networking.PoliticalMapPayloads;
import dev.sakhno.regnum.nations.networking.AtlasPayloads;
import java.util.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.*;

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
        });
    }
}
