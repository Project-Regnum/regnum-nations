package dev.sakhno.regnum.nations.client.terrain;

import dev.sakhno.regnum.nations.client.terrain.diorama.MapSession;
import dev.sakhno.regnum.nations.client.terrain.diorama.Projection;
import dev.sakhno.regnum.nations.client.terrain.diorama.bake.TileBaker;
import dev.sakhno.regnum.nations.client.terrain.diorama.bake.TileKey;
import dev.sakhno.regnum.nations.client.terrain.diorama.render.gpu.TileCanvas;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.gui.GuiGraphics;

/** Living Atlas adapter. All sampling, shading, tile work and storage belong to the MIT subset. */
public final class AtlasTerrain {
    private AtlasTerrain() { }

    public static void initialize() {
        TileBaker.setFast(true);
        ClientChunkEvents.CHUNK_LOAD.register(MapSession::chunkLoaded);
        ClientTickEvents.END_CLIENT_TICK.register(MapSession::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MapSession.close());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MapSession.awaitClosed());
    }

    /** Coordinates deliberately match the existing atlas's chunk origin and floor rounding. */
    public static boolean draw(GuiGraphics graphics, int left, int top, int right, int bottom,
                               double originX, double originZ, double zoom) {
        var session = MapSession.get();
        if (session == null) return false;
        var map = session.map();
        double pixelsPerBlock = zoom / 16;
        int lod = pixelsPerBlock < 0.5 && map.hasOverviews() ? 1 : 0;
        int span = Projection.TILE_UNITS << lod;
        int minX = (int)Math.floor((left-originX) / pixelsPerBlock / span);
        int minZ = (int)Math.floor((top-originZ) / pixelsPerBlock / span);
        int maxX = (int)Math.floor((right-1-originX) / pixelsPerBlock / span);
        int maxZ = (int)Math.floor((bottom-1-originZ) / pixelsPerBlock / span);
        boolean drawn = false;
        for (int z=minZ; z<=maxZ; z++) for (int x=minX; x<=maxX; x++) {
            var key = new TileKey(TileKey.SURFACE, x, z, lod);
            double dx=x-(minX+maxX)/2.0, dz=z-(minZ+maxZ)/2.0;
            var tile = map.want(key, dx*dx+dz*dz);
            if (tile != null && tile.texture() != null) {
                TileCanvas.draw(graphics, tile.texture(),
                        (int)Math.floor(originX+x*(double)span*pixelsPerBlock),
                        (int)Math.floor(originZ+z*(double)span*pixelsPerBlock),
                        (int)Math.floor(originX+(x+1.0)*span*pixelsPerBlock),
                        (int)Math.floor(originZ+(z+1.0)*span*pixelsPerBlock));
                drawn = true;
            } else if (lod>0) {
                // Freshly explored terrain need not wait for the saved overview's writer.
                for (int cz=0; cz<2; cz++) for (int cx=0; cx<2; cx++) {
                    int tx=x*2+cx, tz=z*2+cz;
                    var detail = map.want(new TileKey(TileKey.SURFACE, tx, tz), dx*dx+dz*dz);
                    if (detail != null && detail.texture() != null) {
                        TileCanvas.draw(graphics, detail.texture(),
                                (int)Math.floor(originX+tx*128.0*pixelsPerBlock),
                                (int)Math.floor(originZ+tz*128.0*pixelsPerBlock),
                                (int)Math.floor(originX+(tx+1.0)*128*pixelsPerBlock),
                                (int)Math.floor(originZ+(tz+1.0)*128*pixelsPerBlock));
                        drawn = true;
                    }
                }
            }
        }
        return drawn;
    }
}
