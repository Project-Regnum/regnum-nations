// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Dara Adedeji. Adapted from Diorama v0.2.1 ImmediateCanvas.
package dev.sakhno.regnum.nations.client.terrain.diorama.render.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

/** Only the terrain rectangle portion of the upstream immediate canvas is needed. */
public final class TileCanvas {
    private TileCanvas() { }

    public static void draw(GuiGraphics graphics, MapTexture texture, int x0, int y0, int x1, int y1) {
        graphics.flush();
        var mesh = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        var pose = graphics.pose().last().pose();
        mesh.addVertex(pose, x0, y0, 0).setUv(0, 0).setColor(0xffffffff);
        mesh.addVertex(pose, x0, y1, 0).setUv(0, 1).setColor(0xffffffff);
        mesh.addVertex(pose, x1, y1, 0).setUv(1, 1).setColor(0xffffffff);
        mesh.addVertex(pose, x1, y0, 0).setUv(1, 0).setColor(0xffffffff);
        texture.bind(0, Sampling.TILE_SMOOTH);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        BufferUploader.drawWithShader(mesh.buildOrThrow());
        RenderSystem.setShaderTexture(0, 0);
    }
}
